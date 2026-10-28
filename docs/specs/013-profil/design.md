# Design 013: Profil – stan, lista i zmiana profilu

Dotyczy: [spec.md](spec.md)

## Przegląd

Trzy nowe wpisy katalogu, nowe konteksty i wyniki parsera, bez nowych faz sesji (korzysta ze znaczników `MatchingCodeRequest` i `CommandMismatch` z design 012). Lista profili wraca do `ui` w `SessionOutcome.Completed.reply`; wybór pozycji przechodzi z `CommandRunViewModel` do `CommandsViewModel` przez singleton `ProfileSelection` (jak `CurrentRun`).

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.commands

data class ProfileChoice(val number: Int, val name: String) { override fun toString() = "ProfileChoice(***)" }

sealed interface Command {
    data object ProfileStatus : Command   // id PROFILE_STATUS
    data object ProfileList : Command     // id PROFILE_LIST
    /** Tylko przez of()/parsePercent() (PROF-02, PROF-03). toString() bez danych. */
    class ProfileSwitch private constructor(val number: Int, val profileName: String, val percent: Int) : Command {
        companion object {
            const val DEFAULT_PERCENT = 100
            fun of(choice: ProfileChoice, percent: Int): ProfileSwitchValidation
            fun parsePercent(choice: ProfileChoice, input: String): ProfileSwitchValidation
        }
    }
}

object ProfileLimits { const val MIN_PERCENT = 50; const val MAX_PERCENT = 200; const val MAX_NUMBER = 99 }

sealed interface ProfileSwitchValidation {
    data class Valid(val command: Command.ProfileSwitch) : ProfileSwitchValidation
    data class Invalid(val error: ProfileSwitchError) : ProfileSwitchValidation
}
enum class ProfileSwitchError { INVALID_CHOICE, EMPTY, INVALID_CHARACTERS, BELOW_MIN, ABOVE_MAX }

// smsText(): ProfileStatus -> "PROFILE STATUS"; ProfileList -> "PROFILE LIST";
//            ProfileSwitch -> if (percent == 100) "PROFILE $number" else "PROFILE $number $percent"
// toExpectedCommand(): ExpectedCommand.ProfileStatus / ProfileList / ProfileSwitch(profileName, percent)
```

```kotlin
package pl.twojanazwa.aapsremote.parser

sealed interface ExpectedCommand {
    data object ProfileStatus : ExpectedCommand
    data object ProfileList : ExpectedCommand
    /** require: profileName niepusta bez LF, percent ≥ 1. toString() bez danych. */
    class ProfileSwitch(val profileName: String, val percent: Int) : ExpectedCommand
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

Wzorce (`matchEntire`):

```kotlin
val PROFILE_STATUS = Regex("""([^\n]+) \(([1-9][0-9]{0,3})%\)""")
val PROFILE_LIST_LINE = Regex("""([1-9][0-9]?)\. ([^\n]+)""")         // na każdej linii osobno
val PROFILE_CODE_REQUEST = Regex(
    """Aby przelaczyc profil na ([^\n]+) ([1-9][0-9]{0,3})% odpowiedz kodem z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em""",
)
```

`ProfileStatus`: pierwsza grupa nie może być pusta ani zaczynać się spacją (` (115%)` → nierozpoznana). `ProfileList`: split po `\n`, każda linia pasuje, numer = indeks + 1, nazwy różne. `ProfileSwitch(N, p)`: `WRONG_CODE` → `WrongCode`; `PROFILE_CODE_REQUEST` → grupa 1 == N && grupa 2 == p ? `ProfileCodeRequest` : `ProfileSwitchMismatch`; inaczej `Unrecognized`. Zachłanna grupa nazwy przy nietypowej nazwie może dać inny podział – kończy się wtedy niezgodnością (stop), nigdy kodem.

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

/** Singleton Hilt; wybór z wyniku listy do formularza. Tylko w pamięci. */
@Singleton class ProfileSelection @Inject constructor() {
    val choice: StateFlow<ProfileChoice?>
    fun select(choice: ProfileChoice)
    fun clear()
}
// ResultView: + profileItems: List<ProfileChoice>? (tylko Completed PROFILE_LIST).
// CommandRunViewModel.onProfileChosen(number): select + onResultDismissed().
// CommandsViewModel: onProfileStatusClicked, onProfileListClicked, onProfilePercentChanged,
//   onProfileNextClicked, onProfileFormCancelled; formularz: ProfileFormState(choice, percentText, error).
```

## Model danych i stany

`CommandsViewModel` – formularz profilu:

| Stan | Zdarzenie | Nowy stan | Wymaganie |
| --- | --- | --- | --- |
| panel | `ProfileSelection.choice` → wybór | formularz (wybór, `"100"`), `selection.clear()` | PROF-14 |
| formularz | „Dalej”, `Invalid` | błąd pod polem | PROF-15 |
| formularz | „Dalej”, `Valid` | podsumowanie z PIN-em dla polecenia | PROF-15 |
| formularz / podsumowanie | „Anuluj”, Wstecz, `onStop()` | panel, PIN `""`, wybór porzucony | PROF-15 |
| podsumowanie | „Wyślij” | jak CMDUI-11 – CMDUI-13 | PROF-15 |

Znacznik `secureWindow` `CommandsViewModel` = dotychczasowe warunki ∨ formularz profilu otwarty.

Historia: `HistoryCommand` + `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`; `HistoryRow`/`HistoryEntity`/`HistoryEntry` + `profileNumber: Int?`, `profilePercent: Int?`; baza wersja 3, `MIGRATION_2_3`; obie migracje w jednym `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Porównanie nazwy i procentu w prośbie o kod | tylko procent; numer | AAPS nie podaje numeru w prośbie; nazwa wykrywa zmianę listy po pobraniu. |
| Lista przez zwykłą sesję `PROFILE LIST` | osobny mechanizm pobierania | Ta sama ścieżka co `BG`: jedno aktywne polecenie, limity, historia. |
| `ProfileSelection` jako singleton | parametr nawigacji, `SavedStateHandle` | Dane AAPS nie trafiają do zapisywanego stanu instancji; wzorzec `CurrentRun`. |
| Wybór porzucany przy `onStop()` | trzymanie do powrotu | Prościej i bezpieczniej (lista może się zestarzeć; P8). |
| Bez wyniku „potwierdzono zmianę profilu” | zgadnięty wzorzec | Treść nieznana – `Unknown` z treścią (P4). |
| Powód `AMOUNT_MISMATCH` dla profilu | nowy `PROFILE_MISMATCH` | Bez zmiany `FailureReason`, DATA-09 i testów parytetu historii; tekst rozróżnia `ui` po poleceniu. |
| Osobna migracja 2 → 3 | wspólna z bolusem | Każda spec ma własne zadanie danych; kolejność 012 → 013. |

## Mapowanie wymagań na testy

| Wymaganie | Test |
| --- | --- |
| PROF-01 – PROF-05 | `ProfileCommandTest`, `CommandCatalogTest` |
| PROF-06 – PROF-10 | `ProfileReplyParserTest`, `ParserRedactionTest` |
| PROF-11, PROF-12 | `CommandSessionProfileTest`, `SessionCoordinatorProfileTest` |
| PROF-13 – PROF-15 | `CommandsViewModelProfileTest`, `CommandRunViewModelProfileTest`, `ProfileSelectionTest` |
| PROF-16 | `ResultMapperProfileTest`, `ProgressStepsTest` |
| PROF-17 | `HistoryCodecProfileTest`, `DatabaseSchemaV3Test`, `HistoryEntryMapperProfileTest`; W3 |
| PROF-18 | testy `toString()` w powyższych, testy braku logowania |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Warunki wstępne: zadania 1 – 5 Spec 012 (znaczniki parsera, `ReplyMatch`, migracja 1 → 2, uogólnione teksty wyniku i launcher).

**Zasada kolejności:** zmianę profilu można wysłać („Wyślij”) dopiero w zadaniu 5 – po krokach przebiegu, tekstach wyniku i historii (zadania 3, 4). Przyciski `PROFILE STATUS`/`PROFILE LIST` w zadaniu 4 nie wymagają kodu.

**Miejsca, które przestają się kompilować po dodaniu `CommandId.PROFILE_*`, obiektów `Command.Profile*` i `ExpectedCommand.Profile*`** (sprawdzone na `main`, jak w design 012): `commands/CommandCatalog.kt`, `commands/CommandExtensions.kt`, `data/HistoryCodec.kt` (`encode(CommandId)`), `ui/commands/RunTexts.kt` (`CommandId.titleRes`), `ui/commands/ResultMapper.kt` (`when (command)`), `parser/AapsReplyParser.kt` (`when (expected)`), `session/ReplyMatch.kt` (Spec 012, `when` po `CommandId`). Zadanie 1 dodaje w nich gałęzie minimalne (parser → `Unrecognized`, `ReplyMatch` → `false`, nagłówki `run_title_profile_*`, `DETAILS_MISSING`, tekst polecenia w historii), więc po zadaniu 1 polecenia profilu nie mogą odesłać kodu.

1. **Katalog profilu** – **dodaje `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH` do `CommandId`** (wszystkie trzy w tym zadaniu); `commands/Command.kt`, `CommandId.kt`, `CommandCatalog.kt`, `CommandDefinition.kt` (słowa kluczowe ze spacją), `CommandExtensions.kt`, `ProfileChoice.kt`, `ProfileLimits.kt`, `ProfileSwitchValidation.kt`; `parser/ExpectedCommand.kt`, `parser/AapsReplyParser.kt`, `session/ReplyMatch.kt`, `data/HistoryCodec.kt`, `ui/commands/RunTexts.kt`, `ui/commands/ResultMapper.kt`, `strings.xml`; testy `ProfileCommandTest`, `CommandCatalogTest`. Wymagania: PROF-01 – PROF-05. Etykiety `bezpieczenstwo`, `trudnosc:latwe`.
2. **Parser profilu** – `parser/AapsReply.kt`, `AapsPl3426Patterns.kt`, `AapsReplyParser.kt`, kopie plików `dane/` do zasobów testowych; testy `ProfileReplyParserTest`. Wymagania: PROF-06 – PROF-10. Zależy od 1. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
3. **Sesja i historia profilu** – `session/CommandSession.kt` (`AWAITING_REPLY` przyjmuje `ProfileStatusReport` tylko dla `PROFILE_STATUS` i `ProfileListReport` tylko dla `PROFILE_LIST`), `session/ReplyMatch.kt` (`ProfileCodeRequest` – `PROFILE_SWITCH`), `data/HistoryCommand.kt`, `HistoryRow.kt`, `HistoryEntry.kt`, `HistoryCodec.kt`, `data/platform/HistoryEntity.kt`, `AapsRemoteDatabase.kt` (wersja 3, `MIGRATION_2_3` w tym samym `addMigrations`), `app/schemas/…/3.json`; testy `CommandSessionProfileTest`, `HistoryCodecProfileTest`, `DatabaseSchemaV3Test` (w tym ścieżka 1 → 3). Wymagania: PROF-11, PROF-12, PROF-17 (dane). Zależy od 2. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. **Warunek scalenia:** W3 (bazy w wersji 2 i 1) sprawdzone na telefonie, wynik w opisie PR.
4. **UI: przyciski, wynik listy, wybór** – `ui/commands/ProfileSelection.kt`, `CommandRunViewModel.kt`, `ResultView.kt`, `ResultMapper.kt`, `ResultCard.kt`, `RunTexts.kt`, `ProgressSteps.kt`, `CommandsPanel.kt` (sekcja Profil), `strings.xml`; testy `CommandRunViewModelProfileTest`, `ResultMapperProfileTest`, `ProfileSelectionTest`. Wymagania: PROF-13, PROF-14, PROF-16. Zależy od 3. Etykiety `bezpieczenstwo`, `trudnosc:trudne`. PR opisuje W1.
5. **UI: formularz procentu, podsumowanie z „Wyślij”, historia** – `ui/commands/CommandsViewModel.kt`, `CommandsUiState.kt`, `ProfileForm.kt`, uogólnione podsumowanie, `CommandTexts.kt`, `ui/history/HistoryLabels.kt`, `HistoryEntryMapper.kt`, `strings.xml`; testy `CommandsViewModelProfileTest`, `HistoryEntryMapperProfileTest`. Wymagania: PROF-15, PROF-17 (UI), PROF-18. Zależy od 4 i zadania 6 Spec 012 (uogólnione podsumowanie). Etykiety `bezpieczenstwo`, `trudnosc:trudne`. PR opisuje W2 – W4.
6. **Scalenie wpisów planowanych do spec 002–010** – PR `Spec: zmiana …` po wdrożeniu zadań Spec 011 – 013: treść wymagań wymienionych we wpisach „Planowane zmiany (Spec 011 – 013)” w „Historii zmian” spec 002, 003, 004, 007, 008, 009, 010 (np. CMD-01, CMD-03 – CMD-06, PARSER-01, SESSION-06, SESSION-08, SESSION-11 – SESSION-13, DATA-07, DATA-09, DATA-14, CMDUI-01, CMDUI-04, CMDUI-07 – CMDUI-12, CMDUI-16, CMDUI-17, CMDUI-19, CMDUI-23, HISTUI-03, HISTUI-04) przepisana zgodnie z 011 – 013 i ze stanem kodu; wpisy „planowane” zamienione na zwykłe. Tylko dokumentacja. Zależy od wszystkich zadań Spec 011 – 013. Etykiety `trudnosc:latwe`; recenzja `recenzent` (PR `Spec: …`).

Zależności: 1 → 2 → 3 → 4 → 5 → 6.
