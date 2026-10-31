# Design 013: Profil – stan i zmiana profilu

Dotyczy: [spec.md](spec.md)

## Przegląd

Trzy wpisy katalogu, konteksty i wyniki parsera, bez nowych faz sesji (korzysta ze znaczników `MatchingCodeRequest` i `CommandMismatch` z design 012). Od zmiany 2026-10-30 zmiana profilu dotyczy zawsze profilu 1: `Command.ProfileSwitch` niesie tylko procent, kontekst parsera i druga linia obrony w sesji porównują tylko procent, a UI otwiera formularz procentu przyciskiem „Zmień profil” (bez pobierania listy, bez `ProfileSelection`). `PROFILE LIST` zostaje w katalogu, parserze i sesji bez przycisku w UI (P11).

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.commands

sealed interface Command {
    data object ProfileStatus : Command   // id PROFILE_STATUS
    data object ProfileList : Command     // id PROFILE_LIST; bez przycisku w UI (P11)
    /** Tylko przez of()/parsePercent() (PROF-02, PROF-03). toString() bez danych. */
    class ProfileSwitch private constructor(val percent: Int) : Command {
        /** Zawsze 1 (PROF-02); pole zostaje dla historii (PROF-17) i nagłówka (PROF-16). */
        val number: Int get() = ProfileLimits.PROFILE_NUMBER
        companion object {
            const val DEFAULT_PERCENT = 100
            fun of(percent: Int): ProfileSwitchValidation
            fun parsePercent(input: String): ProfileSwitchValidation
        }
    }
}

object ProfileLimits { const val MIN_PERCENT = 50; const val MAX_PERCENT = 200; const val PROFILE_NUMBER = 1 }

sealed interface ProfileSwitchValidation {
    data class Valid(val command: Command.ProfileSwitch) : ProfileSwitchValidation
    data class Invalid(val error: ProfileSwitchError) : ProfileSwitchValidation
}
enum class ProfileSwitchError { EMPTY, INVALID_CHARACTERS, BELOW_MIN, ABOVE_MAX }

// smsText(): ProfileStatus -> "PROFILE STATUS"; ProfileList -> "PROFILE LIST";
//            ProfileSwitch -> "PROFILE $number $percent" (zawsze z procentem, PROF-04)
// toExpectedCommand(): ExpectedCommand.ProfileStatus / ProfileList / ProfileSwitch(percent)
// CommandParameter.ProfileAndPercent(minPercent, maxPercent, defaultPercent) – bez zmian pól; KDoc: numer stały 1.
// Usuwane: ProfileChoice, ProfileSwitchError.INVALID_CHOICE, ProfileLimits.MAX_NUMBER.
```

```kotlin
package pl.twojanazwa.aapsremote.parser

sealed interface ExpectedCommand {
    data object ProfileStatus : ExpectedCommand
    data object ProfileList : ExpectedCommand
    /** require: percent ≥ 1. toString() bez danych. Bez nazwy profilu (PROF-05). */
    class ProfileSwitch(val percent: Int) : ExpectedCommand
}

data class ProfileListItem(val number: Int, val name: String) { override fun toString() = "ProfileListItem(***)" }

sealed interface AapsReply {
    data class ProfileStatusReport(val text: String) : AapsReply                                  // PROF-06
    data class ProfileListReport(val items: List<ProfileListItem>, val text: String) : AapsReply  // PROF-07
    data class ProfileCodeRequest(val text: String) : MatchingCodeRequest                        // PROF-08
    data class ProfileSwitchMismatch(val text: String) : CommandMismatch                         // PROF-09
    // toString() każdego: "AapsReply.<Nazwa>(***)"
}
```

Wzorce (`matchEntire`) bez zmian:

```kotlin
val PROFILE_STATUS = Regex("""([^\n]+) \(([1-9][0-9]{0,3})%\)""")
val PROFILE_LIST_LINE = Regex("""([1-9][0-9]?)\. ([^\n]+)""")         // na każdej linii osobno
val PROFILE_CODE_REQUEST = Regex(
    """Aby przelaczyc profil na ([^\n]+) ([1-9][0-9]{0,3})% odpowiedz kodem z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em""",
)
```

`ProfileSwitch(p)`: `WRONG_CODE` → `WrongCode`; `PROFILE_CODE_REQUEST` → grupa 2 == p ? `ProfileCodeRequest` : `ProfileSwitchMismatch`; inaczej `Unrecognized`. Grupa 1 (nazwa) nie jest porównywana (PROF-08). Grupa 2 to liczba bezpośrednio przed `% odpowiedz kodem z aplikacji Authenticator dla: `; przy nietypowej nazwie zachłanna grupa 1 może dać inny podział – wtedy procent się nie zgodzi (stop) albo zgodzi się z liczbą stojącą bezpośrednio przed tym tekstem.

```kotlin
package pl.twojanazwa.aapsremote.session

// CommandSession.amountMatches – druga linia obrony (PROF-12):
//   is AapsReply.ProfileCodeRequest -> sent is Command.ProfileSwitch && profileRequestMatches(reply.text, sent)
// profileRequestMatches: normalizacja, PROFILE_CODE_REQUEST.matchEntire, g[2].toIntOrNull() == sent.percent (bez g[1]).
```

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

/** Formularz zmiany profilu 1 (PROF-15). toString() bez danych. */
class ProfileFormState(val percentText: String, val error: ProfileSwitchError? = null)

// CommandsViewModel: onProfileStatusClicked() (bez zmian), onProfileSwitchClicked() (nowa: otwiera formularz
//   z "100", gdy canStartProfile), onProfilePercentChanged, onProfileNextClicked, onProfileFormCancelled.
//   Usuwane: onProfileListClicked, parametr ProfileSelection i kolektor wyboru.
// CommandRunViewModel: konstruktor (currentRun, coordinator) – bez ProfileSelection i TotpSecretStore;
//   usuwane onProfileChosen i pole RunUiState.secret.
// ResultView / RunView: profileName -> profileNumber: Int?; usuwane profileItems.
// ResultCard: bez pozycji listy i parametru onProfileChosen. Usuwany plik ProfileSelection.kt (i ProfileChoice.kt w commands).
// runTitle(PROFILE_SWITCH): R.string.run_title_profile_switch z argumentami (number, percent).
```

### Zmiana 2026-10-31: odpowiedzi po kodzie (PROF-19 – PROF-21)

```kotlin
package pl.twojanazwa.aapsremote.parser

sealed interface AapsReply {
    /** PROF-19. Bez procentu i nazwy (AAPS ich nie podaje). */
    data class ProfileSwitchDone(val text: String) : MatchingDone {
        override fun toString(): String = "AapsReply.ProfileSwitchDone(***)"
    }
    /** PROF-20. Powiadomienie do wszystkich numerów (R11) – nie MatchingDone, nie MatchingCodeRequest. */
    data class ProfileBaseUpdated(val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.ProfileBaseUpdated(***)"
    }
}

// AapsPl3426Patterns (matchEntire na całej treści):
val PROFILE_SWITCH_DONE = Regex("""Zmiana profilu wykonana""")
val PROFILE_BASE_UPDATED = Regex("""Profil bazowy w pompie uaktualniony""")
// parseProfileSwitch: WRONG_CODE → PROFILE_CODE_REQUEST → PROFILE_SWITCH_DONE → PROFILE_BASE_UPDATED → null
```

```kotlin
package pl.twojanazwa.aapsremote.session

sealed interface SessionOutcome {
    /** PROF-19: „AAPS potwierdził zmianę profilu” – nie dowód, że zmieniono profil 1 na percent. */
    data class ProfileSwitchConfirmed(val percent: Int, val text: String) : SessionOutcome {
        override fun toString(): String = "ProfileSwitchConfirmed(***)"
    }
}

// ReplyMatch.isDoneFor: CommandId.PROFILE_SWITCH -> reply is AapsReply.ProfileSwitchDone
// CommandSession.amountMatches: is ProfileSwitchDone, is ProfileBaseUpdated -> sent is Command.ProfileSwitch
// CommandSession.confirmed: is ProfileSwitchDone -> ProfileSwitchConfirmed((command as Command.ProfileSwitch).percent, codeRequestText + "<pusta linia>" + text)
//   (procent z polecenia sesji – treść AAPS go nie zawiera; treść prośby o kod z nazwą profilu zachowana w text, PROF-21)
// processMessage, AWAITING_CONFIRMATION: is AapsReply.ProfileBaseUpdated -> addUnexpected(sms)
//   (przed kodem – istniejąca gałąź else → Failed(UNRECOGNIZED_REPLY))
```

`data`: `HistoryOutcome.ProfileSwitchConfirmed` (bez pól, jak `BolusConfirmed`), literał `PROFILE_SWITCH_CONFIRMED` w `HistoryCodec`. `ui`: `ResultMapper` (sukces, argument = `percent`), `HistoryEntryMapper` (argument = `profilePercent` wpisu, brak → „Brak szczegółów wyniku …”), klucze `result_profile_switch_confirmed`, `history_outcome_profile_switch_confirmed` w `strings.xml`.

## Model danych i stany

`CommandsViewModel` – formularz profilu:

| Stan | Zdarzenie | Nowy stan | Wymaganie |
| --- | --- | --- | --- |
| panel, `canStartProfile` | „Zmień profil” | formularz (`"100"`, bez błędu) | PROF-13 |
| panel, `!canStartProfile` | „Zmień profil” | bez zmian | PROF-13 |
| formularz | „Dalej”, `Invalid` | błąd pod polem | PROF-15 |
| formularz | „Dalej”, `Valid` | podsumowanie z PIN-em dla polecenia (jedna zmiana stanu: formularz zamknięty, podsumowanie otwarte) | PROF-15 |
| formularz / podsumowanie | „Anuluj”, Wstecz, `onStop()` | panel, PIN `""` | PROF-15 |
| podsumowanie | „Wyślij” | jak CMDUI-11 – CMDUI-13 | PROF-15 |

Znacznik `secureWindow` `CommandsViewModel` = dotychczasowe warunki ∨ formularz profilu otwarty (bez zmian).

Historia bez zmian schematu: `HistoryCommand` z `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`; `profileNumber`, `profilePercent`; baza w wersji 3 (`MIGRATION_2_3`). `HistoryCodec` bierze `number` (= 1) i `percent` z `Command.ProfileSwitch`.

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Zawsze profil 1, bez listy (2026-10-30, P9) | lista profili i wybór pozycji (dotychczas) | Decyzja użytkownika: użytkownik zmienia tylko procent profilu 1; mniej kroków i mniej kodu. |
| Porównanie tylko procentu w prośbie o kod (P10) | porównanie nazwy z ustawieniem albo z `PROFILE STATUS` | Aplikacja nie zna nazwy profilu 1; dodatkowe pobranie nazwy wymagałoby drugiego polecenia. Ryzyko zaakceptowane przez użytkownika 2026-10-30, opisane w spec („Bezpieczeństwo”). |
| `PROFILE 1 <p>` także dla 100 (P9) | `PROFILE 1` dla 100 (dotychczas) | Decyzja użytkownika; jedna postać treści. Od 2026-10-31 z kodu AAPS 3.4.2.6: `PROFILE 1 100` ≡ `PROFILE 1` (R7), plan awaryjny nieaktualny. |
| `PROFILE LIST` zostaje w katalogu, parserze i sesji (P11) | usunięcie teraz | Bez zmian w Spec 002 – 004 i bez ryzyka dla starych wpisów historii; usunięcie – „Przyszłe zmiany”. |
| `Command.ProfileSwitch.number` jako stała właściwość | usunięcie pola | `HistoryCodec`, nagłówek i etykieta historii („Zmiana profilu N, P%”) zostają bez zmian. |
| Usunięcie przycisku `PROFILE LIST` w zadaniu 1 | dopiero w zadaniu 2 | Po zadaniu 1 polecenie nie zna wybranego numeru; ścieżka „lista → wybór profilu 2” wysłałaby `PROFILE 1 p`. Bez przycisku formularz jest nieosiągalny do zadania 2. |
| Bez wyniku „potwierdzono zmianę profilu” (do 2026-10-30) | zgadnięty wzorzec | Treść nieznana – `Unknown` z treścią (P4). Zastąpione 2026-10-31 przez PROF-19 (treść z kodu AAPS, R8). |
| `ProfileSwitchConfirmed` z procentem z polecenia sesji (2026-10-31) | wynik bez procentu | AAPS nie podaje procentu w potwierdzeniu; procent wysłany był sprawdzony w prośbie o kod (PROF-08, PROF-12), a komunikat („AAPS potwierdził zmianę profilu (polecenie: profil 1, P%).”) mówi o potwierdzeniu AAPS i podaje procent jako dane polecenia. Treść prośby o kod (nazwa profilu) jest dołączona do `text`, bo samo potwierdzenie jej nie zawiera. |
| `Profil bazowy w pompie uaktualniony`: po kodzie nieoczekiwana, przed kodem stop (2026-10-31) | zawsze nieoczekiwana; zawsze nierozpoznana | Po kodzie powiadomienie może towarzyszyć naszej zmianie (R11) i nie powinno kończyć sesji „nieznanym”; przed kodem oznacza zmianę profilu z innego źródła – w razie wątpliwości stop (AGENTS.md). |
| Wzorce PROF-19, PROF-20 tylko w kontekście zmiany profilu | we wszystkich kontekstach | Najmniejsza zmiana PARSER-01; inne konteksty – „Przyszłe zmiany”. |
| Powód `AMOUNT_MISMATCH` dla profilu | nowy `PROFILE_MISMATCH` | Bez zmiany `FailureReason`, DATA-09 i testów parytetu historii; tekst rozróżnia `ui` po poleceniu. |
| Osobna migracja 2 → 3 | wspólna z bolusem | Każda spec ma własne zadanie danych; kolejność 012 → 013. |

## Mapowanie wymagań na testy

| Wymaganie | Test |
| --- | --- |
| PROF-01 – PROF-05 | `ProfileCommandTest`, `CommandCatalogTest` |
| PROF-06 – PROF-10 | `ProfileReplyParserTest`, `ParserRedactionTest` |
| PROF-11, PROF-12 | `CommandSessionProfileTest`, `CommandSessionProfileFlowTest`, `SessionCoordinatorProfileTest` |
| PROF-13, PROF-15 | `CommandsViewModelProfileButtonsTest`, `CommandsViewModelProfileTest`, `CommandsSourcesTest` (brak `onProfileListClicked`, `startNoCode(Command.ProfileList)` i klucza `commands_profile_list`) |
| PROF-14 | wycofane – usuwane `ProfileSelectionTest` i testy wyboru w `CommandRunViewModelProfileTest` |
| PROF-16 | `ResultMapperProfileTest`, `RunTextsTest`, `ProgressStepsTest` |
| PROF-17 | `HistoryCodecProfileTest`, `DatabaseSchemaV3Test`, `HistoryEntryMapperProfileTest` |
| PROF-18 | testy `toString()` w powyższych, testy braku logowania |
| PROF-19, PROF-20 (parser) | `ProfileReplyParserTest`, `ParserRedactionTest` |
| PROF-19, PROF-20 (sesja) | `CommandSessionProfileTest`, `CommandSessionProfileFlowTest`, `ReplyMatchTest` |
| PROF-21 | `ResultMapperProfileTest`, `HistoryCodecProfileTest`, `HistoryEntryMapperProfileTest` |

## Podział na zadania

### Zadania zmiany 2026-10-31

Dane z kodu AAPS 3.4.2.6 dla BOLUS (Spec 012, BOL-20 – BOL-22) i PROFILE (PROF-19 – PROF-21). Każde zadanie to jeden PR, ok. 200–400 linii z testami.

**Zasada kolejności:** zadanie 1 nie zmienia zachowania sesji – nowe wyniki parsera trafiają do istniejących gałęzi (`ProfileSwitchDone` przez `ReplyMatch.isDoneFor` = fałsz staje się `Unrecognized`; `BolusRefused`, `BolusFailed`, `ProfileBaseUpdated` przechodzą `amountMatches` tylko dla własnego polecenia i trafiają do gałęzi `else` – przed kodem `Failed(UNRECOGNIZED_REPLY)`, po kodzie `Unknown(UNRECOGNIZED_REPLY)`, jak dziś). Żaden stan pośredni nie wysyła kodu na nowy wynik.

1. **Parser: odpowiedzi z kodu AAPS** – `parser/AapsReply.kt` (`BolusRefusal`, `BolusRefused`, `BolusFailed`, `ProfileSwitchDone`, `ProfileBaseUpdated`), `AapsPl3426Patterns.kt`, `AapsReplyParser.kt` (`parseBolus`, `parseProfileSwitch`); w `session/CommandSession.kt` tylko gałęzie wyczerpujących `when`, które przestają się kompilować: `amountMatches` (`sent is Command.Bolus` / `Command.ProfileSwitch`) i `confirmed` (`ProfileSwitchDone` → `error(...)` ze stałym tekstem, nieosiągalne, bo `ReplyMatch.isDoneFor` zwraca fałsz do zadania 3); kopie nowych plików `dane/` (012: `bolus-odmowa-odstep.txt`, `bolus-odmowa-kolejka.txt`, `bolus-blad.txt`; 013: `profile-sukces.txt`, `profile-bazowy-uaktualniony.txt`, `profile-nieprawidlowy.txt`) do `app/src/test/resources/parser/aaps-3.4.2.6-pl/`; testy `BolusReplyParserTest`, `ProfileReplyParserTest`, `ParserRedactionTest`, regresja `CommandSessionBolusTest`/`CommandSessionProfileTest` (nowe pliki → dotychczasowe wyniki). Wymagania: BOL-20, BOL-21 (parser), PROF-10 (`Nieprawidlowy profil!`), PROF-19, PROF-20 (parser). Etykiety `bezpieczenstwo`, `trudnosc:latwe`. Zależy od scalenia tej zmiany spec.
2. **Bolus: odmowy i błąd – sesja, wynik, historia** – `session/SessionState.kt` (`AAPS_BOLUS_INTERVAL`, `AAPS_BOLUS_QUEUE`, `BOLUS_FAILED`), `session/CommandSession.kt` (gałęzie z design 012), `data/HistoryCodec.kt`, `ui/commands/ResultMessages.kt`, `ResultMapper.kt`, `ui/history/HistoryEntryMapper.kt`, `strings.xml`; testy `CommandSessionBolusTest`, `BolusIntervalTest`, `ResultMapperBolusTest`, `HistoryCodecBolusTest`, `HistoryCodecTest` (parytet), `HistoryEntryMapperBolusTest`, test wyczerpującego odwzorowania CMDUI-19 (10 `FailureReason`, 8 `UnknownReason`). Wymagania: BOL-20, BOL-21 (sesja), BOL-22. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. Zależy od 1. PR opisuje W6 (Spec 012), jeśli wykonane.
3. **Profil: potwierdzenie i powiadomienie – sesja, wynik, historia** – `session/SessionState.kt` (`ProfileSwitchConfirmed`), `session/ReplyMatch.kt`, `session/CommandSession.kt` (`confirmed`, `ProfileBaseUpdated` po kodzie), `data/HistoryOutcome.kt`, `HistoryCodec.kt`, `ui/commands/ResultMapper.kt`, `ResultMessages.kt`, `ui/history/HistoryEntryMapper.kt`, `strings.xml`; testy `CommandSessionProfileTest`, `CommandSessionProfileFlowTest`, `ReplyMatchTest`, `ResultMapperProfileTest`, `HistoryCodecProfileTest`, `HistoryEntryMapperProfileTest`. Wymagania: PROF-19, PROF-20 (sesja), PROF-21. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. Zależy od 1; z zadaniem 2 dzieli pliki `SessionState.kt`, `CommandSession.kt`, `HistoryCodec.kt`, `ResultMapper.kt` – kto później, ten scala. PR opisuje W5.
4. **Scalenie planowanych zmian w spec 002, 004, 007, 009, 010** – PR `Spec: zmiana …` po zadaniach 2 – 3 i po #259: PARSER-01 (nowe wyniki w kontekstach `Bolus x` i zmiany profilu), SESSION-11 – SESSION-13 (tabele: odmowy, błąd bolusa, potwierdzenie i powiadomienie profilu), DATA-09 (`PROFILE_SWITCH_CONFIRMED`, nowe powody), CMDUI-19 (wiersze i liczby w kryterium: 10 `FailureReason`, 8 `UnknownReason`), HISTUI-04 (teksty wyników). Tylko dokumentacja. Etykieta `trudnosc:latwe`; recenzja `recenzent` (PR `Spec: …`). Zależy od 2, 3 i #259.

Zależności: 1 → 2, 1 → 3; 2, 3 → 4. Zakres to proponowane minimum (potwierdzenie zmiany profilu, odmowa przez odstęp, błąd bolusa) plus odmowa przez kolejkę (jeden wzorzec i jedna wartość `FailureReason` więcej) i powiadomienie o profilu bazowym; pozostałe odmowy – „Przyszłe zmiany” w Spec 012 i 013.

### Zadania zmiany 2026-10-30

Każde zadanie to jeden PR, ok. 200–400 linii z testami (zadanie 3 i 4 mniejsze). Zadania 1 – 6 pierwszej wersji (niżej) są scalone.

**Zasada kolejności:** po zadaniu 1 w panelu nie ma przycisku otwierającego formularz zmiany profilu (usunięty przycisk `PROFILE LIST`), więc nie da się wysłać zmiany profilu, dopóki zadanie 2 nie doda „Zmień profil”. Żadna wersja pośrednia nie wysyła `PROFILE 1 p` po wyborze innego profilu z listy.

1. **Profil 1 bez nazwy: katalog, parser, sesja** – `commands/Command.kt` (`ProfileSwitch(percent)`, `number`, `of(percent)`, `parsePercent(input)`), `ProfileLimits.kt` (`PROFILE_NUMBER`, bez `MAX_NUMBER`), `ProfileSwitchValidation.kt` (bez `INVALID_CHOICE`), `CommandExtensions.kt` (`PROFILE 1 <p>`, kontekst z procentem), `CommandParameter.kt` (KDoc); `parser/ExpectedCommand.kt`, `parser/AapsReplyParser.kt` (porównanie tylko procentu); `session/CommandSession.kt` (`profileRequestMatches` tylko procent); w `ui` tylko to, co przestaje się kompilować: `CommandsViewModel.onProfileNextClicked` (`parsePercent(form.percentText)`), `CommandTexts.kt` (argumenty podsumowania: numer i procent, bez `INVALID_CHOICE`), `RunUiState.kt`, `ResultView.kt`, `ResultMapper.kt`, `CommandRunViewModel.kt`, `RunTexts.kt`, `RunScreen.kt`, `ResultCard.kt` (`profileName` → `profileNumber`), `strings.xml` (`commands_profile_summary_title`, `commands_profile_summary_text`, `run_title_profile_switch`, `result_profile_amount_mismatch`, `result_profile_unknown_amount_mismatch` – teksty z PROF-15, PROF-16); `CommandsPanel.kt` i `CommandsScreen.kt` – usunięcie przycisku „Zmień profil…” (`PROFILE LIST`) i parametru `onProfileListClicked`; testy `ProfileCommandTest`, `CommandCatalogTest`, `ProfileReplyParserTest`, `CommandSessionProfileTest` (w tym: prośba o inny procent → bez kodu; prośba o inną nazwę przy zgodnym procencie → kod `0504714321`; prośba sprzed wysłania → bez kodu), `CommandSessionProfileFlowTest`, `ResultMapperProfileTest`, `RunTextsTest`, `HistoryCodecProfileTest` (numer 1). Wymagania: PROF-02 – PROF-05, PROF-08 – PROF-10, PROF-12, PROF-16 (nagłówek, teksty niezgodności), PROF-17 (numer 1). Etykiety `bezpieczenstwo`, `trudnosc:trudne`. Zależy od zmiany spec (ten PR).

   **Stan pośredni po zadaniu 1:** kod wyboru z listy zostaje, bez wywołującego z UI: `ProfileChoice`, `ProfileSelection`, `CommandRunViewModel.onProfileChosen`, `ProfileFormState.choice`, `profileItems` w `ResultView`/`ResultCard`. Zadanie 1 zmienia w nim tylko to, co przestaje się kompilować (wywołania `ProfileSwitch.of(choice, …)`/`parsePercent(choice, …)` → `of(percent)`/`parsePercent(input)`, numer wyboru ignorowany); testy wyboru zostają i są dostosowane tylko tam, gdzie się nie kompilują. Bez przycisku `PROFILE LIST` żaden wynik listy nie powstaje z UI, więc `ProfileSelection` nic nie dostaje, a formularz jest nieosiągalny do zadania 2. Tekst wyniku `PROFILE LIST` w kodzie zostaje w zadaniu 1 bez zmian (nieosiągalny z UI) i zmienia się w zadaniu 2 razem z usunięciem wyboru.
2. **UI: „Zmień profil” i formularz bez listy** – `ui/commands/CommandsViewModel.kt` (`onProfileSwitchClicked`, bez `ProfileSelection` i `onProfileListClicked`), `CommandsUiState.kt` (`ProfileFormState(percentText, error)`), `ProfileForm.kt` (tytuł „Zmiana profilu 1”), `CommandsPanel.kt` i `CommandsScreen.kt` (przycisk „Zmień profil”), `CommandRunViewModel.kt` (bez `onProfileChosen`, `ProfileSelection`, `TotpSecretStore`), `RunUiState.kt` (bez `secret`), `ResultView.kt`, `ResultMapper.kt`, `ResultCard.kt` (bez `profileItems` i pozycji), usunięcie `ProfileSelection.kt` i `commands/ProfileChoice.kt`, `strings.xml` (`commands_profile_switch` „Zmień profil”, `commands_profile_form_title` „Zmiana profilu %1$d”; usunięcie `commands_profile_list`, `result_profile_item`); testy `CommandsViewModelProfileButtonsTest`, `CommandsViewModelProfileTest`, `CommandRunViewModelProfileTest` (bez wyboru), `ResultMapperProfileTest` (wynik listy bez pozycji), `CommandsSourcesTest`, `CommandsRedactionTest`; usunięcie `ProfileSelectionTest`. Tekst wyniku `PROFILE LIST` w `strings.xml` zmienia się na „Lista profili z AAPS.” (bez „Wybierz profil, aby go zmienić.”). Wymagania: PROF-13, PROF-14 (usunięcie), PROF-15, PROF-16 (wynik listy), PROF-18; CMDUI-04, CMDUI-06 (spec 009, część profilu). Etykiety `bezpieczenstwo`, `trudnosc:latwe`. **Warunek scalenia: W2 wykonane na telefonie (w tym `PROFILE 1 100`, O1), wynik w opisie PR**; gdy O1 wypadnie negatywnie – najpierw PR `Spec:` z planem awaryjnym (PROF-04). (Nieaktualne od 2026-10-31: O1 rozstrzyga kod AAPS, R7.) PR opisuje też W1 i W4. Zależy od 1.
3. **Panel: kolejność sekcji i fokus pola PIN** – `ui/commands/CommandsPanel.kt` (kolejność: banery, wskaźnik uruchamiania, Bolus, Profil, CARBS, BG, PUMP), `CommandSummary.kt` (`FocusRequester`, `LocalWindowInfo.current.isWindowFocused`, `LocalSoftwareKeyboardController`; design 009, „Fokus pola PIN”); testy `CommandsSourcesTest` (kolejność sekcji w źródle `CommandsPanel.kt`, wywołania fokusu w `CommandSummary.kt`, brak `rememberSaveable`). Wymagania: CMDUI-01 (kolejność), CMDUI-28 (spec 009). Etykiety `bezpieczenstwo` (pole PIN), `trudnosc:latwe`. PR opisuje W1 i W14 ze spec 009. Bez twardej zależności; `CommandsPanel.kt` wspólny z zadaniami 1 i 2 – kto później, ten scala.
4. **Scalenie wpisów planowanych w spec 002, 003, 004, 007** – PR `Spec: zmiana …` po zadaniach 1 – 2: treść PARSER-01 (kontekst zmiany profilu tylko z procentem, kryterium „pusta nazwa”), CMD-01 (parametr `PROFILE_SWITCH`), CMD-07 (kryterium `PROFILE 1 100`), CMD-08 (kontekst z procentem), CMD-09 (`toString()` bez nazwy), SESSION-11 i „Bezpieczeństwo” spec 004 (porównanie w sesji tylko procentu, bez nazwy), DATA-07 (kryterium zmiany profilu z numerem 1) przepisana zgodnie z tą spec; wpisy „Planowane zmiany (Spec 013, 2026-10-30)” zamienione na zwykłe. Tylko dokumentacja. Etykieta `trudnosc:latwe`; recenzja `recenzent` (PR `Spec: …`). Zależy od 1 i 2.

Zależności: 1 → 2 → 4; 3 niezależne.

### Zadania pierwszej wersji (2026-10-28, scalone)

Warunki wstępne: zadania 1 – 5 Spec 012 (znaczniki parsera, `ReplyMatch`, migracja 1 → 2, uogólnione teksty wyniku i launcher).

1. **Katalog profilu** – `CommandId.PROFILE_*`, `Command.Profile*`, `ProfileChoice`, `ProfileLimits`, `ProfileSwitchValidation`, gałęzie minimalne w parserze, `ReplyMatch`, historii i `ui`. Wymagania: PROF-01 – PROF-05.
2. **Parser profilu** – `AapsReply`, `AapsPl3426Patterns`, `AapsReplyParser`, kopie plików `dane/`. Wymagania: PROF-06 – PROF-10.
3. **Sesja i historia profilu** – `CommandSession`, `ReplyMatch`, historia, baza w wersji 3 (`MIGRATION_2_3`). Wymagania: PROF-11, PROF-12, PROF-17 (dane). W3 sprawdzone na telefonie.
4. **UI: przyciski, wynik listy, wybór** – `ProfileSelection`, `CommandRunViewModel`, wynik, sekcja „Profil”. Wymagania: PROF-13, PROF-14, PROF-16.
5. **UI: formularz procentu, podsumowanie z „Wyślij”, historia** – Wymagania: PROF-15, PROF-17 (UI), PROF-18.
6. **Scalenie wpisów planowanych do spec 002–010** – PR „Spec: zmiana spec 002–010 po wdrożeniu 011–013” (#253).
