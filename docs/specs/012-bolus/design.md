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
enum class BolusAmountError { EMPTY, INVALID_CHARACTERS, TOO_MANY_DECIMALS, BELOW_MIN, ABOVE_MAX, NOT_STEP }

/** BOL-03 kroki 1–4: tekst → setne albo błąd składni. Wspólne z LimitsInput (Spec 011). */
object BolusAmountText {
    sealed interface Parsed { data class Value(val centiUnits: Int) : Parsed; data class Error(val error: BolusAmountError) : Parsed }
    fun parse(input: String): Parsed
    /** 10 → "0.1", 200 → "2" (BOL-05). */
    fun toSms(centiUnits: Int): String
}

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

    data class BolusCodeRequest(val centiUnits: Int, val text: String) : MatchingCodeRequest   // toString "AapsReply.BolusCodeRequest(***)"
    data class BolusAmountMismatch(val expectedCentiUnits: Int, val receivedCentiUnits: Int, val stage: CarbsStage, val text: String) : CommandMismatch
    data class BolusDone(val centiUnits: Int, val text: String) : MatchingDone
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

Pozostałe wiersze SESSION-11/SESSION-13 bez zmian (`WrongCode`, `Unrecognized`). `ui` i `data` odwzorowują `BolusConfirmed` wyczerpującym `when`.

Historia: `HistoryRow`/`HistoryEntity`/`HistoryEntry` + pole `bolusCentiUnits: Int?` (kolumna `bolus_centi_units`); `HistoryCommand.BOLUS` (tekst `BOLUS`), `HistoryOutcome.BolusConfirmed` (tekst `BOLUS_CONFIRMED`). `AapsRemoteDatabase` wersja 2, `MIGRATION_1_2` z jednym `ALTER TABLE`.

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
| Brak szybkich wartości bolusa | jak `CARBS` | Ilość insuliny zawsze wpisywana świadomie; użytkownik nie prosił. |

## Mapowanie wymagań na testy

| Wymaganie | Test |
| --- | --- |
| BOL-01, BOL-02, BOL-04 – BOL-06 | `BolusCommandTest`, aktualizacja `CommandCatalogTest` |
| BOL-03 | `BolusAmountTextTest` (Spec 011, zad. 1), `BolusCommandTest` (tekst z limitem) |
| BOL-07 – BOL-09 | `BolusReplyParserTest` (pliki z `dane/`, warianty syntetyczne), `ParserRedactionTest` |
| BOL-10, BOL-11 | `CommandSessionBolusTest` (czas wirtualny, `FakeSmsSender`, `FakeIncomingSmsSource`) |
| BOL-12 | `BolusIntervalTest` (czysta funkcja), `CommandLauncherBolusTest` |
| BOL-13 – BOL-15 | `CommandsViewModelBolusTest`, test źródła okna (`DialogProperties`) |
| BOL-16 | `ResultMapperBolusTest`, `ProgressStepsTest` |
| BOL-17 | `HistoryCodecBolusTest`, `DatabaseSchemaV2Test`, `HistoryEntryMapperBolusTest`; W3 |
| BOL-18 | testy `toString()` w powyższych, rozszerzenie testów braku logowania |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Warunek wstępny: zadanie 1 Spec 011.

1. **Katalog: `Command.Bolus`** – `commands/Command.kt`, `CommandId.kt`, `CommandCatalog.kt`, `CommandParameter.kt`, `CommandExtensions.kt`, `BolusValidation.kt`, `BolusLimits.kt`; `ExpectedCommand.Bolus` (tylko typ) w `parser/ExpectedCommand.kt`; testy `BolusCommandTest`, `CommandCatalogTest`. Wymagania: BOL-01, BOL-02, BOL-04 – BOL-06. Etykiety `bezpieczenstwo`, `trudnosc:latwe`.
2. **Parser bolusa** – `parser/AapsReply.kt` (znaczniki, nowe warianty), `AapsPl3426Patterns.kt`, `AapsReplyParser.kt`; kopie plików `dane/` do `app/src/test/resources/parser/aaps-3.4.2.6-pl/`; testy `BolusReplyParserTest`, `ParserRedactionTest`. Wymagania: BOL-07 – BOL-09. Zależy od 1. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
3. **Sesja bolusa** – `session/CommandSession.kt` (znaczniki zamiast klas `CARBS`), `SessionState.kt` (`BolusConfirmed`); minimalne gałęzie `BolusConfirmed` w `ui/commands/ResultMapper.kt` i `data/HistoryCodec.kt`, żeby kod się kompilował (pełne teksty w zad. 5 i 4); testy `CommandSessionBolusTest`, regresja testów Spec 004. Wymagania: BOL-10, BOL-11. Zależy od 2. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
4. **Historia bolusa i migracja** – `data/HistoryCommand.kt`, `HistoryOutcome.kt`, `HistoryRow.kt`, `HistoryEntry.kt`, `HistoryCodec.kt`, `data/platform/HistoryEntity.kt`, `AapsRemoteDatabase.kt` (wersja 2, `MIGRATION_1_2`), `DataModule.kt`, `app/schemas/…/2.json`; testy `HistoryCodecBolusTest`, `DatabaseSchemaV2Test`. Wymagania: BOL-17 (dane). Zależy od 3. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
5. **UI: sekcja Bolus, okno, podsumowanie** – `ui/commands/CommandsViewModel.kt`, `CommandsUiState.kt`, `CommandsPanel.kt`, `CommandDialogs.kt`, `CarbsSummary.kt` → uogólnione podsumowanie, `CommandTexts.kt`, `strings.xml`; testy `CommandsViewModelBolusTest`. Wymagania: BOL-13 – BOL-15, BOL-18 (część). Zależy od 1 oraz zadania 4 Spec 011. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. PR opisuje W1, W5.
6. **Launcher: odstęp + przebieg/wynik/historia UI** – `ui/commands/CommandLauncher.kt`, `BolusInterval.kt`, `LaunchResult.kt`, `CommandMessage.kt`, `ProgressSteps.kt`, `ResultMapper.kt`, `ResultMessages.kt`, `RunTexts.kt`, `ui/history/HistoryLabels.kt`, `HistoryEntryMapper.kt`, `strings.xml`; testy `BolusIntervalTest`, `CommandLauncherBolusTest`, `ResultMapperBolusTest`, `HistoryEntryMapperBolusTest`. Wymagania: BOL-12, BOL-16, BOL-17 (UI). Zależy od 3, 4, 5. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. PR opisuje W2 – W4. Jeśli przekroczy 400 linii – podział na 6a (odstęp) i 6b (przebieg, wynik, historia).

Zależności: 1 → 2 → 3 → 4 → 6; 5 równolegle z 2–4 (po 1).
