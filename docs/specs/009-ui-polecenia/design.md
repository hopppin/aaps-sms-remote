# Design 009: UI – polecenia i przebieg polecenia

Dotyczy: [spec.md](spec.md)

## Przegląd

Druga część pakietu `ui` z [AGENTS.md](../../../AGENTS.md#architektura), budowana na szkielecie ze Spec 008 (`AppScaffold`, `SessionModule`, `SmsPermissionsViewModel`, `SecureWindowEffect`). Zakładka Polecenia ma dwa ViewModele tworzone w `AppScaffold` (poza `NavHost`, więc w zakresie `Activity`) i przekazywane do `CommandsScreen`:

| Klasa | Odpowiedzialność | Zależności |
| --- | --- | --- |
| `ui.commands.CommandLauncher` | sprawdzenia przed startem, odczyt sekretu, `SessionCoordinator.start` w zakresie aplikacji, `SessionHistoryRecorder.track` | `SessionCoordinator`, `AapsNumberSettingsStore`, `SmsPermissions`, `TotpSecretProvider`, `SessionHistoryRecorder`, `@CommandRunScope CoroutineScope` |
| `ui.commands.CommandsViewModel` | panel: gotowość, `BG`/`PUMP`, pole `CARBS`, okno „Na pewno”, podsumowanie z PIN-em, komunikaty | `CommandLauncher`, `SessionCoordinator`, `AapsNumberSettingsStore`, `TotpSecretStore`, `SmsPermissions` |
| `ui.commands.CommandRunViewModel` | przebieg i wynik aktywnej sesji, anulowanie, ekran włączony, Wstecz w trakcie polecenia | `SessionCoordinator` |
| `ui.commands.ProgressSteps`, `ResultMapper` | czyste odwzorowania faza → kroki, `SessionResult` → `ResultView` | – (`session`, `commands`) |

ViewModele są czystą logiką testowaną w JVM. Kompozycje (`CommandsScreen`, `CommandsPanel`, `CarbsSummary`, `RunScreen`, `ResultCard`, okna) są cienką warstwą: zbierają `StateFlow` (`collectAsStateWithLifecycle`), wołają metody ViewModeli i robią to, co wymaga `Activity`/okna (`FLAG_SECURE`, `keepScreenOn`, `finish()`, nawigacja do Ustawień, prośba o uprawnienia przez `SmsPermissionsSection` ze Spec 008). Podsumowanie i przebieg nie są trasami nawigacji – są trybami zakładki Polecenia (jak skaner w Spec 008), żeby PIN nie przechodził przez argumenty ani `SavedStateHandle`.

Układ plików:

```
ui/
  commands/CommandRunScope.kt, LaunchResult.kt, CommandLauncher.kt
  commands/Readiness.kt, CommandMessage.kt, CommandsUiState.kt, CommandsViewModel.kt
  commands/ProgressSteps.kt, ResultView.kt, ResultMapper.kt
  commands/RunUiState.kt, CommandRunViewModel.kt
  commands/CommandsScreen.kt, CommandsPanel.kt, CarbsSummary.kt, CommandDialogs.kt, CommandTexts.kt
  commands/RunScreen.kt, ResultCard.kt, RunTexts.kt
  commands/CommandsPlaceholderScreen.kt        (usuwany)
  navigation/AppScaffold.kt                    (zmiana: ViewModele zakładki, KeepScreenOnEffect, LocalSecureWindow)
  platform/CommandsModule.kt, KeepScreenOnEffect.kt, SecureWindowCounter.kt
  settings/secret/SecureWindowEffect.kt        (zmiana: licznik zamiast bezpośredniej flagi)
```

## Interfejsy

### Uruchamianie (CMDUI-12 – CMDUI-15, CMDUI-27)

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

/** Zakres korutyn aplikacji, w którym biegną sesje (CMDUI-13). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CommandRunScope

enum class LaunchRejection {
    START_FAILED, COMMAND_ACTIVE, NUMBER_LOADING, NUMBER_MISSING, SMS_PERMISSIONS_MISSING,
    SECRET_ABSENT, SECRET_INVALIDATED, SECRET_LOCKED, SECRET_UNAVAILABLE,
}

sealed interface LaunchResult {
    /** Sesja wystartowała; widok bierze ją z SessionCoordinator.activeSession (bez referencji w wyniku). */
    data object Started : LaunchResult
    data class Rejected(val reason: LaunchRejection) : LaunchResult
}

/** Bez stanu: pola wyłącznie z zależnościami (CMDUI-15). */
class CommandLauncher @Inject constructor(
    private val coordinator: SessionCoordinator,
    private val numbers: AapsNumberSettingsStore,
    private val permissions: SmsPermissions,
    private val secrets: TotpSecretProvider,
    private val recorder: SessionHistoryRecorder,
    @CommandRunScope private val scope: CoroutineScope,
) {
    /**
     * [pin] != null dokładnie dla command.definition.requiresCode. Każdy Rejected i anulowanie
     * korutyny = pin wyczyszczony. Nie rzuca poza CancellationException.
     */
    suspend fun launch(command: Command, pin: AapsPin?): LaunchResult
}
```

Algorytm `launch` (kolejność wiążąca, CMDUI-12 – CMDUI-14):

```kotlin
var started = false
try {
    if ((pin != null) != command.definition.requiresCode) return Rejected(START_FAILED)
    if (coordinator.activeSession.value != null) return Rejected(COMMAND_ACTIVE)
    when (numbers.state.value) {
        AapsNumberState.Loading -> return Rejected(NUMBER_LOADING)
        AapsNumberState.NotConfigured, AapsNumberState.LoadFailed -> return Rejected(NUMBER_MISSING)
        is AapsNumberState.Configured -> Unit
    }
    if (!permissions.canSend() || !permissions.canReceive()) return Rejected(SMS_PERMISSIONS_MISSING)
    val credentials = if (pin == null) null else when (val loaded = secrets.load()) {
        is LoadResult.Loaded -> CodeCredentials(pin, loaded.secret)
        LoadResult.Absent -> return Rejected(SECRET_ABSENT)
        LoadResult.Invalidated -> return Rejected(SECRET_INVALIDATED)
        LoadResult.Locked -> return Rejected(SECRET_LOCKED)
        LoadResult.Unavailable -> return Rejected(SECRET_UNAVAILABLE)
    }
    val result = try {
        coordinator.start(scope, command, credentials)
    } catch (e: IllegalArgumentException) {
        return Rejected(START_FAILED)
    }
    return when (result) {
        is StartResult.Started -> {
            started = true
            recorder.track(result.session.state)   // CMDUI-14: przed powrotem
            LaunchResult.Started
        }
        StartResult.Busy -> Rejected(COMMAND_ACTIVE)
    }
} finally {
    if (!started) pin?.clear()                     // idempotentne; także przy CancellationException
}
```

`when` po `LoadResult` i `AapsNumberState` jest wyczerpujący (bez `else`). `credentials` i `loaded` są zmiennymi lokalnymi.

```kotlin
package pl.twojanazwa.aapsremote.ui.platform

@Module
@InstallIn(SingletonComponent::class)
object CommandsModule {
    /** SupervisorJob: zakończenie jednej sesji wyjątkiem nie anuluje zakresu; nigdy nie anulowany przez ui. */
    @Provides
    @Singleton
    @CommandRunScope
    fun commandRunScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

### Panel i podsumowanie (CMDUI-01 – CMDUI-12, CMDUI-23, CMDUI-24)

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

enum class NumberReadiness { LOADING, CONFIGURED, NOT_CONFIGURED, LOAD_FAILED }

/** Z SecretState: UNKNOWN → UNKNOWN (CARBS dostępne). */
enum class SecretReadiness { UNKNOWN, PRESENT, ABSENT, INVALIDATED }

enum class CommandMessage(val opensSettings: Boolean) {
    COMMAND_ACTIVE(false), NUMBER_LOADING(false), NUMBER_MISSING(true), SMS_PERMISSIONS_MISSING(false),
    SECRET_ABSENT(true), SECRET_INVALIDATED(true), SECRET_LOCKED(false), SECRET_UNAVAILABLE(false),
    START_FAILED(false),
}

fun LaunchRejection.toMessage(): CommandMessage   // 1:1 po nazwie (CMDUI-12)

data class CommandsUiState(
    val number: NumberReadiness = NumberReadiness.LOADING,
    val smsGranted: Boolean = false,
    val secret: SecretReadiness = SecretReadiness.UNKNOWN,
    val commandActive: Boolean = false,
    val launching: Boolean = false,
    val carbsInput: String = "",
    val carbsError: CarbsAmountError? = null,
    /** Okno „Na pewno X g?” (CMDUI-09). */
    val confirmingLargeGrams: Int? = null,
    /** Podsumowanie otwarte dla tej ilości (CMDUI-10). */
    val summaryGrams: Int? = null,
    val pinText: String = "",
    val message: CommandMessage? = null,
) {
    val quickValues: List<Int> get() = CarbsLimits.QUICK_VALUES
    private val ready: Boolean get() =
        number == NumberReadiness.CONFIGURED && smsGranted && !commandActive && !launching
    val canSendNoCode: Boolean get() = ready
    val canStartCarbs: Boolean get() =
        ready && secret != SecretReadiness.ABSENT && secret != SecretReadiness.INVALIDATED
    val canSubmitPin: Boolean get() = summaryGrams != null && pinText.isNotEmpty() && !launching
    /** CMDUI-23. */
    val secureWindow: Boolean get() = summaryGrams != null
    /** CMDUI-24: Wstecz obsługuje ten ViewModel. */
    val interceptsBack: Boolean get() = confirmingLargeGrams != null || summaryGrams != null
    override fun toString(): String   // bez pinText, carbsInput, gramów (CMDUI-26)
}

@HiltViewModel
class CommandsViewModel @Inject constructor(
    private val launcher: CommandLauncher,
    private val coordinator: SessionCoordinator,
    private val numbers: AapsNumberSettingsStore,
    private val secrets: TotpSecretStore,
    private val permissions: SmsPermissions,
) : ViewModel() {
    val uiState: StateFlow<CommandsUiState>

    fun onBgClicked()
    fun onPumpClicked()
    fun onCarbsInputChanged(text: String)
    fun onQuickValueClicked(grams: Int)
    fun onCarbsNextClicked()
    fun onLargeAmountConfirmed()
    fun onLargeAmountDismissed()
    fun onPinChanged(text: String)
    fun onSendClicked()
    fun onSummaryDismissed()
    fun onBack()                 // okno „Na pewno” → zamknij; podsumowanie → onSummaryDismissed
    fun refreshPermissions()     // ON_RESUME zakładki i po wyniku prośby o uprawnienia
    fun onStop()                 // CMDUI-11
    fun onMessageShown()
}

@StringRes fun CommandMessage.textRes(): Int
@StringRes fun CarbsAmountError.textRes(): Int
@StringRes fun NumberReadiness.bannerRes(): Int?     // CONFIGURED → null
@StringRes fun SecretReadiness.bannerRes(): Int?     // UNKNOWN, PRESENT → null
```

### Przebieg i wynik (CMDUI-16 – CMDUI-22, CMDUI-24)

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

enum class ProgressStepId { SEND_COMMAND, AWAIT_REPLY, AWAIT_CODE_REQUEST, PREPARE_CODE, SEND_CODE, AWAIT_CONFIRMATION }

enum class StepStatus { DONE, CURRENT, PENDING }

data class ProgressStep(val id: ProgressStepId, val status: StepStatus)

object ProgressSteps {
    /** CMDUI-17. Faza niemożliwa dla polecenia albo FINISHED → wszystkie PENDING. */
    fun of(command: CommandId, phase: SessionPhase): List<ProgressStep>

    /** Argument minut kroku (AWAIT_REPLY, AWAIT_CODE_REQUEST → reply; AWAIT_CONFIRMATION → confirmation; inne → null). */
    fun minutesArg(id: ProgressStepId, timeouts: SessionTimeouts = SessionTimeouts()): Int?
}

enum class CancelMode { CANCEL, STOP_WAITING, NONE }

/** Fazy przed kodem → CANCEL; SENDING_CODE, AWAITING_CONFIRMATION → STOP_WAITING; FINISHED → NONE. */
fun SessionPhase.cancelMode(): CancelMode

enum class ResultKind { SUCCESS, FAILURE, NO_RESPONSE, UNKNOWN, CANCELLED }

enum class ResultMessage {
    BG_REPLY, PUMP_REPLY, CARBS_CONFIRMED,
    SEND_FAILED, UNRECOGNIZED_REPLY, AMOUNT_MISMATCH, UNEXPECTED_REPLY, WRONG_CODE,
    CODE_REQUEST_EXPIRED, CODE_PREPARATION_TIMEOUT, INTERNAL_ERROR,
    NO_RESPONSE, SEND_TIMEOUT,
    UNKNOWN_CONFIRMATION_TIMEOUT, UNKNOWN_CANCELLED_AFTER_CODE, UNKNOWN_CODE_SEND_FAILED,
    UNKNOWN_UNRECOGNIZED_REPLY, UNKNOWN_AMOUNT_MISMATCH, UNKNOWN_UNEXPECTED_REPLY, UNKNOWN_INTERNAL_ERROR,
    CANCELLED,
}

data class ResultView(
    val commandId: CommandId,
    /** Ilość z polecenia CARBS (nagłówek „Polecenie CARBS X g”), null dla BG/PUMP. */
    val commandGrams: Int?,
    val kind: ResultKind,
    val message: ResultMessage,
    /** Argument komunikatu: gramy (CARBS_CONFIRMED) albo minuty (NO_RESPONSE, SEND_TIMEOUT, UNKNOWN_CONFIRMATION_TIMEOUT). */
    val messageArg: Int?,
    val replyText: String?,
    val codeNotSent: Boolean,
    val unknownWarning: Boolean,
    val unexpectedCount: Int,
) {
    override fun toString(): String   // bez gramów i treści (CMDUI-26)
}

object ResultMapper {
    /** CMDUI-19, CMDUI-20. Wyczerpujący when po SessionOutcome, FailureReason, UnknownReason. */
    fun map(result: SessionResult, timeouts: SessionTimeouts = SessionTimeouts()): ResultView
}

sealed interface RunView {
    val commandId: CommandId
    val commandGrams: Int?

    data class Active(
        override val commandId: CommandId,
        override val commandGrams: Int?,
        val steps: List<ProgressStep>,
        val cancelMode: CancelMode,
        val unexpectedCount: Int,
    ) : RunView { override fun toString(): String }   // bez gramów

    data class Finished(val result: ResultView) : RunView {
        override val commandId get() = result.commandId
        override val commandGrams get() = result.commandGrams
    }
}

data class RunUiState(
    val view: RunView? = null,
    val commandActive: Boolean = false,
    val confirmingStopWaiting: Boolean = false,
    val confirmingExit: Boolean = false,
    /** Jednorazowe żądanie Activity.finish() (CMDUI-24); kasowane przez onExitHandled. */
    val exitRequested: Boolean = false,
) {
    /** CMDUI-22. */
    val keepScreenOn: Boolean get() = commandActive
    val interceptsBack: Boolean get() = view != null
}

@HiltViewModel
class CommandRunViewModel @Inject constructor(
    private val coordinator: SessionCoordinator,
) : ViewModel() {
    val uiState: StateFlow<RunUiState>

    fun onCancelClicked()            // CANCEL → activeSession.value?.cancel(); STOP_WAITING → okno
    fun onStopWaitingConfirmed()     // activeSession.value?.cancel()
    fun onStopWaitingDismissed()
    fun onResultDismissed()          // tylko przy Finished
    fun onBack()                     // Finished → onResultDismissed; Active → confirmingExit
    fun onExitConfirmed()            // confirmingExit = false, exitRequested = true
    fun onExitDismissed()
    fun onExitHandled()
}

@StringRes fun ResultKind.titleRes(): Int
@StringRes fun ResultMessage.textRes(): Int
@StringRes fun ProgressStepId.textRes(): Int
```

### Okno i ekran (CMDUI-22, CMDUI-23)

```kotlin
package pl.twojanazwa.aapsremote.ui.platform

/** Licznik żądań FLAG_SECURE w jednym Activity. onChange(true) przy 0 → 1, onChange(false) przy 1 → 0. */
class SecureWindowCounter(private val onChange: (Boolean) -> Unit) {
    fun acquire()
    fun release()     // przy 0 bez efektu
}

val LocalSecureWindow: ProvidableCompositionLocal<SecureWindowCounter>

/** view.keepScreenOn = on; przy usunięciu z kompozycji false. */
@Composable
fun KeepScreenOnEffect(on: Boolean)
```

`SecureWindowEffect(secure)` ze Spec 008 przechodzi na licznik: `DisposableEffect(secure) { if (secure) counter.acquire(); onDispose { if (secure) counter.release() } }`. `AppScaffold` tworzy `SecureWindowCounter { on -> if (on) window.addFlags(FLAG_SECURE) else window.clearFlags(FLAG_SECURE) }` w `remember` i podaje przez `CompositionLocalProvider`. Powód: w czasie animacji przejścia `NavHost` oba ekrany (np. Ustawienia ze skanerem i Polecenia z podsumowaniem) mogą być chwilowo w kompozycji; zdjęcie flagi przez jeden z nich nie może odsłonić drugiego. Zachowanie UI-23 się nie zmienia.

### Ekrany (bez testów automatycznych)

```kotlin
package pl.twojanazwa.aapsremote.ui.commands

@Composable
fun CommandsScreen(
    commandsViewModel: CommandsViewModel,
    runViewModel: CommandRunViewModel,
    onOpenSettings: () -> Unit,
    smsPermissionsViewModel: SmsPermissionsViewModel = hiltViewModel(),
)
```

- `LifecycleEventEffect(ON_RESUME)` → `commandsViewModel.refreshPermissions()`, `smsPermissionsViewModel.onResume()`; `LifecycleEventEffect(ON_STOP)` → `commandsViewModel.onStop()`.
- Kolejność treści: `run.view != null` → `RunScreen`; inaczej `summaryGrams != null` → `CarbsSummary`; inaczej `CommandsPanel`.
- `BackHandler(enabled = run.interceptsBack) { runViewModel.onBack() }`, `BackHandler(enabled = !run.interceptsBack && commands.interceptsBack) { commandsViewModel.onBack() }`.
- `SecureWindowEffect(commands.secureWindow)`; `exitRequested` → `LocalActivity.current?.finish()` i `onExitHandled()`.
- `SnackbarHost`: `message` → tekst; `opensSettings` → akcja „Ustawienia” wołająca `onOpenSettings`; po pokazaniu `onMessageShown()`.
- Pole ilości: `KeyboardOptions(keyboardType = KeyboardType.Number)`. Pole PIN-u: `singleLine = true`, `PasswordVisualTransformation()`, `KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false)`; wartość wyłącznie z `uiState.pinText`.

`AppScaffold` (zmiana zadania 6 i 7): `val commandsViewModel: CommandsViewModel = hiltViewModel()` i `val runViewModel: CommandRunViewModel = hiltViewModel()` wywołane przed `NavHost` (właściciel = `Activity`); trasa `COMMANDS` → `CommandsScreen(commandsViewModel, runViewModel, onOpenSettings = { navigateTo(SETTINGS) })` (ta sama funkcja nawigacji co pasek, UI-02); `KeepScreenOnEffect(runState.keepScreenOn)`.

### Wygląd (Material 3, propozycja)

- **Panel:** przewijana kolumna z odstępem 16 dp. Banery jako `Card` w kolorze `errorContainer` (numer, sekret) z tekstem i `TextButton` „Przejdź do Ustawień”; sekcja uprawnień SMS ze Spec 008 w `Card`. Przyciski „BG” i „PUMP” – `FilledTonalButton` pełnej szerokości, wysokość 72 dp, etykieta `titleLarge` i podpis `bodyMedium` („Glikemia”, „Stan pompy”). Sekcja „CARBS” w `OutlinedCard`: tytuł, `OutlinedTextField` z błędem w `supportingText`, rząd trzech `OutlinedButton` „10 g”, „20 g”, „30 g”, `Button` „Dalej” pełnej szerokości. W czasie uruchamiania – `LinearProgressIndicator` nad przyciskami.
- **Okno „Na pewno”:** `AlertDialog` z tytułem „Na pewno X g?”, tekstem i przyciskami „Tak, X g” (confirm) i „Nie” (dismiss).
- **Podsumowanie:** kolumna wyśrodkowana w pionie: `headlineSmall` „Wysłać CARBS X g do AAPS?”, wyjaśnienie `bodyMedium`, `OutlinedTextField` „PIN AAPS”, rząd przycisków `TextButton` „Anuluj” i `Button` „Wyślij”.
- **Przebieg:** nagłówek `headlineSmall` „Polecenie …”; lista kroków (`ListItem`: ikona – zaznaczenie dla wykonanego, `CircularProgressIndicator` 20 dp dla bieżącego, pusty okrąg dla oczekującego); tekst o pierwszym planie w `Card` `secondaryContainer`; liczba wiadomości nieoczekiwanych (gdy > 0); `OutlinedButton` „Anuluj polecenie” / „Przestań czekać” na dole.
- **Wynik:** `Card` w kolorze zależnym od rodzaju (sukces – `primaryContainer`, błąd – `errorContainer`, brak odpowiedzi i nieznany – `tertiaryContainer`, anulowane – `surfaceVariant`), nagłówek rodzaju, komunikat, dodatki, „Treść SMS z AAPS:” z treścią czcionką `bodyMedium` (bez skracania, przewijana), `Button` „Zamknij”.

## Model danych i stany

### `CommandsViewModel`

`uiState` = `combine(numbers.state, secrets.state, coordinator.activeSession.map { it != null }, local)` → `stateIn(viewModelScope, SharingStarted.Eagerly, CommandsUiState())`; `local` to `MutableStateFlow` z polami panelu, `smsGranted` i `message`. `init`: odczyt uprawnień, `viewModelScope.launch { secrets.refreshState() }`. Odwzorowania: `AapsNumberState` → `NumberReadiness` 1:1; `SecretState` → `SecretReadiness` 1:1.

Procedura `startCommand(command, pin)` (wspólna dla `BG`, `PUMP`, „Wyślij”): (1) `launching` → `pin?.clear()`, koniec; (2) `activeSession.value != null` → `pin?.clear()`, `message = COMMAND_ACTIVE`, koniec; (3) `launching = true`; (4) `launcher.launch(command, pin)`; (5) `launching = false`; `Started` → `summaryGrams = null`, `carbsInput = ""`, `carbsError = null`; `Rejected(r)` → `summaryGrams = null`, `message = r.toMessage()`.

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `canSendNoCode` | `onBgClicked` / `onPumpClicked` | – | `startCommand(Bg/Pump, null)` | CMDUI-06 |
| nie `canSendNoCode` | `onBgClicked` / `onPumpClicked` | polecenie w toku → `message = COMMAND_ACTIVE`; inaczej bez zmian | brak wywołania launchera | CMDUI-05 |
| dowolny | `onCarbsInputChanged(t)` | `carbsInput = t`, `carbsError = null` | – | CMDUI-07 |
| `canStartCarbs` | `onCarbsNextClicked`, `parse` = `Invalid(e)` | `carbsError = e` | – | CMDUI-07 |
| `canStartCarbs` | `onCarbsNextClicked`, `Valid(c)`, `c.requiresExtraConfirmation` | `confirmingLargeGrams = c.grams` | – | CMDUI-09 |
| `canStartCarbs` | `onCarbsNextClicked`, `Valid(c)`, bez dodatkowego potwierdzenia | `summaryGrams = c.grams`, `pinText = ""` | – | CMDUI-10 |
| `canStartCarbs` | `onQuickValueClicked(g)` | `carbsInput = "g"`, `carbsError = null`, `summaryGrams = g`, `pinText = ""` | – | CMDUI-08 |
| `confirmingLargeGrams = g` | `onLargeAmountConfirmed` | `confirmingLargeGrams = null`, `summaryGrams = g`, `pinText = ""` | – | CMDUI-09 |
| `confirmingLargeGrams ≠ null` | `onLargeAmountDismissed` / `onBack` | `confirmingLargeGrams = null` | – | CMDUI-09, CMDUI-24 |
| `summaryGrams ≠ null` | `onPinChanged(t)`, `t` tylko cyfry i `t.length ≤ 12` | `pinText = t` | – | CMDUI-10 |
| `summaryGrams ≠ null` | `onPinChanged(t)`, inaczej | bez zmian | – | CMDUI-10 |
| `canSubmitPin` | `onSendClicked` | `chars = pinText.toCharArray()`; `pinText = ""` | `pin = AapsPin.fromChars(chars)` (zeruje `chars`); `startCommand(Carbs.of(g), pin)` | CMDUI-11, CMDUI-12 |
| `summaryGrams ≠ null` | `onSummaryDismissed` / `onBack` | `summaryGrams = null`, `pinText = ""` | – | CMDUI-10, CMDUI-24 |
| dowolny | `onStop` | `pinText = ""`, `summaryGrams = null`, `confirmingLargeGrams = null` | – | CMDUI-11 |
| dowolny | `refreshPermissions` | `smsGranted = canSend() && canReceive()` | – | CMDUI-03 |

Stała `PIN_MAX_LENGTH = 12` (decyzja użytkownika, P2 w spec). `Command.Carbs.of(summaryGrams)` przy „Wyślij” zawsze daje `Valid` (ilość pochodzi z wcześniejszego `parse`/`of`); `Invalid` → `summaryGrams = null` bez startu (gałąź obronna, bez wymagania).

### `CommandRunViewModel`

`init`: `viewModelScope.launch { coordinator.activeSession.collect { session -> if (session != null) observe(session) } }`. `observe(session)` zbiera `session.state` do fazy `FINISHED` włącznie (`transformWhile`); w każdej fazie przed `FINISHED` ustawia `view = Active(command, ProgressSteps.of(...), phase.cancelMode(), unexpectedMessages.size)`, a przy `FINISHED` – `view = Finished(ResultMapper.map(state.result!!))`, `confirmingStopWaiting = false`, `confirmingExit = false`. `session` jest tylko parametrem tej funkcji (ramka korutyny), nie polem; po powrocie z `observe` referencja znika (CMDUI-21). Ponieważ `activeSession` zmienia się na `null` dopiero po opublikowaniu `FINISHED` (SESSION-22), a `StateFlow` sesji zachowuje ostatnią wartość, `observe` zawsze widzi `FINISHED`. Drugi kolektor: `commandActive = activeSession.value != null`.

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| dowolny | nowa aktywna sesja | `view = Active(…)` | `observe` | CMDUI-16 |
| `Active` | zmiana fazy / nieoczekiwanej wiadomości | `view = Active(…)` z nowymi krokami i liczbą | – | CMDUI-17, CMDUI-20 |
| `Active` | `FINISHED` | `view = Finished(map(result))`, okna zamknięte | – | CMDUI-19, CMDUI-21 |
| `Active`, `cancelMode = CANCEL` | `onCancelClicked` | – | `activeSession.value?.cancel()` | CMDUI-18 |
| `Active`, `cancelMode = STOP_WAITING` | `onCancelClicked` | `confirmingStopWaiting = true` | – | CMDUI-18 |
| `confirmingStopWaiting` | `onStopWaitingConfirmed` | `confirmingStopWaiting = false` | `activeSession.value?.cancel()` | CMDUI-18 |
| `confirmingStopWaiting` | `onStopWaitingDismissed` | `confirmingStopWaiting = false` | – | CMDUI-18 |
| `Finished` | `onResultDismissed` / `onBack` | `view = null` | – | CMDUI-21, CMDUI-24 |
| `Active` | `onResultDismissed` | bez zmian | – | CMDUI-21 |
| `Active` | `onBack` | `confirmingExit = true` | – | CMDUI-24 |
| `confirmingExit` | `onExitConfirmed` | `confirmingExit = false`, `exitRequested = true` | – | CMDUI-24 |
| `confirmingExit` | `onExitDismissed` | `confirmingExit = false` | – | CMDUI-24 |
| `exitRequested` | `onExitHandled` | `exitRequested = false` | – | CMDUI-24 |

### Odwzorowanie wyniku (`ResultMapper`)

| `SessionOutcome` | `kind` | `message` | `messageArg` | `replyText` |
| --- | --- | --- | --- | --- |
| `Completed` (`BG` / `PUMP`) | `SUCCESS` | `BG_REPLY` / `PUMP_REPLY` | – | `text` |
| `CarbsConfirmed(g, text)` | `SUCCESS` | `CARBS_CONFIRMED` | `g` | `text` |
| `Failed(r, text)` | `FAILURE` | `FailureReason` → wartość o tej samej nazwie | – | `text` |
| `TimedOut(SENDING_COMMAND)` | `NO_RESPONSE` | `SEND_TIMEOUT` | `reply` w min | – |
| `TimedOut(inna faza)` | `NO_RESPONSE` | `NO_RESPONSE` | `reply` w min | – |
| `Unknown(r, text)` | `UNKNOWN` | `UNKNOWN_<r>` | `confirmation` w min dla `CONFIRMATION_TIMEOUT` | `text` |
| `Cancelled` | `CANCELLED` | `CANCELLED` | – | – |

`codeNotSent` = `command.definition.requiresCode && (outcome is Failed && reason != WRONG_CODE || outcome is TimedOut || outcome is Cancelled)`; `unknownWarning` = `outcome is Unknown`; `unexpectedCount` = `result.unexpectedMessages.size`; `commandGrams` = `(command as? Command.Carbs)?.grams`. Minuty: `timeouts.reply.inWholeMinutes.toInt()` (3), `timeouts.confirmation.inWholeMinutes.toInt()` (6).

### Teksty (`strings.xml`, CMDUI-25)

Klucze są wiążące; treści – jak w spec. Każde zadanie dodaje swój blok z komentarzem `<!-- 009: <obszar> -->`. Kolumna „Arg.”: `d` – jeden argument `%1$d`, `dd` – dwa (`%1$d`, `%2$d`). Używane ponownie ze Spec 008 (bez kopii): `msg_command_active` (`COMMAND_ACTIVE`), `sms_denied` (`SMS_PERMISSIONS_MISSING`), `secret_msg_locked` (`SECRET_LOCKED`), `action_cancel`.

| Klucz | Treść | Arg. |
| --- | --- | --- |
| `commands_bg` | BG | |
| `commands_bg_hint` | Glikemia | |
| `commands_pump` | PUMP | |
| `commands_pump_hint` | Stan pompy | |
| `commands_carbs_title` | CARBS – węglowodany | |
| `commands_carbs_label` | Węglowodany (g) | |
| `commands_carbs_quick` | %1$d g | d |
| `commands_carbs_next` | Dalej | |
| `commands_carbs_error_empty` | Podaj ilość węglowodanów (%1$d–%2$d g). | dd |
| `commands_carbs_error_invalid_characters` | Podaj liczbę całkowitą – tylko cyfry. | |
| `commands_carbs_error_below_min` | Najmniejsza ilość to %1$d g. | d |
| `commands_carbs_error_above_max` | Największa ilość to %1$d g. | d |
| `commands_large_title` | Na pewno %1$d g? | d |
| `commands_large_text` | To więcej niż %1$d g. Sprawdź ilość, zanim wpiszesz PIN. | d |
| `commands_large_confirm` | Tak, %1$d g | d |
| `commands_large_dismiss` | Nie | |
| `commands_summary_title` | Wysłać CARBS %1$d g do AAPS? | d |
| `commands_summary_text` | Aplikacja wyśle CARBS %1$d g, a kod z PIN-em odeśle dopiero, gdy AAPS poprosi o kod dla %1$d g. | d |
| `commands_pin_label` | PIN AAPS | |
| `commands_send` | Wyślij | |
| `commands_open_settings` | Przejdź do Ustawień | |
| `commands_banner_number_loading` | Wczytywanie ustawień… | |
| `commands_banner_number_not_configured` | Nie ustawiono numeru AAPS. Bez niego aplikacja nie wyśle poleceń. | |
| `commands_banner_number_load_failed` | Nie udało się wczytać numeru AAPS. Ustaw go ponownie w Ustawieniach. | |
| `commands_banner_secret_absent` | CARBS wymaga sekretu TOTP. Zeskanuj kod QR z AAPS w Ustawieniach. | |
| `commands_banner_secret_invalidated` | Sekret TOTP przestał działać (np. po zmianie blokady ekranu). Zeskanuj ponownie kod QR z AAPS w Ustawieniach. | |
| `commands_msg_number_loading` | Trwa wczytywanie ustawień. Spróbuj za chwilę. | |
| `commands_msg_number_missing` | Ustaw numer telefonu AAPS w Ustawieniach. | |
| `commands_msg_secret_absent` | Brak sekretu TOTP. Zeskanuj kod QR z AAPS w Ustawieniach. | |
| `commands_msg_secret_invalidated` | Sekret TOTP przestał działać. Zeskanuj ponownie kod QR z AAPS w Ustawieniach. | |
| `commands_msg_secret_unavailable` | Nie udało się odczytać sekretu TOTP. Spróbuj ponownie. | |
| `commands_msg_start_failed` | Nie udało się uruchomić polecenia. Spróbuj ponownie. | |
| `commands_msg_action_settings` | Ustawienia | |
| `run_title_bg` | Polecenie BG | |
| `run_title_pump` | Polecenie PUMP | |
| `run_title_carbs` | Polecenie CARBS %1$d g | d |
| `run_step_send_command` | Wysyłanie SMS z poleceniem | |
| `run_step_await_reply` | Czekanie na odpowiedź AAPS (do %1$d min) | d |
| `run_step_await_code_request` | Czekanie na prośbę AAPS o kod (do %1$d min) | d |
| `run_step_prepare_code` | Przygotowanie kodu | |
| `run_step_send_code` | Wysyłanie kodu | |
| `run_step_await_confirmation` | Czekanie na potwierdzenie AAPS (do %1$d min) | d |
| `run_foreground_hint` | Nie wychodź z aplikacji i nie blokuj telefonu do końca polecenia. Ekran pozostanie włączony. | |
| `run_unexpected` | Wiadomości od AAPS niezwiązane z tym poleceniem: %1$d (np. potwierdzenie polecenia wysłanego z innego telefonu). | d |
| `run_cancel` | Anuluj polecenie | |
| `run_stop_waiting` | Przestań czekać | |
| `run_stop_title` | Przestać czekać? | |
| `run_stop_text` | Kod został już wysłany. AAPS może wykonać polecenie mimo to. Wynik będzie nieznany – sprawdź w AAPS. | |
| `run_stop_dismiss` | Czekaj dalej | |
| `run_exit_title` | Trwa polecenie | |
| `run_exit_text` | Wyjście z aplikacji może przerwać odbiór odpowiedzi AAPS. Polecenie nie zostanie anulowane. | |
| `run_exit_stay` | Zostań | |
| `run_exit_leave` | Wyjdź | |
| `result_kind_success` | Gotowe | |
| `result_kind_failure` | Błąd | |
| `result_kind_no_response` | AAPS nie odpowiada | |
| `result_kind_unknown` | Nieznany – sprawdź w AAPS | |
| `result_kind_cancelled` | Anulowano | |
| `result_bg_reply` | Odpowiedź AAPS na BG | |
| `result_pump_reply` | Odpowiedź AAPS na PUMP | |
| `result_carbs_confirmed` | AAPS potwierdził wprowadzenie %1$d g | d |
| `result_send_failed` | Nie udało się wysłać SMS. Sprawdź zasięg, kartę SIM i uprawnienia SMS. | |
| `result_unrecognized_reply` | Nierozpoznana odpowiedź AAPS. | |
| `result_amount_mismatch` | AAPS podał inną ilość niż wysłana (np. obciętą przez limit w AAPS). | |
| `result_unexpected_reply` | Odpowiedź AAPS nie pasuje do etapu polecenia. | |
| `result_wrong_code` | AAPS odrzucił kod. Sprawdź PIN i to, czy zegar telefonu ustawia się automatycznie. | |
| `result_code_request_expired` | Prośba AAPS o kod jest zbyt stara. | |
| `result_code_preparation_timeout` | Nie udało się przygotować kodu na czas. Sprawdź zegar telefonu. | |
| `result_internal_error` | Błąd wewnętrzny aplikacji. | |
| `result_no_response` | Brak odpowiedzi AAPS w ciągu %1$d min. | d |
| `result_send_timeout` | Telefon nie potwierdził wysłania SMS w ciągu %1$d min. | d |
| `result_unknown_confirmation_timeout` | Kod wysłany, ale AAPS nie potwierdził w ciągu %1$d min. | d |
| `result_unknown_cancelled_after_code` | Przestano czekać po wysłaniu kodu. | |
| `result_unknown_code_send_failed` | Wysyłanie kodu zgłosiło błąd, ale SMS mógł dotrzeć do AAPS. | |
| `result_unknown_unrecognized_reply` | Po wysłaniu kodu przyszła nierozpoznana odpowiedź AAPS (np. o kolejce pompy). | |
| `result_unknown_amount_mismatch` | Po wysłaniu kodu AAPS podał inną ilość niż wysłana. | |
| `result_unknown_unexpected_reply` | Po wysłaniu kodu AAPS ponownie poprosił o kod. Kod nie został wysłany drugi raz. | |
| `result_unknown_internal_error` | Błąd wewnętrzny aplikacji po wysłaniu kodu. | |
| `result_cancelled` | Polecenie anulowane. | |
| `result_reply_label` | Treść SMS z AAPS: | |
| `result_code_not_sent` | Kod nie został wysłany. | |
| `result_unknown_warning` | Nie ponawiaj polecenia, zanim nie sprawdzisz w AAPS, czy zostało wykonane. | |
| `result_close` | Zamknij | |

Przycisk potwierdzenia w oknie „Przestać czekać?” używa `run_stop_waiting`. Argumenty: `commands_carbs_error_empty` – `CarbsLimits.MIN_GRAMS`, `MAX_GRAMS`; `commands_large_text` – `CarbsLimits.EXTRA_CONFIRMATION_ABOVE_GRAMS`; kroki i wyniki z minutami – `ProgressSteps.minutesArg` / `ResultView.messageArg`. Klucz `placeholder_commands_text` jest usuwany (zadanie 6); `NavigationStringsTest` ze Spec 008 przestaje go wymagać.

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| ViewModele zakładki Polecenia w zakresie `Activity` (tworzone w `AppScaffold`) | ViewModel wpisu nawigacji | przebieg i tekst pola przeżywają zmianę zakładki niezależnie od `saveState`/`restoreState`; `keepScreenOn` zależy od stanu sesji na każdej zakładce (CMDUI-22) |
| Dwa ViewModele: panel (`CommandsViewModel`) i przebieg (`CommandRunViewModel`) | jeden ViewModel | rozłączne odpowiedzialności i testy, zadania równoległe; przebieg zależy tylko od koordynatora i pokazuje też sesję sprzed utworzenia ViewModelu |
| `CommandLauncher` jako osobna klasa bez stanu | logika startu w ViewModelu | sprawdzenia, odczyt sekretu, czyszczenie PIN-u i `track` testowalne bez ViewModelu; jedno miejsce z sekretem w pamięci (zmienna lokalna) – łatwy test retencji (CMDUI-15) |
| Zakres `@CommandRunScope` (`SupervisorJob` + `Dispatchers.Default`) w `ui.platform.CommandsModule` | `viewModelScope`; `@DataScope`; zakres w `session.platform` | sesja musi przeżyć ViewModel i `Activity` (CMDUI-13); `@DataScope` należy do `data` (IO, inne przeznaczenie); `session` nie zmieniamy (decyzja zakresu). Sesja nie blokuje wątków, więc `Default` |
| `LaunchResult.Started` bez referencji do sesji | `Started(session)` | ViewModel nie ma okazji zatrzymać sesji (z `CodeCredentials`) w polu; widok bierze sesję z `activeSession` (jedno źródło prawdy) |
| Kolejność: sprawdzenia przed `load()` | odczyt sekretu na początku | sekret jest odszyfrowywany tylko, gdy start jest możliwy – krócej w pamięci, brak zbędnych operacji Keystore (SECRET-14) |
| `pin.clear()` w `finally` launchera na każdej ścieżce bez startu | polegać tylko na koordynatorze | koordynator nie widzi PIN-u przy odrzuceniu przed startem i przy anulowaniu w trakcie `load()`; czyszczenie jest idempotentne |
| PIN jako tekst w stanie ViewModelu, czyszczony przy „Wyślij” przed `load()` i przy `ON_STOP` | `rememberSaveable`; `TextFieldState` w kompozycji | `rememberSaveable` trafia do stanu instancji; stan ViewModelu daje testy JVM czyszczenia (jak UI-14). `String` nie da się wyzerować – ograniczenie opisane |
| Podsumowanie i przebieg jako tryby zakładki, nie trasy | trasy `commands/summary/{g}`, `commands/run` | argumenty tras i `SavedStateHandle` trafiają do stanu instancji; PIN i stan sesji zostają w pamięci ViewModelu |
| Odwzorowanie wyniku jako czysta funkcja z wyczerpującym `when` | odwzorowanie w kompozycji | test JVM każdego `FailureReason`/`UnknownReason`; nowa wartość w `session` wymusza decyzję w `ui` |
| Anulowanie przez `activeSession.value?.cancel()` | referencja sesji w ViewModelu | brak pola z sesją (retencja `CodeCredentials`, CMDUI-21); anulowanie po zakończeniu nic nie robi (SESSION-17) |
| Okno potwierdzenia tylko przy anulowaniu po kodzie | okno zawsze / nigdy | przed kodem anulowanie jest bezpieczne (nic nie wysłano poza poleceniem); po kodzie skutek jest nieodwracalny i wynik „nieznany” (SESSION-17) |
| Ekran włączony na poziomie `Activity`, gdy `activeSession != null` | tylko na ekranie przebiegu | odbiór wymaga włączonego ekranu (Spec 005, P2) także wtedy, gdy użytkownik zajrzy do Ustawień |
| Licznik `FLAG_SECURE` zamiast bezpośredniego `add/clearFlags` | dwa niezależne efekty | przy animacji przejścia `NavHost` dwa ekrany chronione mogą być w kompozycji naraz; zdjęcie flagi przez jeden nie może odsłonić drugiego |
| Banery gotowości + nieaktywne przyciski + sprawdzenie w chwili startu | tylko komunikat po dotknięciu | użytkownik widzi przyczynę przed dotknięciem; sprawdzenie w launcherze zamyka wyścigi (stan ekranu jest asynchroniczny) |
| `UNKNOWN` sekretu nie blokuje `CARBS` | blokada do poznania stanu | jak decyzja w Spec 008: `refreshState()` przy błędzie zostawia `UNKNOWN`; ostateczna decyzja przy `load()` |
| Teksty minut z `SessionTimeouts()` jako argumenty | stałe „3 min” w tekstach | jedno źródło wartości limitów (SESSION-01) |

## Mapowanie wymagań na testy

Wszystkie testy JVM w `app/src/test/java/pl/twojanazwa/aapsremote/`. Identyfikator wymagania jest w nazwie każdej metody testowej (np. `` `CMDUI-12 brak sekretu daje SECRET_ABSENT i czysci PIN` ``). Pomocnicze ze Spec 004/005/007/008: `session/FakeSmsSender`, `session/FakeIncomingSmsSource`, `sms/FakeSmsPermissions`, `data/FakeCommandHistory`, `ui/MainDispatcherExtension`, `ui/SessionDoubles`, `ui/settings/number/FakeAapsNumberSettingsStore`, `ui/settings/secret/FakeTotpSecretStore` (rozszerzana o kolejkę wyników `load()` i bramkę `CompletableDeferred`). Nowe: `ui/commands/CommandTestFixtures.kt` (koordynator z atrapami i zegarem `T0`, rejestrator z `FakeCommandHistory`, launcher).

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| CMDUI-01 | `ui/commands/CommandsSourcesTest` – pliki i `strings.xml`; `CommandsViewModelReadinessTest` (stan początkowy); W1 |
| CMDUI-02 | `ui/commands/CommandsViewModelReadinessTest` – ViewModel (Turbine); W2 |
| CMDUI-03 | `CommandsViewModelReadinessTest` (uprawnienia, liczba odczytów); W3 |
| CMDUI-04 | `CommandsViewModelReadinessTest` (sekret, `refreshState`); W2 |
| CMDUI-05 | `ui/commands/CommandsViewModelLockTest` – ViewModel, bramki wysyłania i `load()` |
| CMDUI-06 | `ui/commands/CommandsViewModelNoCodeTest` – ViewModel + launcher + koordynator z atrapami; W4 |
| CMDUI-07 | `ui/commands/CommandsViewModelCarbsInputTest` |
| CMDUI-08 | `CommandsViewModelCarbsInputTest` (szybkie wartości) |
| CMDUI-09 | `ui/commands/CommandsViewModelLargeAmountTest` |
| CMDUI-10 | `ui/commands/CommandsViewModelSummaryTest`; W6 |
| CMDUI-11 | `ui/commands/CommandsViewModelPinRetentionTest` – bramka `load()`, `onStop`, refleksja; `CommandsSourcesTest` (bez `rememberSaveable`/`SavedStateHandle`); W6 |
| CMDUI-12 | `ui/commands/CommandLauncherChecksTest` – launcher; `CommandsViewModelSummaryTest` (zamknięcie podsumowania, komunikat); `ui/commands/CommandMessageTest` (odwzorowanie powodów) |
| CMDUI-13 | `ui/commands/CommandLauncherStartTest` – launcher (CARBS 1 z kodem `0504714321`, `Busy`, niepoprawny zestaw); `CommandsViewModelNoCodeTest` (czyszczenie ViewModelu); `ui/platform/CommandsModuleTest` |
| CMDUI-14 | `ui/commands/CommandLauncherHistoryTest` – MockK rejestratora i prawdziwy rejestrator z `FakeCommandHistory` |
| CMDUI-15 | `ui/commands/CommandLauncherRetentionTest` – refleksja; `CommandsViewModelPinRetentionTest` |
| CMDUI-16 | `ui/commands/CommandRunViewModelObserveTest`; W8 |
| CMDUI-17 | `ui/commands/ProgressStepsTest` – czysta funkcja (wszystkie fazy × 3 polecenia); W5 |
| CMDUI-18 | `ui/commands/CommandRunViewModelCancelTest`; W8 |
| CMDUI-19 | `ui/commands/ResultMapperTest` – wszystkie wiersze tabeli; W4, W5, W12 |
| CMDUI-20 | `ResultMapperTest` (treść), `CommandRunViewModelObserveTest` (liczba nieoczekiwanych w przebiegu) |
| CMDUI-21 | `ui/commands/CommandRunViewModelResultTest` – zamknięcie, refleksja; W8 |
| CMDUI-22 | `ui/commands/CommandRunViewModelKeepScreenOnTest`; W9 |
| CMDUI-23 | `CommandsViewModelSummaryTest` (znacznik), `ui/platform/SecureWindowCounterTest`; W10 |
| CMDUI-24 | `CommandsViewModelSummaryTest` (`onBack`), `ui/commands/CommandRunViewModelBackTest`; W11 |
| CMDUI-25 | `ui/commands/CommandTextsTest`, `ui/commands/RunTextsTest` – odwzorowania i klucze w `strings.xml` |
| CMDUI-26 | `UiNoLoggingTest` (Spec 008, obejmuje `ui/`), `ui/commands/CommandsRedactionTest`, `ui/commands/ResultRedactionTest` – `toString()` |
| CMDUI-27 | `CommandsModuleTest` – refleksja; `assembleDebug` |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Wymagania częściowe oznaczono „(część)”.

**Warunek wstępny:** zaimplementowane zadania 1, 2, 3, 8 i 10 z [design 008](../008-ui-ustawienia/design.md#podział-na-zadania) (fundament, nawigacja, ViewModel i sekcja uprawnień SMS, `SecureWindowEffect`). Zadania 1–5 tej spec wymagają tylko zadania 1 ze Spec 008 (`SessionModule`, `MainDispatcherExtension`, `SessionDoubles`) i atrap z zadań 4–5 ze Spec 008.

**Zależności i równoległość:** {1, 2} równolegle; 3 ← 1; 4 ← 3; 5 ← 2 (równolegle z 3 i 4); 6 ← 4 (+ Spec 008: 2, 3, 8, 10); 7 ← 5, 6.

**Pliki wspólne:** `ui/commands/CommandsViewModel.kt` i `CommandsUiState.kt` – 3, potem 4; `ui/navigation/AppScaffold.kt` – 6, potem 7; `strings.xml` – 6 i 7 (każde dopisuje własny blok `<!-- 009: … -->`, bez zmian cudzych kluczy; 6 usuwa `placeholder_commands_text`); `ui/settings/secret/SecureWindowEffect.kt` (Spec 008) – tylko 6; `ui/settings/secret/FakeTotpSecretStore.kt` (test, Spec 008) – rozszerza 1, używają 3 i 4; `ui/commands/CommandTestFixtures.kt` – tworzy 1, rozszerzają 3–5.

1. **Uruchamianie: `CommandLauncher` i zakres aplikacji** – `ui/commands/CommandRunScope.kt`, `LaunchResult.kt`, `CommandLauncher.kt`, `ui/platform/CommandsModule.kt`; testy `CommandTestFixtures`, `CommandLauncherChecksTest`, `CommandLauncherStartTest`, `CommandLauncherHistoryTest`, `CommandLauncherRetentionTest`, `CommandsModuleTest`. Wymagania: CMDUI-12 (launcher), CMDUI-13, CMDUI-14, CMDUI-15 (launcher), CMDUI-27. Równolegle z 2.
2. **Odwzorowania przebiegu i wyniku** – `ui/commands/ProgressSteps.kt` (z `CancelMode`), `ResultView.kt`, `ResultMapper.kt`; testy `ProgressStepsTest`, `ResultMapperTest`, `ResultRedactionTest`. Wymagania: CMDUI-17, CMDUI-18 (`cancelMode`), CMDUI-19, CMDUI-20 (treść), CMDUI-26 (`toString` wyniku). Bez zależności; równolegle z 1.
3. **`CommandsViewModel`: gotowość, `BG`/`PUMP`, blokady, komunikaty** – `ui/commands/Readiness.kt`, `CommandMessage.kt` (z `toMessage`), `CommandsUiState.kt`, `CommandsViewModel.kt` (bez `CARBS`); testy `CommandsViewModelReadinessTest`, `CommandsViewModelLockTest` (`BG`/`PUMP`), `CommandsViewModelNoCodeTest`, `CommandMessageTest`. Wymagania: CMDUI-02, CMDUI-03, CMDUI-04, CMDUI-05 (część), CMDUI-06, CMDUI-12 (komunikaty), CMDUI-13 (czyszczenie ViewModelu). Zależy od 1.
4. **`CommandsViewModel`: `CARBS`, podsumowanie i PIN** – `CommandsViewModel.kt`, `CommandsUiState.kt`; testy `CommandsViewModelCarbsInputTest`, `CommandsViewModelLargeAmountTest`, `CommandsViewModelSummaryTest`, `CommandsViewModelPinRetentionTest`, `CommandsViewModelLockTest` („Wyślij” w trakcie uruchamiania), `CommandsRedactionTest`. Wymagania: CMDUI-05 (część), CMDUI-07 – CMDUI-11, CMDUI-12 (ViewModel), CMDUI-15 (ViewModel), CMDUI-23 (znacznik), CMDUI-24 (panel), CMDUI-26 (`toString` panelu). Zależy od 3.
5. **`CommandRunViewModel`: przebieg, anulowanie, wynik** – `ui/commands/RunUiState.kt`, `CommandRunViewModel.kt`; testy `CommandRunViewModelObserveTest`, `…CancelTest`, `…ResultTest`, `…KeepScreenOnTest`, `…BackTest`. Wymagania: CMDUI-16, CMDUI-18, CMDUI-20 (liczba w przebiegu), CMDUI-21, CMDUI-22 (znacznik), CMDUI-24 (przebieg), CMDUI-26 (`toString` przebiegu). Zależy od 2; równolegle z 3 i 4.
6. **Ekran: panel, podsumowanie, okna, `FLAG_SECURE`** – `ui/commands/CommandsScreen.kt` (panel i podsumowanie, bez przebiegu), `CommandsPanel.kt`, `CarbsSummary.kt`, `CommandDialogs.kt` (okno „Na pewno”), `CommandTexts.kt`; `ui/platform/SecureWindowCounter.kt`, zmiana `SecureWindowEffect.kt`; `AppScaffold.kt` (ViewModele w zakresie `Activity`, `onOpenSettings`, `LocalSecureWindow`); usunięcie `CommandsPlaceholderScreen.kt` i `placeholder_commands_text` (aktualizacja `NavigationStringsTest`); `strings.xml` (blok `commands_*`); testy `CommandTextsTest`, `CommandsSourcesTest`, `SecureWindowCounterTest`. Wymagania: CMDUI-01, CMDUI-02 – CMDUI-04 (widok), CMDUI-07 – CMDUI-10 (widok), CMDUI-23, CMDUI-25 (część). PR opisuje W1, W2, W3, W6, W10 (oraz ponowne W9 ze Spec 008 – skaner nadal chroniony). Zależy od 4 i zadań 2, 3, 8, 10 ze Spec 008.
7. **Ekran przebiegu i wyniku, ekran włączony, Wstecz** – `ui/commands/RunScreen.kt`, `ResultCard.kt`, `RunTexts.kt`, okna „Przestać czekać?” i „Trwa polecenie” w `CommandDialogs.kt`; `ui/platform/KeepScreenOnEffect.kt`; `CommandsScreen.kt` (tryb przebiegu, `BackHandler`, `exitRequested`); `AppScaffold.kt` (`KeepScreenOnEffect`); `strings.xml` (bloki `run_*`, `result_*`); test `RunTextsTest`. Wymagania: CMDUI-16 – CMDUI-22 (widok), CMDUI-24, CMDUI-25 (część). PR opisuje W4, W5, W7, W8, W9, W11, W12 oraz punkty z wcześniejszych spec (sekcja „Punkty weryfikacji z wcześniejszych specyfikacji” w spec): Spec 005 W1–W5, W7; Spec 006 W1 (odczyt), W3, W4, W9, W10 (W2 ze Spec 006 – nie w 009, P4 w spec); Spec 007 W1 (`BG`), W7; Spec 008 UI-15. Zależy od 5 i 6.
