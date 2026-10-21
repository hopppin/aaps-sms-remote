# Design 010: UI – historia poleceń

Dotyczy: [spec.md](spec.md)

## Przegląd

Trzecia część pakietu `ui` z [AGENTS.md](../../../AGENTS.md#architektura). Zakładka Historia ma jeden ViewModel (`HistoryViewModel`, właściciel = wpis nawigacji `history`, tworzony przez `hiltViewModel()` w `NavHost`), który zbiera `CommandHistory.entries` i wywołuje `clearAll()`. Odwzorowanie wpisu na wiersz i szczegóły to czyste funkcje; odwzorowanie powodów wyniku jest wspólne z `ResultMapper` ze Spec 009.

| Klasa | Odpowiedzialność | Zależności |
| --- | --- | --- |
| `ui.commands.ResultMessages` (nowy plik, wydzielony z `ResultMapper`) | `FailureReason`/`UnknownReason`/faza → `ResultMessage`, argument minut | `session` |
| `ui.history.HistoryEntryMapper` | `HistoryEntry` → `HistoryItem` (wiersz), `HistoryDetails` (szczegóły), `HistoryResultView` | `data`, `ui.commands.ResultMessages` |
| `ui.history.HistoryTimeFormat` | chwila + strefa → tekst `dd.MM.yyyy HH:mm[:ss]` | `java.time` |
| `ui.history.HistoryViewModel` | stan treści, ponowienie odczytu, szczegóły, czyszczenie, komunikaty, Wstecz | `CommandHistory` |
| kompozycje `HistoryScreen` i in. | widok, `SecureWindowEffect(true)`, `BackHandler`, Snackbar | `HistoryViewModel` |

ViewModel nie zależy od `SessionCoordinator` (P2) ani od zegara. Formatowanie czasu odbywa się w kompozycji (`ZoneId.systemDefault()` przy każdej kompozycji), żeby stan ViewModelu nie zależał od strefy.

Układ plików:

```
ui/
  commands/ResultMessages.kt                    (nowy – wydzielony z ResultMapper)
  commands/ResultMapper.kt                      (zmiana: używa ResultMessages, zachowanie bez zmian)
  history/HistoryLabels.kt, HistoryResultView.kt, HistoryItem.kt, HistoryEntryMapper.kt, HistoryTimeFormat.kt
  history/HistoryUiState.kt, HistoryViewModel.kt
  history/HistoryTexts.kt
  history/HistoryScreen.kt, HistoryList.kt, HistoryDetailsView.kt, HistoryClearDialog.kt
  history/HistoryPlaceholderScreen.kt           (usuwany)
  navigation/AppScaffold.kt                     (zmiana: trasa history → HistoryScreen)
```

## Interfejsy

### Wspólne odwzorowanie powodów (HISTUI-04)

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

/** Wartość ResultMessage o tej samej nazwie (CMDUI-19). Wyczerpujący when. */
fun FailureReason.toResultMessage(): ResultMessage

/** UNKNOWN_<nazwa> (CMDUI-19). Wyczerpujący when. */
fun UnknownReason.toResultMessage(): ResultMessage

/** SENDING_COMMAND → SEND_TIMEOUT; każda inna faza i null → NO_RESPONSE. */
fun timedOutResultMessage(phase: SessionPhase?): ResultMessage

/** NO_RESPONSE, SEND_TIMEOUT → reply w min; UNKNOWN_CONFIRMATION_TIMEOUT → confirmation w min; inne → null. */
fun ResultMessage.minutesArg(timeouts: SessionTimeouts = SessionTimeouts()): Int?
```

`ResultMapper.map` (Spec 009) wywołuje te funkcje zamiast własnych `when` po powodach; `ResultMapperTest` przechodzi bez zmian. Gdy zadanie 2 ze Spec 009 nie jest jeszcze scalone w chwili startu zadania 1 tej spec, zadanie 1 czeka (zależność w „Podziale na zadania”).

### Wiersz i szczegóły (HISTUI-03 – HISTUI-06)

```kotlin
package pl.twojanazwa.aapsremote.ui.history

enum class HistoryCommandLabel { BG, PUMP, CARBS, CARBS_NO_AMOUNT, UNRECOGNIZED }

enum class HistoryTimeLabel { STARTED, COMMAND_SENT, CODE_SENT, FINISHED }

data class HistoryResultView(
    /** null = „Nierozpoznany wynik” (HistoryOutcome.Unrecognized). */
    val kind: ResultKind?,
    /** null = „Brak szczegółów wyniku …”. */
    val message: ResultMessage?,
    /** Gramy (CARBS_CONFIRMED) albo minuty (ResultMessage.minutesArg). */
    val messageArg: Int?,
    val codeNotSent: Boolean,
    val unknownWarning: Boolean,
) {
    override fun toString(): String   // bez messageArg (gramy)
}

data class HistoryItem(
    val id: Long,
    val command: HistoryCommandLabel,
    /** Tylko dla CARBS. */
    val grams: Int?,
    val result: HistoryResultView,
    /** startedAt ?: finishedAt (HISTUI-05). */
    val listTimeEpochMillis: Long?,
) {
    override fun toString(): String   // "HistoryItem(id=…, ***)"
}

data class HistoryDetails(
    val id: Long,
    val command: HistoryCommandLabel,
    val grams: Int?,
    val result: HistoryResultView,
    /** Kolejność = HistoryTimeLabel.entries; wartość null = „brak”. */
    val times: List<Pair<HistoryTimeLabel, Long?>>,
    val replyText: String?,
    /** null, gdy liczba = 0 (HISTUI-06). */
    val unexpectedCount: Int?,
) {
    override fun toString(): String   // "HistoryDetails(id=…, ***)"
}

object HistoryEntryMapper {
    fun item(entry: HistoryEntry, timeouts: SessionTimeouts = SessionTimeouts()): HistoryItem
    fun details(entry: HistoryEntry, timeouts: SessionTimeouts = SessionTimeouts()): HistoryDetails
    /** HISTUI-04. Wyczerpujący when po HistoryOutcome (bez else). */
    fun result(entry: HistoryEntry, timeouts: SessionTimeouts = SessionTimeouts()): HistoryResultView
}

object HistoryTimeFormat {
    /** dd.MM.yyyy HH:mm:ss w podanej strefie. */
    fun details(epochMillis: Long, zone: ZoneId): String
    /** dd.MM.yyyy HH:mm w podanej strefie. */
    fun row(epochMillis: Long, zone: ZoneId): String
}
```

Wzorce `DateTimeFormatter.ofPattern(..., Locale.ROOT)` – same cyfry i separatory, więc wynik nie zależy od języka telefonu ani danych CLDR w JDK.

### ViewModel (HISTUI-01, HISTUI-02, HISTUI-07 – HISTUI-12)

```kotlin
package pl.twojanazwa.aapsremote.ui.history

sealed interface HistoryContent {
    data object Loading : HistoryContent
    data object Empty : HistoryContent
    data object Unavailable : HistoryContent
    data class Entries(val items: List<HistoryItem>) : HistoryContent {
        override fun toString(): String   // "Entries(size=N)"
    }
}

/** W pliku HistoryLabels.kt (zadanie 1), żeby zadania 2 i 3 mogły iść równolegle. */
enum class HistoryMessage { CLEARED, CLEAR_FAILED }

data class HistoryUiState(
    val content: HistoryContent = HistoryContent.Loading,
    val details: HistoryDetails? = null,
    /** Liczba wpisów w otwartym oknie czyszczenia; null = okno zamknięte. */
    val confirmingClearCount: Int? = null,
    val clearing: Boolean = false,
    val message: HistoryMessage? = null,
) {
    val canClear: Boolean get() = content is HistoryContent.Entries && details == null && !clearing
    val interceptsBack: Boolean get() = confirmingClearCount != null || details != null
    override fun toString(): String   // bez gramów, treści i czasów (HISTUI-15)
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: CommandHistory,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState>

    fun onRetry()               // tylko w Unavailable
    fun onEntryClicked(id: Long)
    fun onDetailsClosed()
    fun onClearClicked()        // tylko gdy canClear
    fun onClearConfirmed()      // okno zamknięte, clearing = true, jedno clearAll()
    fun onClearDismissed()
    fun onMessageShown()
    fun onBack()                // okno → onClearDismissed; szczegóły → onDetailsClosed
}
```

Zbieranie: `retryTrigger: MutableStateFlow<Int>` → `flatMapLatest { flow { emit(null); emitAll(history.entries) } }` w `viewModelScope`; `null` → `Loading`. Wewnętrznie ViewModel trzyma ostatnią listę `HistoryEntry` i `selectedId: Long?`; `details` jest wyliczane z nich przy każdej emisji (`HistoryEntryMapper.details`), a brak wpisu o `selectedId` zeruje wybór. `onRetry` w stanie innym niż `Unavailable` nic nie robi. Czyszczenie: `viewModelScope.launch { val r = history.clearAll(); message = …; clearing = false }`; drugie `onClearConfirmed` przy `clearing = true` albo zamkniętym oknie jest ignorowane.

### Teksty (HISTUI-14)

```kotlin
package pl.twojanazwa.aapsremote.ui.history

@StringRes fun HistoryCommandLabel.textRes(): Int     // BG → commands_bg, PUMP → commands_pump (Spec 009)
@StringRes fun HistoryTimeLabel.textRes(): Int
@StringRes fun HistoryMessage.textRes(): Int
/** kind == null → history_kind_unrecognized, inaczej ResultKind.titleRes() (Spec 009). */
@StringRes fun HistoryResultView.titleRes(): Int
/** message == null → history_result_details_missing, inaczej ResultMessage.textRes() (Spec 009). */
@StringRes fun HistoryResultView.messageRes(): Int
```

### Ekran (bez testów automatycznych)

```kotlin
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel())
```

- `SecureWindowEffect(true)` na początku kompozycji (HISTUI-13, licznik ze Spec 009).
- `BackHandler(enabled = state.interceptsBack) { viewModel.onBack() }`.
- Treść: `details != null` → `HistoryDetailsView`; inaczej według `content`: `Loading` → `CircularProgressIndicator` i `history_loading`; `Empty` → `history_empty`; `Unavailable` → `history_unavailable` + `Button` `history_retry`; `Entries` → `HistoryList` (`LazyColumn`, `key = id`).
- Nagłówek `nav_history`, pod nim `history_limit_info` z `HistoryLimits.MAX_ENTRIES`; w pasku nagłówka `TextButton` `history_clear` (`enabled = state.canClear`).
- `HistoryClearDialog`: `AlertDialog` z `history_clear_title`, `history_clear_text` (N), `history_clear_confirm`, `action_cancel`.
- Treść SMS: `Text(replyText)` (zwykły `String`, bez `AnnotatedString` z linkami i bez `SelectionContainer`); `SnackbarHost` dla `message`, potem `onMessageShown()`.

### Wygląd (Material 3, propozycja)

- **Wiersz:** `ListItem` – `headlineContent` polecenie (`titleMedium`), `supportingContent` komunikat wyniku (2 linie, wielokropek), `overlineContent` czas wiersza, `leadingContent` ikona/kolor rodzaju jak karta wyniku w Spec 009 (sukces – `primary`, błąd – `error`, brak odpowiedzi i nieznany – `tertiary`, anulowane i nierozpoznany – `onSurfaceVariant`); `HorizontalDivider` między wierszami.
- **Szczegóły:** przewijana kolumna: `headlineSmall` `history_details_title` i polecenie, `Card` wyniku jak `ResultCard` ze Spec 009 (bez przycisku), tabela czasów (etykieta – wartość), „Treść SMS z AAPS:” z treścią, liczba nieoczekiwanych, `Button` `result_close`.

## Model danych i stany

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| – | utworzenie | `Loading` | zbieranie `entries` | HISTUI-01 |
| dowolny (zbieranie) | `Entries([])` | `Empty`, szczegóły zamknięte | – | HISTUI-01, HISTUI-08 |
| dowolny (zbieranie) | `Entries(≥ 1)` | `Entries(items)`; szczegóły zostają, gdy wpis jest na liście | – | HISTUI-02, HISTUI-07 |
| dowolny (zbieranie) | `Unavailable` | `Unavailable`, szczegóły i okno zamknięte | – | HISTUI-09 |
| `Unavailable` | `onRetry` | `Loading` | nowe zbieranie | HISTUI-09 |
| `Entries` | `onEntryClicked(id)` na liście | szczegóły `id` | – | HISTUI-07 |
| szczegóły | `onDetailsClosed` / `onBack` | lista | – | HISTUI-07, HISTUI-12 |
| `Entries`, `!clearing` | `onClearClicked` | okno (N = liczba wierszy) | – | HISTUI-10 |
| okno | `onClearDismissed` / `onBack` | lista | – | HISTUI-10, HISTUI-12 |
| okno | `onClearConfirmed` | `clearing = true`, okno zamknięte | `clearAll()` raz | HISTUI-10 |
| `clearing` | `Cleared` | `clearing = false`, komunikat `CLEARED` | – | HISTUI-11 |
| `clearing` | `Failed` | `clearing = false`, komunikat `CLEAR_FAILED` | – | HISTUI-11 |
| komunikat | `onMessageShown` | bez komunikatu | – | HISTUI-11 |

### Odwzorowanie wyniku (`HistoryEntryMapper.result`)

| `HistoryOutcome` | `kind` | `message` | `messageArg` |
| --- | --- | --- | --- |
| `Completed`, `BG` / `PUMP` | `SUCCESS` | `BG_REPLY` / `PUMP_REPLY` | – |
| `Completed`, inne polecenie | `SUCCESS` | `null` | – |
| `CarbsConfirmed`, `CARBS` z `g` | `SUCCESS` | `CARBS_CONFIRMED` | `g` |
| `CarbsConfirmed`, brak ilości / inne polecenie | `SUCCESS` | `null` | – |
| `Failed(r)` / `Failed(null)` | `FAILURE` | `r.toResultMessage()` / `null` | – |
| `TimedOut(p)` | `NO_RESPONSE` | `timedOutResultMessage(p)` | `minutesArg` (3) |
| `Unknown(r)` / `Unknown(null)` | `UNKNOWN` | `r.toResultMessage()` / `null` | `minutesArg` (6 dla `CONFIRMATION_TIMEOUT`) |
| `Cancelled` | `CANCELLED` | `CANCELLED` | – |
| `Unrecognized` | `null` | `null` | – |

`codeNotSent` = `command == CARBS && (outcome is Failed && reason != null && reason != WRONG_CODE || outcome is TimedOut || outcome is Cancelled)`; `unknownWarning` = `outcome is Unknown`. `HistoryCommandLabel`: `BG`, `PUMP`, `UNRECOGNIZED` wprost; `CARBS` z ilością → `CARBS`, bez ilości → `CARBS_NO_AMOUNT`.

### Teksty (`strings.xml`, HISTUI-14)

Blok `<!-- 010: historia -->`. Kolumna „Arg.”: `d` – `%1$d`. Używane ponownie (bez kopii): `nav_history`, `action_cancel` (Spec 008); `commands_bg`, `commands_pump`, `result_kind_*`, `result_*` (komunikaty), `result_reply_label`, `result_code_not_sent`, `result_unknown_warning`, `result_close`, `run_unexpected` (Spec 009). Usuwany: `placeholder_history_text` (aktualizacja `NavigationStringsTest`).

| Klucz | Treść | Arg. |
| --- | --- | --- |
| `history_limit_info` | Aplikacja przechowuje %1$d ostatnich poleceń. | d |
| `history_loading` | Wczytywanie historii… | |
| `history_empty` | Brak poleceń w historii. Wynik każdego polecenia pojawi się tutaj po jego zakończeniu. | |
| `history_unavailable` | Nie udało się wczytać historii. | |
| `history_retry` | Spróbuj ponownie | |
| `history_command_carbs` | CARBS %1$d g | d |
| `history_command_carbs_no_amount` | CARBS (ilość nieznana) | |
| `history_command_unrecognized` | Nieznane polecenie | |
| `history_kind_unrecognized` | Nierozpoznany wynik | |
| `history_result_details_missing` | Brak szczegółów wyniku (wpis z innej wersji aplikacji). | |
| `history_details_title` | Szczegóły polecenia | |
| `history_time_started` | Rozpoczęcie | |
| `history_time_command_sent` | Wysłanie polecenia | |
| `history_time_code_sent` | Wysłanie kodu | |
| `history_time_finished` | Zakończenie | |
| `history_time_none` | brak | |
| `history_clear` | Wyczyść historię | |
| `history_clear_title` | Wyczyścić historię? | |
| `history_clear_text` | Wszystkie wpisy (%1$d) zostaną usunięte. Tej operacji nie można cofnąć. | d |
| `history_clear_confirm` | Wyczyść | |
| `history_msg_cleared` | Historia wyczyszczona. | |
| `history_msg_clear_failed` | Nie udało się wyczyścić historii. Spróbuj ponownie. | |

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Wydzielenie `FailureReason`/`UnknownReason`/faza → `ResultMessage` z `ResultMapper` do `ui.commands.ResultMessages` | osobna tabela w `ui.history`; przeniesienie `ResultKind`/`ResultMessage` do nowego pakietu `ui.common` | jedna tabela i jedne teksty dla wyniku i historii (test równości HISTUI-04); przeniesienie typów zmieniałoby design 009 i rozbijało jego zadania |
| `HistoryEntryMapper.result` z własnym `when` po `HistoryOutcome` | konwersja `HistoryEntry` → `SessionResult` i `ResultMapper.map` | `HistoryOutcome` ma wartości `null` i `Unrecognized`, których `SessionOutcome` nie ma; konwersja wymagałaby zgadywania (DATA-09) |
| „Brak szczegółów wyniku” zamiast zgadywania przy `null`/nierozpoznanych | pominięcie wpisu; domyślny komunikat rodzaju | wpis nie znika z historii, a aplikacja nie twierdzi niczego, czego wpis nie mówi |
| `codeNotSent = false` przy `Failed(null)` | `true` jak dla innych `Failed` | przy nieznanym powodzie mógł to być `WRONG_CODE` (kod wysłany) – fałszywe „kod nie został wysłany” mogłoby skłonić do ponowienia |
| Szczegóły jako stan ViewModelu, nie trasa | trasa `history/{id}` | jak tryby zakładki Polecenia w Spec 009; zamknięcie przy zniknięciu wpisu w jednym miejscu; mniej zmian w `AppScaffold` |
| ViewModel w zakresie wpisu nawigacji | zakres `Activity` jak Polecenia | brak stanu, który musi przeżyć zmianę zakładki poza `saveState`/`restoreState`; strumień i tak daje aktualną listę |
| Formatowanie czasu w kompozycji z `ZoneId.systemDefault()` | strefa w ViewModelu / wstrzykiwany zegar | stan ViewModelu bez zależności od strefy; czysta funkcja z jawną strefą testowana w JVM |
| Stały wzorzec `dd.MM.yyyy HH:mm[:ss]`, `Locale.ROOT` | `ofLocalizedDateTime` | aplikacja jest tylko po polsku; wynik deterministyczny w testach JVM (P4) |
| Ponowienie odczytu tylko przyciskiem | automatyczne ponawianie | strumień po `Unavailable` jest zakończony (DATA-11); zasada „bez automatycznego ponawiania” w duchu AGENTS.md; brak pętli przy trwałym błędzie |
| Czyszczenie bez blokady w trakcie polecenia | blokada jak UI-15 | brak wpływu na sesję i bezpieczeństwo kodu (P2) |
| `FLAG_SECURE` przez `SecureWindowEffect(true)` na całej zakładce | tylko szczegóły | dane zdrowotne także w wierszach (ilość, komunikat); spójnie z CMDUI-23 (P3) |
| Treść jako zwykły `Text(String)` | `AnnotatedString` z linkami, zaznaczanie | nierozpoznana treść może pochodzić z dowolnego SMS z numeru AAPS – bez aktywnych linków |

## Mapowanie wymagań na testy

Wszystkie testy JVM w `app/src/test/java/pl/twojanazwa/aapsremote/`; identyfikator wymagania w nazwie każdej metody (np. `` `HISTUI-09 ponowienie po Unavailable zbiera strumien drugi raz` ``). Pomocnicze ze Spec 007/008/009: `ui/MainDispatcherExtension`, `data/HistoryFixtures` (jeśli pasuje), dane `parser/aaps-3.4.2.6-pl/`. Nowe: `ui/history/ControllableCommandHistory.kt` – atrapa `CommandHistory`: kolejka strumieni na kolejne zbierania (`MutableSharedFlow` albo `flowOf(Unavailable)`), licznik zbierań, `clearAll()` z kolejką wyników, bramką `CompletableDeferred` i licznikiem wywołań; `ui/history/HistoryTestEntries.kt` – fabryka wpisów.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| HISTUI-01 | `ui/history/HistoryViewModelStateTest` (Turbine); `ui/history/HistorySourcesTest` (pliki, klucz, `AppScaffold`); W1, W2 |
| HISTUI-02 | `HistoryViewModelStateTest`; W1, W3 |
| HISTUI-03 | `ui/history/HistoryEntryMapperTest` |
| HISTUI-04 | `HistoryEntryMapperTest` (tabela, dodatki); `ui/history/HistoryResultParityTest` (równość z `ResultMapper`); `ResultMapperTest` ze Spec 009 bez zmian; W7 |
| HISTUI-05 | `ui/history/HistoryTimeFormatTest`; `HistoryEntryMapperTest` (czas wiersza, cztery czasy); W8 |
| HISTUI-06 | `HistoryEntryMapperTest` (treść, liczba); `HistorySourcesTest` (brak HTML/linków) |
| HISTUI-07 | `ui/history/HistoryViewModelDetailsTest` |
| HISTUI-08 | `HistoryViewModelStateTest` |
| HISTUI-09 | `HistoryViewModelStateTest` (z `ControllableCommandHistory`, czas wirtualny) |
| HISTUI-10 | `ui/history/HistoryViewModelClearTest` (bramka, refleksja konstruktora); W4 |
| HISTUI-11 | `HistoryViewModelClearTest`; W4 |
| HISTUI-12 | `HistoryViewModelDetailsTest` (`onBack`, `interceptsBack`); W4 |
| HISTUI-13 | `HistorySourcesTest` (`SecureWindowEffect(true)`); W6 |
| HISTUI-14 | `ui/history/HistoryTextsTest` (odwzorowania, klucze, `%1$d`, brak kopii treści `result_*`); W1–W8 |
| HISTUI-15 | `ui/history/HistoryRedactionTest`; `UiNoLoggingTest` (Spec 008) |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami.

**Warunki wstępne:** Spec 009 – zadanie 2 (`ResultKind`, `ResultMessage`, `ResultMapper`) przed zadaniem 1; zadanie 7 (`RunTexts.kt`: `titleRes`/`textRes`, klucze `result_*`, `run_unexpected`) przed zadaniem 3; zadania 6 i 7 (licznik `FLAG_SECURE`, zmiany `AppScaffold.kt`) przed zadaniem 4. Spec 008 – zadanie 10 (`SecureWindowEffect`) przed zadaniem 4.

**Zależności i równoległość:** 1 → {2, 3} równolegle → 4.

**Pliki wspólne:** `ui/commands/ResultMapper.kt` (Spec 009) – tylko 1 (refaktoryzacja bez zmiany zachowania); `ui/navigation/AppScaffold.kt` – 4 (po 009: 6, 7); `strings.xml` – 3 dodaje blok `<!-- 010: historia -->`, 4 usuwa `placeholder_history_text`; `ui/navigation/NavigationStringsTest.kt` (Spec 008) – 4; `ui/history/HistoryTestEntries.kt` – tworzy 1, używają 2 i 3.

1. **Odwzorowania wpisu i czasu** – `ui/commands/ResultMessages.kt`, zmiana `ResultMapper.kt`; `ui/history/HistoryLabels.kt` (`HistoryCommandLabel`, `HistoryTimeLabel`, `HistoryMessage`), `HistoryResultView.kt`, `HistoryItem.kt` (z `HistoryDetails`), `HistoryEntryMapper.kt`, `HistoryTimeFormat.kt`; testy `HistoryTestEntries`, `HistoryEntryMapperTest`, `HistoryResultParityTest`, `HistoryTimeFormatTest`, `HistoryRedactionTest` (wiersz i szczegóły). Wymagania: HISTUI-03, HISTUI-04, HISTUI-05, HISTUI-06 (odwzorowanie), HISTUI-15 (część).
2. **`HistoryViewModel`** – `ui/history/HistoryUiState.kt` (z `HistoryContent`), `HistoryViewModel.kt`; testy `ControllableCommandHistory`, `HistoryViewModelStateTest`, `HistoryViewModelDetailsTest`, `HistoryViewModelClearTest`, rozszerzenie `HistoryRedactionTest` (stan). Wymagania: HISTUI-01 (stany), HISTUI-02, HISTUI-07 – HISTUI-12, HISTUI-15 (część). Zależy od 1; równolegle z 3.
3. **Teksty historii** – `ui/history/HistoryTexts.kt`, blok `<!-- 010: historia -->` w `strings.xml`; test `HistoryTextsTest`. Wymagania: HISTUI-14 (część: odwzorowania i klucze). Zależy od 1 i zadania 7 ze Spec 009; równolegle z 2.
4. **Ekran Historia** – `ui/history/HistoryScreen.kt`, `HistoryList.kt`, `HistoryDetailsView.kt`, `HistoryClearDialog.kt`; `AppScaffold.kt` (trasa `history` → `HistoryScreen()`); usunięcie `HistoryPlaceholderScreen.kt` i `placeholder_history_text`, aktualizacja `NavigationStringsTest`; test `HistorySourcesTest`. Wymagania: HISTUI-01 (ekran), HISTUI-03 – HISTUI-12 (widok), HISTUI-13, HISTUI-14 (część). PR opisuje W1–W8 tej spec, w tym punkty Spec 007: W3, W4, W5 i W6 (część historii). Zależy od 2, 3 oraz zadań 6, 7 ze Spec 009 i 10 ze Spec 008.
