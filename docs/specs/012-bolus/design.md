# Design 012: Polecenie BOLUS

Dotyczy: [spec.md](spec.md)

## Przegląd

`BOLUS` to nowy wpis katalogu i nowe warianty w `parser`, `session`, `data` i `ui` – według reguły z AGENTS.md („nowe polecenie = wpis w katalogu + wzorce w parserze + testy”). Ścieżka sesji jest ta sama co dla `CARBS`; sesja przestaje rozpoznawać prośbę o kod i niezgodną ilość po konkretnych klasach `CARBS`, a zaczyna po wspólnych interfejsach znacznikowych w `AapsReply`. Limity i odstęp czyta `CommandLauncher` (Spec 011).

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.commands

enum class CommandId { BG, PUMP, CARBS, BOLUS, /* Spec 013: */ PROFILE_STATUS, PROFILE_LIST, PROFILE_SWITCH }

sealed interface Command {
    // …
    /** Tylko przez of()/parse() (BOL-02, BOL-03). toString() bez ilości (BOL-06). */
    class Bolus private constructor(val centiUnits: Int) : Command {
        override val id = CommandId.BOLUS
        override fun equals(other: Any?): Boolean = other is Bolus && other.centiUnits == centiUnits
        override fun hashCode(): Int = centiUnits
        override fun toString(): String = "Command.Bolus(***)"
        companion object {
            fun of(centiUnits: Int, limits: CommandLimits): BolusValidation
            fun parse(input: String, limits: CommandLimits): BolusValidation
        }
    }
}

sealed interface BolusValidation {
    data class Valid(val command: Command.Bolus) : BolusValidation
    data class Invalid(val error: BolusAmountError) : BolusValidation
}
// BolusAmountError i BolusAmountText (BOL-03, BOL-05) – zdefiniowane w zad. 1 Spec 011 (design 011, „Interfejsy”); tu tylko użycie.

object BolusLimits { const val MIN_CENTI_UNITS = 5; const val STEP_CENTI_UNITS = 5 }

// CommandParameter.InsulinUnits(minCentiUnits = 5, stepCentiUnits = 5) – wpis BOLUS (BOL-01).
// smsText(): is Command.Bolus -> "BOLUS ${BolusAmountText.toSms(centiUnits)}"
// toExpectedCommand(): is Command.Bolus -> ExpectedCommand.Bolus(centiUnits)
// requiresExtraConfirmation(limits): is Command.Bolus -> limits.bolusRequiresExtraConfirmation(centiUnits)
```

```kotlin
package pl.twojanazwa.aapsremote.parser

sealed interface ExpectedCommand {
    // …
    class Bolus(val centiUnits: Int) : ExpectedCommand { /* require ≥ 1, equals/hashCode, toString "ExpectedCommand.Bolus(***)" */ }
}

sealed interface AapsReply {
    /** Prośba o kod zgodna z poleceniem – jedyna droga do kodu (SESSION-08). */
    sealed interface MatchingCodeRequest : AapsReply
    /** Wiadomość w formie prośby/potwierdzenia, ale z inną wartością niż wysłana. */
    sealed interface CommandMismatch : AapsReply
    /** Potwierdzenie wykonania zgodne z poleceniem. */
    sealed interface MatchingDone : AapsReply

    data class CodeRequest(val grams: Int, val text: String) : MatchingCodeRequest          // bez zmian poza nadtypem
    data class CarbsAmountMismatch(/* bez zmian */) : CommandMismatch
    data class CarbsDone(val grams: Int, val text: String) : MatchingDone

    // Każdy wariant jawnie nadpisuje toString() – bez ilości i treści SMS (BOL-09, PARSER-10).
    data class BolusCodeRequest(val centiUnits: Int, val text: String) : MatchingCodeRequest {
        override fun toString(): String = "AapsReply.BolusCodeRequest(***)"
    }
    data class BolusAmountMismatch(
        val expectedCentiUnits: Int,
        val receivedCentiUnits: Int,
        val stage: CarbsStage,
        val text: String,
    ) : CommandMismatch {
        override fun toString(): String = "AapsReply.BolusAmountMismatch(stage=$stage, ***)"
    }
    data class BolusDone(val centiUnits: Int, val text: String) : MatchingDone {
        override fun toString(): String = "AapsReply.BolusDone(***)"
    }
    // …
}
// CarbsStage zostaje nazwą etapu także dla bolusa (CODE_REQUEST, CONFIRMATION) – bez zmiany nazwy, żeby nie ruszać Spec 002.
```

Wzorce (`AapsPl3426Patterns`, `matchEntire`):

```kotlin
private const val U = """(0|[1-9][0-9]{0,2}),([0-9]{2})"""
val BOLUS_CODE_REQUEST = Regex("""Aby dostarczyc bolus ${U}U wprowadz kod z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em""")
val BOLUS_DONE_FIRST_LINE = Regex("""Bolus $U U podany prawidlowo""")
```

Parser dla `Bolus(x)`: `WRONG_CODE` → `WrongCode`; `BOLUS_CODE_REQUEST` na całej treści → x ? `BolusCodeRequest` : `BolusAmountMismatch(CODE_REQUEST)`; `BOLUS_DONE_FIRST_LINE` na pierwszej linii → `BolusDone` / `BolusAmountMismatch(CONFIRMATION)`; inaczej `Unrecognized`.

```kotlin
package pl.twojanazwa.aapsremote.session

sealed interface SessionOutcome {
    // …
    /** „AAPS potwierdził podanie X j.” – nie dowód wykonania polecenia z aplikacji. */
    data class BolusConfirmed(val centiUnits: Int, val text: String) : SessionOutcome { override fun toString() = "BolusConfirmed(***)" }
}
```

```kotlin
package pl.twojanazwa.aapsremote.ui.commands
// LaunchRejection: + BOLUS_INTERVAL, BOLUS_HISTORY_UNAVAILABLE.
// LaunchResult.Rejected(reason, minutesLeft: Int? = null) – minutesLeft tylko dla BOLUS_INTERVAL.
// CommandLauncher: nowe zależności CommandHistory, EpochClock.
// CommandsViewModel: onBolusInputChanged, onBolusNextClicked, onBolusLargeConfirmed, onBolusLargeDismissed;
//   podsumowanie uogólnione: SummaryState(command: Command) zamiast samej ilości CARBS.
```

## Model danych i stany

Sesja (`CommandSession.processMessage`) – zmiana bez nowych faz:

| Faza | Wynik parsera | Akcja | Wymaganie |
| --- | --- | --- | --- |
| `AWAITING_CODE_REQUEST` | `MatchingCodeRequest` | `startPreparingCode` | SESSION-08, BOL-10 |
| `PREPARING_CODE` | `MatchingCodeRequest` | `Failed(UNEXPECTED_REPLY)` | BOL-10 |
| przed kodem | `CommandMismatch` | `Failed(AMOUNT_MISMATCH)` | BOL-10 |
| przed kodem | `MatchingDone` | wiadomość nieoczekiwana | BOL-10 |
| `AWAITING_CONFIRMATION` | `CarbsDone` / `BolusDone`, `receivedAt ≥ codeSentAt` | `CarbsConfirmed` / `BolusConfirmed` | SESSION-12, BOL-11 |
| `AWAITING_CONFIRMATION` | `CommandMismatch` | `Unknown(AMOUNT_MISMATCH)` | BOL-11 |
| `AWAITING_CONFIRMATION` | `MatchingCodeRequest` | `Unknown(UNEXPECTED_REPLY)` | BOL-11 |

Przed tabelą działa funkcja zgodności (BOL-19): wynik `MatchingCodeRequest`/`MatchingDone` niezgodny z `command.id` jest zamieniany na `Unrecognized`.

```kotlin
package pl.twojanazwa.aapsremote.session

internal object ReplyMatch {
    /** BOL-19, PROF-12. Wyczerpujący when po CommandId; BG/PUMP/PROFILE_STATUS/PROFILE_LIST → false. */
    fun isCodeRequestFor(id: CommandId, reply: AapsReply.MatchingCodeRequest): Boolean
    fun isDoneFor(id: CommandId, reply: AapsReply.MatchingDone): Boolean
}
// CommandSession: nowy parametr konstruktora (domyślny – prawdziwy parser), tylko do testów BOL-19:
//   private val parse: (String, ExpectedCommand) -> AapsReply = AapsReplyParser::parse
```

Pozostałe wiersze SESSION-11/SESSION-13 bez zmian (`WrongCode`, `Unrecognized`). `ui` i `data` odwzorowują `BolusConfirmed` wyczerpującym `when`.

Historia: `HistoryRow`/`HistoryEntity`/`HistoryEntry` + pole `bolusCentiUnits: Int?` (kolumna `bolus_centi_units`); `HistoryCommand.BOLUS` (tekst `BOLUS`), `HistoryOutcome.BolusConfirmed` (tekst `BOLUS_CONFIRMED`). `AapsRemoteDatabase` wersja 2, `MIGRATION_1_2` z jednym `ALTER TABLE`, rejestrowana w jednym `addMigrations(...)` (Spec 013 dopisuje tam `MIGRATION_2_3`).

Sprawdzenie odstępu (`CommandLauncher`, czysta funkcja do testów):

```kotlin
internal object BolusInterval {
    /** null = można; inaczej liczba minut do końca odstępu (≥ 1). BOL-12. */
    fun minutesLeft(entries: List<HistoryEntry>, nowEpochMillis: Long, intervalMinutes: Int): Int?
}
```

Kolejność w `launch` dla `BOLUS`: kroki 0–3 CMDUI-12 → LIM-09 → BOL-12 → odczyt sekretu → start.

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Interfejsy znacznikowe `MatchingCodeRequest`, `CommandMismatch`, `MatchingDone` | jeden ogólny `CodeRequest(amount: Any)` | Sesja nie zależy od rodzaju polecenia; typy `CARBS` i ich testy (Spec 002) zostają bez zmian. |
| Wzorzec ilości AAPS tylko z przecinkiem i 2 miejscami | dowolny separator | Ścisłe dopasowanie = bezpieczna strona (P2); inny zapis kończy się stopem albo „nieznany”. |
| Potwierdzenie bolusa jako rozpoznawany wynik | wszystko po kodzie = „nieznany” | Mamy prawdziwy SMS (B3); ilość w potwierdzeniu daje użytkownikowi jasny wynik, a inna ilość → „nieznany”. |
| Odstęp z historii aplikacji w `CommandLauncher` | osobna tabela „ostatni bolus”; brak sprawdzania | Bez nowych danych – historia już ma polecenie, wynik i chwile (P6). |
| Kolumna `bolus_centi_units` + migracja 1 → 2 | wspólna kolumna „ilość” | DATA-09 i istniejące wiersze bez zmian; brak migracji destrukcyjnej (DATA-14). |
| Migracja sprawdzana testem JVM (SQL + `2.json`) i ręcznie (W3) | `MigrationTestHelper` | Projekt nie używa `androidTest` ani Robolectric (decyzja użytkownika w Spec 007 – 009). |
| `Failed(WRONG_CODE)` liczy się do odstępu | pomijać wpisy ze złym kodem | Kod był wysłany; aplikacja nie ma pewności, czy AAPS go odrzucił – strona bezpieczna (BOL-12). |
| Port parsera w `CommandSession` (parametr z wartością domyślną) | testy tylko na prawdziwym parserze | BOL-19 da się sprawdzić tylko przy wyniku, którego prawdziwy parser w danym kontekście nie zwraca. |
| Brak szybkich wartości bolusa | jak `CARBS` | Ilość insuliny zawsze wpisywana świadomie; użytkownik nie prosił. |

## Mapowanie wymagań na testy

| Wymaganie | Test |
| --- | --- |
| BOL-01, BOL-02, BOL-04 – BOL-06 | `BolusCommandTest`, aktualizacja `CommandCatalogTest` |
| BOL-03 | `BolusAmountTextTest` (Spec 011, zad. 1), `BolusCommandTest` (tekst z limitem) |
| BOL-07 – BOL-09 | `BolusReplyParserTest` (pliki z `dane/`, warianty syntetyczne), `ParserRedactionTest` |
| BOL-10, BOL-11 | `CommandSessionBolusTest` (czas wirtualny, `FakeSmsSender`, `FakeIncomingSmsSource`) |
| BOL-19 | `ReplyMatchTest` (wszystkie pary), `CommandSessionBolusTest` (pliki innych poleceń, podstawiony parser) |
| BOL-12 | `BolusIntervalTest` (czysta funkcja), `CommandLauncherBolusTest` |
| BOL-13 – BOL-15 | `CommandsViewModelBolusTest`, test źródła okna (`DialogProperties`) |
| BOL-16 | `ResultMapperBolusTest`, `ProgressStepsTest` |
| BOL-17 | `HistoryCodecBolusTest`, `DatabaseSchemaV2Test`, `HistoryEntryMapperBolusTest`; W3 |
| BOL-18 | testy `toString()` w powyższych, rozszerzenie testów braku logowania |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Warunek wstępny: zadania 1 i 4 Spec 011.

**Zasada kolejności:** żaden scalony stan nie pozwala wysłać `BOLUS` bez sprawdzenia limitu i odstępu, bez kroków przebiegu, tekstów wyniku i ostrzeżenia „Nie ponawiaj bolusa…”. Sekcja Bolus z przyciskiem „Wyślij” (zadanie 6) jest ostatnia.

**Miejsca, które przestają się kompilować po dodaniu `CommandId.BOLUS`, `Command.Bolus` i `ExpectedCommand.Bolus`** (sprawdzone na `main`): `commands/CommandCatalog.kt` (wpis), `commands/CommandExtensions.kt` (`smsText`, `toExpectedCommand`), `data/HistoryCodec.kt` (`encode(CommandId)`), `ui/commands/RunTexts.kt` (`CommandId.titleRes`), `ui/commands/ResultMapper.kt` (`when (command)` w `Completed`), `parser/AapsReplyParser.kt` (`when (expected)`). Zadanie 1 dodaje w nich gałęzie minimalne: `HistoryCodec` – tekst `BOLUS` (odczyt jako `UNRECOGNIZED` do zadania 4), `RunTexts` – nagłówek `run_title_bolus`, `ResultMapper` – `DETAILS_MISSING`, parser – `Unrecognized` (do zadania 2). Po zadaniu 1 bolusa nie da się uruchomić z UI.

1. **Katalog: `Command.Bolus`** – `commands/Command.kt`, `CommandId.kt` (dodaje `BOLUS`), `CommandCatalog.kt`, `CommandParameter.kt`, `CommandExtensions.kt`, `BolusValidation.kt` (tylko `BolusValidation`; `BolusAmountError` i `BolusAmountText` są ze Spec 011, zad. 1), `BolusLimits.kt`; `parser/ExpectedCommand.kt` (`Bolus`), `parser/AapsReplyParser.kt` (gałąź `Bolus` → `Unrecognized`), `data/HistoryCodec.kt`, `ui/commands/RunTexts.kt`, `ui/commands/ResultMapper.kt`, `strings.xml` (`run_title_bolus`); testy `BolusCommandTest`, `CommandCatalogTest`. Wymagania: BOL-01, BOL-02, BOL-04 – BOL-06. Etykiety `bezpieczenstwo`, `trudnosc:latwe`.
2. **Parser bolusa** – `parser/AapsReply.kt` (znaczniki, nowe warianty), `AapsPl3426Patterns.kt`, `AapsReplyParser.kt`; kopie plików `dane/` do `app/src/test/resources/parser/aaps-3.4.2.6-pl/`; testy `BolusReplyParserTest`, `ParserRedactionTest`. Wymagania: BOL-07 – BOL-09. Zależy od 1. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
3. **Sesja bolusa** – `session/CommandSession.kt` (znaczniki zamiast klas `CARBS`, port parsera, funkcja zgodności), `session/ReplyMatch.kt`, `SessionState.kt` (`BolusConfirmed`); minimalne gałęzie `BolusConfirmed` w `ui/commands/ResultMapper.kt` i `data/HistoryCodec.kt` (pełne w zad. 4 i 5); testy `CommandSessionBolusTest`, `ReplyMatchTest`, regresja testów Spec 004. Wymagania: BOL-10, BOL-11, BOL-19. Zależy od 2. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
4. **Historia bolusa i migracja** – `data/HistoryCommand.kt`, `HistoryOutcome.kt`, `HistoryRow.kt`, `HistoryEntry.kt`, `HistoryCodec.kt`, `data/platform/HistoryEntity.kt`, `AapsRemoteDatabase.kt` (wersja 2, `MIGRATION_1_2`, `addMigrations`), `DataModule.kt`, `app/schemas/…/2.json`; testy `HistoryCodecBolusTest`, `DatabaseSchemaV2Test`. Wymagania: BOL-17 (dane). Zależy od 3. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. **Warunek scalenia:** W3 sprawdzone na telefonie z prawdziwą bazą w wersji 1 (wynik w opisie PR).
5. **Launcher: odstęp + przebieg/wynik/historia UI** – `ui/commands/CommandLauncher.kt`, `BolusInterval.kt`, `LaunchResult.kt`, `CommandMessage.kt`, `ProgressSteps.kt`, `ResultMapper.kt`, `ResultMessages.kt`, `RunTexts.kt`, `ui/history/HistoryLabels.kt`, `HistoryEntryMapper.kt`, `strings.xml`; testy `BolusIntervalTest`, `CommandLauncherBolusTest`, `ResultMapperBolusTest`, `HistoryEntryMapperBolusTest`. Wymagania: BOL-12, BOL-16, BOL-17 (UI). Zależy od 3, 4 i zadania 4 Spec 011. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. Jeśli przekroczy 400 linii – podział na 5a (odstęp) i 5b (przebieg, wynik, historia); oba przed zadaniem 6.
6. **UI: sekcja Bolus, okno, podsumowanie z „Wyślij”** – `ui/commands/CommandsViewModel.kt`, `CommandsUiState.kt`, `CommandsPanel.kt`, `CommandDialogs.kt`, `CarbsSummary.kt` → uogólnione podsumowanie, `CommandTexts.kt`, `strings.xml`; testy `CommandsViewModelBolusTest`. Wymagania: BOL-13 – BOL-15, BOL-18 (część). Zależy od 5. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. PR opisuje W1, W2, W4, W5.

Zależności: 1 → 2 → 3 → 4 → 5 → 6.

## Zmiana 2026-10-31: odmowy i błąd bolusa z kodu AAPS (BOL-20 – BOL-22)

Zadania (wspólne z profilem) – [design 013, „Zadania zmiany 2026-10-31”](../013-profil/design.md#zadania-zmiany-2026-10-31).

```kotlin
package pl.twojanazwa.aapsremote.parser

enum class BolusRefusal { INTERVAL, QUEUE }

sealed interface AapsReply {
    /** BOL-20. Nie jest MatchingCodeRequest ani MatchingDone – nigdy nie daje kodu. */
    data class BolusRefused(val refusal: BolusRefusal, val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.BolusRefused(refusal=$refusal, ***)"
    }
    /** BOL-21. Nie jest MatchingDone – nigdy nie daje BolusConfirmed. */
    data class BolusFailed(val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.BolusFailed(***)"
    }
}

// AapsPl3426Patterns (matchEntire):
val BOLUS_REFUSED_INTERVAL = Regex("""Bolus zdalny niedostepny\. Sprobuj ponownie pozniej\.""")       // cała treść
val BOLUS_REFUSED_QUEUE = Regex("""W kolejce oczekuje inny bolus\. Sprobuj ponownie pozniej\.""")       // cała treść
val BOLUS_FAILED_FIRST_LINE = Regex(
    """Bolus nie powiodl sie\. Zweryfikuj prawdziwy stan przed wyslaniem kolejnej komendy!""",          // pierwsza linia
)
// parseBolus: WRONG_CODE → BOLUS_CODE_REQUEST → BOLUS_REFUSED_* (cała treść) → BOLUS_FAILED_FIRST_LINE → BOLUS_DONE_FIRST_LINE → null
```

```kotlin
package pl.twojanazwa.aapsremote.session

enum class FailureReason { /* dotychczasowe */ AAPS_BOLUS_INTERVAL, AAPS_BOLUS_QUEUE }   // dopisane na końcu
enum class UnknownReason { /* dotychczasowe */ BOLUS_FAILED }                             // dopisane na końcu

// CommandSession.amountMatches: is AapsReply.BolusRefused, is AapsReply.BolusFailed -> sent is Command.Bolus
// processMessage, AWAITING_CODE_REQUEST/PREPARING_CODE:
//   is AapsReply.BolusRefused -> Failed(INTERVAL → AAPS_BOLUS_INTERVAL, QUEUE → AAPS_BOLUS_QUEUE, sms.body)
//   BolusFailed – istniejąca gałąź else → Failed(UNRECOGNIZED_REPLY)
// processMessage, AWAITING_CONFIRMATION:
//   is AapsReply.BolusFailed -> receivedAt >= codeSentAt ? Unknown(BOLUS_FAILED, sms.body) : addUnexpected(sms)
//   BolusRefused – istniejąca gałąź else → Unknown(UNRECOGNIZED_REPLY)
```

`HistoryCodec`: literały `AAPS_BOLUS_INTERVAL`, `AAPS_BOLUS_QUEUE` w `FAILURE_REASONS`/`encode`, `BOLUS_FAILED` w `UNKNOWN_REASONS`/`encode`. `ResultMessages`/`ResultMapper`, `HistoryEntryMapper`: nowe klucze `strings.xml` (`result_failed_aaps_bolus_interval`, `result_failed_aaps_bolus_queue`, `result_unknown_bolus_failed`). `BolusInterval` bez zmian (wpis `Unknown` z kodem liczy się, `Failed` bez kodu – nie).

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Błąd bolusa = `Unknown(BOLUS_FAILED)` | `Failed(BOLUS_FAILED)` | AAPS każe zweryfikować prawdziwy stan; `Failed` sugerowałby, że insuliny nie podano, i zachęcał do powtórzenia (AGENTS.md: bez ponawiania). |
| Dwa powody `Failed` dla odmów | jeden `AAPS_REFUSED` z tekstem z treści | Wyczerpujący `when` w `ui` bez parsowania treści drugi raz; teksty różnią się zaleceniem. |
| Odmowa po kodzie → `Unknown(UNRECOGNIZED_REPLY)` | `Unknown` z nowym powodem | AAPS nie wysyła odmowy po kodzie; taka wiadomość dotyczy innego polecenia – bez nowego tekstu. |
| Wzorce tylko w kontekście `Bolus` | rozpoznawanie we wszystkich kontekstach | Najmniejsza zmiana PARSER-01; w innych sesjach treść i tak kończy się stopem albo „nieznany”. |

| Wymaganie | Test |
| --- | --- |
| BOL-20, BOL-21 (parser) | `BolusReplyParserTest`, `ParserRedactionTest` |
| BOL-20, BOL-21 (sesja) | `CommandSessionBolusTest`, `BolusIntervalTest` |
| BOL-22 | `ResultMapperBolusTest`, `HistoryCodecBolusTest`, `HistoryCodecTest` (parytet), `HistoryEntryMapperBolusTest` |
