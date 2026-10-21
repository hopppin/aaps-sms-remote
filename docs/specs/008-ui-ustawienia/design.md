# Design 008: UI – ustawienia, sekret TOTP i uprawnienia

Dotyczy: [spec.md](spec.md)

## Przegląd

Pierwsza część pakietu `ui` z [AGENTS.md](../../../AGENTS.md#architektura). `MainActivity` pokazuje `AppScaffold`: `NavHost` (Navigation Compose, trasy tekstowe) i Material 3 `NavigationBar` z trzema zakładkami. Polecenia i Historia to zaślepki; Ustawienia to jeden przewijany ekran z trzema sekcjami, każda z własnym ViewModelem (Hilt):

| Sekcja | ViewModel | Zależności |
| --- | --- | --- |
| Numer telefonu AAPS | `ui.settings.number.AapsNumberViewModel` | `data.AapsNumberSettingsStore`, `session.SessionCoordinator` |
| Sekret TOTP | `ui.settings.secret.TotpSecretViewModel` | `security.TotpSecretStore`, `session.SessionCoordinator`, `ui.permissions.CameraPermission`, `otp.OtpauthParser` (obiekt) |
| Uprawnienia SMS | `ui.permissions.SmsPermissionsViewModel` | `sms.SmsPermissions` |

ViewModele są czystą logiką testowaną w JVM (bez Androida poza klasą `ViewModel`). Kompozycje (`*Section`, `*Dialog`, `QrScanner`) są cienką warstwą: zbierają `StateFlow` (`collectAsStateWithLifecycle`), wywołują metody ViewModelu i obsługują to, co wymaga `Activity` (prośby o uprawnienia, `shouldShowRequestPermissionRationale`, intencja ustawień, `FLAG_SECURE`, CameraX). Skaner QR nie jest trasą nawigacji – jest trybem ekranu Ustawień, dzięki czemu wartość QR trafia wprost do tego samego ViewModelu (bez przekazywania wyników przez `SavedStateHandle` poprzedniego wpisu stosu, który system zapisuje w stanie instancji).

Moduł `session.platform.SessionModule` daje singletony `UsableTotpProvider` i `SessionCoordinator`; ViewModele numeru i sekretu obserwują `activeSession` (UI-15).

Układ plików:

```
ui/
  navigation/AppDestination.kt, AppScaffold.kt
  commands/CommandsPlaceholderScreen.kt
  history/HistoryPlaceholderScreen.kt
  settings/SettingsScreen.kt
  settings/number/NumberDisplay.kt, NumberMessage.kt, AapsNumberUiState.kt, AapsNumberViewModel.kt, NumberSection.kt, NumberTexts.kt
  settings/secret/SecretDisplay.kt, SecretMessage.kt, TotpSecretUiState.kt, TotpSecretViewModel.kt, SecretSection.kt, PasteUriDialog.kt, QrScanner.kt, SecureWindowEffect.kt, SecretTexts.kt
  settings/permissions/SmsPermissionsSection.kt, PermissionTexts.kt
  permissions/PermissionDenial.kt, CameraPermission.kt, SmsPermissionsUiState.kt, SmsPermissionsViewModel.kt
  platform/AndroidCameraPermission.kt, UiModule.kt
  theme/Theme.kt (bez zmian)
session/platform/SessionModule.kt
res/drawable/ic_nav_commands.xml, ic_nav_history.xml, ic_nav_settings.xml
```

## Interfejsy

### Nawigacja (UI-01 – UI-03)

```kotlin
package pl.twojanazwa.aapsremote.ui.navigation

enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    COMMANDS("commands", R.string.nav_commands, R.drawable.ic_nav_commands),
    HISTORY("history", R.string.nav_history, R.drawable.ic_nav_history),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_nav_settings);

    companion object {
        val START: AppDestination = COMMANDS
    }
}

/** Scaffold z NavigationBar (kolejność = AppDestination.entries) i NavHost(startDestination = START.route). */
@Composable
fun AppScaffold(modifier: Modifier = Modifier)
```

Przejście na zakładkę (UI-02):

```kotlin
navController.navigate(destination.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

Zaznaczenie w pasku: `currentBackStackEntryAsState().value?.destination?.hierarchy?.any { it.route == destination.route }`. `MainActivity.setContent { AapsRemoteTheme { AppScaffold() } }`; dotychczasowe `HomeScreen` i jego podgląd są usuwane.

### Uprawnienia (UI-10, UI-17, UI-18)

```kotlin
package pl.twojanazwa.aapsremote.ui.permissions

enum class PermissionDenial { NONE, DENIED, PERMANENTLY_DENIED }

object PermissionDenialClassifier {
    /**
     * [missing] – uprawnienia nadal brakujące po wyniku prośby (odczyt po prośbie).
     * Pusta → NONE; którekolwiek z rationale = true → DENIED; inaczej PERMANENTLY_DENIED.
     */
    fun classify(missing: List<String>, shouldShowRationale: (String) -> Boolean): PermissionDenial
}

/** Stan CAMERA w chwili wywołania, bez zapamiętywania. Implementacja: ui.platform.AndroidCameraPermission. */
fun interface CameraPermission {
    fun isGranted(): Boolean

    companion object {
        const val CAMERA: String = "android.permission.CAMERA"
    }
}

data class SmsPermissionsUiState(
    val canSend: Boolean,
    val canReceive: Boolean,
    val denial: PermissionDenial = PermissionDenial.NONE,
) {
    val allGranted: Boolean get() = canSend && canReceive
    val canRequest: Boolean get() = !allGranted
}

@HiltViewModel
class SmsPermissionsViewModel @Inject constructor(
    private val permissions: SmsPermissions,
) : ViewModel() {
    val uiState: StateFlow<SmsPermissionsUiState>

    /** ON_RESUME ekranu: ponowny odczyt; wszystkie przyznane → denial = NONE, inaczej denial bez zmian. */
    fun onResume()

    /** Brakujące z SmsPermissions.REQUIRED, w tej kolejności. */
    fun permissionsToRequest(): List<String>

    /** Po wyniku prośby: ponowny odczyt SmsPermissions (źródło prawdy), potem PermissionDenialClassifier. */
    fun onPermissionsResult(shouldShowRationale: (String) -> Boolean)
}
```

Warstwa Androida: `ui.platform.AndroidCameraPermission` (`ContextCompat.checkSelfPermission(appContext, CAMERA) == PERMISSION_GRANTED`) i `ui.platform.UiModule` (`@Binds CameraPermission`). Prośby: `rememberLauncherForActivityResult(RequestMultiplePermissions())` (SMS) i `RequestPermission()` (CAMERA); rationale: `ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)` z `LocalActivity.current`. Ustawienia aplikacji: `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))`.

### Schowek (UI-24)

```kotlin
package pl.twojanazwa.aapsremote.ui.settings.secret

/** Port schowka systemowego. Implementacja: ui.platform.AndroidSecretClipboard. Nie loguje, nie przechowuje tekstu. */
interface SecretClipboard {
    /** Tekst pierwszego elementu bieżącego schowka; null, gdy brak, brak dostępu albo wyjątek. */
    fun currentText(): String?

    /** Czyści schowek; wyjątki są połykane (bez logowania). */
    fun clear()
}

/** Czysta logika (UI-24): czyści schowek tylko, gdy [pasted] jest niepusty i schowek zawiera dokładnie ten tekst. */
internal fun clearClipboardIfPasted(clipboard: SecretClipboard, pasted: String)
```

`ui.platform.AndroidSecretClipboard`: `ClipboardManager` z kontekstu aplikacji; `currentText()` = `primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()` (klip z wieloma elementami → `null`, bo `clearPrimaryClip()` usuwa cały klip) w `try`/`catch (e: RuntimeException)` → `null`; `clear()` = `clearPrimaryClip()` (API 28+, minSdk 28) w `try`/`catch`. Wiązanie w `UiModule`. Uwagi platformowe (do W10): od API 29 aplikacja bez fokusu nie czyta schowka (`currentText()` = `null` przy `ON_STOP` → schowek bez zmian); od API 31 system może pokazać powiadomienie o odczycie schowka przez aplikację.

### Numer AAPS (UI-04 – UI-06, UI-15, UI-16)

```kotlin
package pl.twojanazwa.aapsremote.ui.settings.number

sealed interface NumberDisplay {
    data object Loading : NumberDisplay
    data object NotConfigured : NumberDisplay
    class Configured(val destination: String) : NumberDisplay {
        override fun toString(): String = "Configured(***)"
        // equals/hashCode po destination
    }
    data object LoadFailed : NumberDisplay
}

enum class NumberInputError { INVALID }

enum class NumberMessage { SAVED, SAVE_FAILED, CLEARED, CLEAR_FAILED, COMMAND_ACTIVE }

data class AapsNumberUiState(
    val display: NumberDisplay = NumberDisplay.Loading,
    val editing: Boolean = false,
    val input: String = "",
    val inputError: NumberInputError? = null,
    val busy: Boolean = false,
    val commandActive: Boolean = false,
    val confirmingClear: Boolean = false,
    val message: NumberMessage? = null,
) {
    private val idle: Boolean get() = !busy && !commandActive
    val canSet: Boolean get() = idle && (display is NumberDisplay.NotConfigured || display is NumberDisplay.LoadFailed)
    val canChange: Boolean get() = idle && display is NumberDisplay.Configured
    val canClear: Boolean get() = idle && display is NumberDisplay.Configured
    val canSave: Boolean get() = idle && editing
    override fun toString(): String // bez display.destination i input (UI-21)
}

@HiltViewModel
class AapsNumberViewModel @Inject constructor(
    private val store: AapsNumberSettingsStore,
    private val coordinator: SessionCoordinator,
) : ViewModel() {
    val uiState: StateFlow<AapsNumberUiState>

    fun onEditClicked()          // „Ustaw numer” i „Zmień”
    fun onInputChanged(text: String)
    fun onSaveClicked()
    fun onEditCancelled()
    fun onClearClicked()
    fun onClearConfirmed()
    fun onClearDismissed()
    fun onMessageShown()
}

@StringRes fun NumberMessage.textRes(): Int   // NumberTexts.kt
```

### Sekret TOTP (UI-07 – UI-16, UI-23)

```kotlin
package pl.twojanazwa.aapsremote.ui.settings.secret

enum class SecretDisplay { CHECKING, ABSENT, PRESENT, INVALIDATED }   // z SecretState: UNKNOWN → CHECKING

enum class SecretInputMode { NONE, SCANNER, PASTE }

enum class PendingSecretInput { SCAN, PASTE }

enum class SecretMessage {
    SAVED, LOCKED, KEY_GENERATION_FAILED, SAVE_FAILED,
    NOT_TOTP_QR, UNSUPPORTED_PARAMETERS, INVALID_SECRET,
    DELETED, DELETE_FAILED, CAMERA_UNAVAILABLE, COMMAND_ACTIVE,
}

data class TotpSecretUiState(
    val display: SecretDisplay = SecretDisplay.CHECKING,
    val mode: SecretInputMode = SecretInputMode.NONE,
    val pasteText: String = "",
    val confirmingReplace: PendingSecretInput? = null,
    val confirmingDelete: Boolean = false,
    val cameraPermissionRequest: Boolean = false,
    val cameraDenial: PermissionDenial = PermissionDenial.NONE,
    val busy: Boolean = false,
    val commandActive: Boolean = false,
    val message: SecretMessage? = null,
) {
    private val idle: Boolean get() = !busy && !commandActive
    val canScan: Boolean get() = idle
    val canPaste: Boolean get() = idle
    val canDelete: Boolean get() = idle && display != SecretDisplay.ABSENT
    val canSubmitPaste: Boolean get() = idle && pasteText.isNotBlank()
    /** UI-23: FLAG_SECURE, gdy skaner albo okno wklejania otwarte. */
    val secureWindow: Boolean get() = mode != SecretInputMode.NONE
    override fun toString(): String // bez pasteText (UI-21)
}

@HiltViewModel
class TotpSecretViewModel @Inject constructor(
    private val store: TotpSecretStore,
    private val coordinator: SessionCoordinator,
    private val cameraPermission: CameraPermission,
    private val clipboard: SecretClipboard,
) : ViewModel() {
    val uiState: StateFlow<TotpSecretUiState>

    fun onScanClicked()
    fun onPasteClicked()
    fun onReplaceConfirmed()
    fun onReplaceDismissed()
    fun onCameraPermissionRequestLaunched()
    fun onCameraPermissionResult(shouldShowRationale: Boolean)
    fun onQrScanned(raw: String)
    fun onCameraFailed()
    fun onScannerClosed()
    fun onPasteTextChanged(text: String)
    fun onPasteSubmitted()
    fun onPasteDismissed()
    fun onDeleteClicked()
    fun onDeleteConfirmed()
    fun onDeleteDismissed()
    fun onResume()      // ponowne sprawdzenie CAMERA: przyznane → cameraDenial = NONE
    fun onStop()        // UI-14: pasteText = "", mode = NONE; UI-24: schowek, gdy było otwarte okno wklejania
    fun onMessageShown()
}

@StringRes fun SecretMessage.textRes(): Int   // SecretTexts.kt

/** Warstwa kamery – bez testów automatycznych (W3, W4). */
@Composable
fun QrScanner(
    onQrValue: (String) -> Unit,
    onCameraError: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
)

/** Dodaje FLAG_SECURE do okna Activity, gdy [secure] = true; zdejmuje przy false i przy usunięciu z kompozycji. */
@Composable
fun SecureWindowEffect(secure: Boolean)
```

Szkic `QrScanner` (wskazówka dla wykonawcy, nie wymaganie): `LifecycleCameraController(context)` z `cameraSelector = DEFAULT_BACK_CAMERA`, `setEnabledUseCases(IMAGE_ANALYSIS)` (podgląd jest włączony zawsze), `setImageAnalysisAnalyzer(mainExecutor, MlKitAnalyzer(listOf(scanner), COORDINATE_SYSTEM_ORIGINAL, mainExecutor) { … })`, gdzie `scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())`; z wyniku pierwszy kod z niepustym `rawValue` → `onQrValue`. `bindToLifecycle(lifecycleOwner)`, `PreviewView` w `AndroidView` z `controller`. Błąd `initializationFuture` albo `!hasCamera(DEFAULT_BACK_CAMERA)` → `onCameraError`. `DisposableEffect`: `unbind()` i `scanner.close()`. Bez zapisu klatek i bez logowania wartości.

Zachowanie `QrScanner` w implementacji (zadanie 10):

- `onQrValue` i `onCameraError` łącznie najwyżej raz na otwarcie skanera (wspólny znacznik dostawy): po pierwszej wartości albo pierwszym błędzie kolejne wyniki analizatora są pomijane; po `onDispose` nic nie jest zgłaszane.
- Błąd detektora w wyniku `MlKitAnalyzer` (`getThrowable(scanner) != null`) dla pojedynczej klatki jest ignorowany (utracona klatka). 5 kolejnych klatek z błędem z rzędu → `onCameraError`; klatka bez błędu zeruje licznik.
- Wyjątek (`RuntimeException`) z konfiguracji kontrolera albo z `bindToLifecycle` (np. brak tylnej kamery) → `onCameraError` raz (wywołanie przez główny executor, bez logowania i bez treści wyjątku).
- `onDispose`: `clearImageAnalysisAnalyzer()`, `unbind()`, `scanner.close()`.

### Ekran Ustawień

```kotlin
package pl.twojanazwa.aapsremote.ui.settings

@Composable
fun SettingsScreen(
    numberViewModel: AapsNumberViewModel = hiltViewModel(),
    secretViewModel: TotpSecretViewModel = hiltViewModel(),
    smsViewModel: SmsPermissionsViewModel = hiltViewModel(),
)
```

- `LifecycleEventEffect(ON_RESUME)` → `smsViewModel.onResume()`, `secretViewModel.onResume()`; `LifecycleEventEffect(ON_STOP)` → `secretViewModel.onStop()`. Dodatkowo `DisposableEffect(Unit) { onDispose { secretViewModel.onStop() } }`: przy zmianie zakładki `ON_STOP` może nie dotrzeć przed usunięciem ekranu z kompozycji (UI-14); `onStop()` jest idempotentne.
- `mode == SCANNER` → zamiast listy sekcji `QrScanner` na całą treść zakładki + `BackHandler { onScannerClosed() }`.
- Inaczej: przewijana kolumna: baner „polecenie w toku” (gdy `commandActive` w stanie numeru albo sekretu), `NumberSection`, `SecretSection`, `SmsPermissionsSection`; okna potwierdzeń i `PasteUriDialog`; `SnackbarHost` pokazujący `message` obu ViewModeli.
- Snackbar: `LaunchedEffect(tekst komunikatu)` najpierw woła `onMessageShown()` (komunikat jest kasowany w stanie w chwili przekazania do paska), potem uruchamia `showSnackbar` w osobnej korutynie z `rememberCoroutineScope()`. Skutki: powtórzony identyczny komunikat (np. drugi `SAVE_FAILED`) pokazuje się ponownie, bo stan przeszedł przez `null`; po zmianie zakładki i powrocie stary komunikat nie wraca.
- `NumberSection`: pole numeru i „Anuluj” edycji są nieaktywne przy `busy` (zgodnie z `onInputChanged`/`onEditCancelled` w ViewModelu).
- „Otwórz ustawienia aplikacji” (CAMERA i SMS): `startActivity` w `try`; `ActivityNotFoundException` (system bez ekranu informacji o aplikacji) jest ignorowany bez komunikatu i bez logowania.
- „Zezwól” (SMS): `permissionsToRequest()` niepuste → prośba systemowa; puste (uprawnienia nadane z zewnątrz, a `ON_RESUME` jeszcze nie przyszło) → `smsViewModel.onResume()` zamiast prośby.
- `PasteUriDialog`: `DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, dismissOnClickOutside = false)` – dialog jest osobnym oknem, którego `FLAG_SECURE` Activity może nie objąć (UI-23); dotknięcie poza oknem nie zamyka go i nie kasuje wpisu.
- `SecureWindowEffect(secretState.secureWindow)` (UI-23).
- `cameraPermissionRequest == true` → `LaunchedEffect` uruchamia prośbę o CAMERA i woła `onCameraPermissionRequestLaunched()`.
- Pole numeru: `KeyboardOptions(keyboardType = KeyboardType.Phone)`. Pole wklejania: `singleLine = true`, `visualTransformation = PasswordVisualTransformation()`, `KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)`; stan pola wyłącznie z `uiState.pasteText` (bez `rememberSaveable`).

### Moduł sesji (UI-19)

```kotlin
package pl.twojanazwa.aapsremote.session.platform

/** Jeden koordynator na aplikację (SESSION-22). Zakres uruchamiania sesji – spec 009. */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    fun usableTotpProvider(clock: EpochClock): UsableTotpProvider = UsableTotpProvider(clock)

    @Provides
    @Singleton
    fun sessionCoordinator(
        sender: SmsSender,
        incoming: IncomingSmsSource,
        clock: EpochClock,
        totp: UsableTotpProvider,
    ): SessionCoordinator = SessionCoordinator(sender, incoming, clock, totp)
}
```

Miejsce zgodne z konwencją `<pakiet>/platform/<Nazwa>Module.kt` (`SmsModule`, `SecurityModule`, `DataModule`). `UsableTotpProvider` jest wiązany tutaj, bo używa go tylko sesja; `otp/ClockModule` bez zmian.

## Model danych i stany

### Numer AAPS – `AapsNumberViewModel`

`uiState` = `combine(store.state, coordinator.activeSession.map { it != null }, local)` → `stateIn(viewModelScope, SharingStarted.Eagerly, AapsNumberUiState())`, gdzie `local` to `MutableStateFlow` pól edycji, okna, `busy`, `message`. Odwzorowanie `AapsNumberState` → `NumberDisplay`: `Loading` → `Loading`, `NotConfigured` → `NotConfigured`, `Configured(n)` → `Configured(n.destination)`, `LoadFailed` → `LoadFailed`. Osobny kolektor `activeSession` w `init` zamyka okno potwierdzenia przy przejściu `null` → sesja.

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `busy` = false, brak polecenia (`activeSession.value == null`) i stan magazynu ≠ `Loading` – liczone ze źródeł w chwili akcji (odpowiednik `canSet`/`canChange`) | `onEditClicked` | `editing`, `input` = `destination` przy `Configured`, inaczej `""` | – | UI-05, UI-15, UI-16 |
| `busy` albo polecenie w toku albo `Loading` | `onEditClicked` | bez zmian | ignorowane (bez komunikatu) | UI-15, UI-16 |
| `editing`, `busy` = false | `onInputChanged(t)` | `input = t`, `inputError = null` | – | UI-05 |
| `busy` | `onInputChanged(t)` / `onEditCancelled` | bez zmian | ignorowane (wynik zapisu dotyczy tekstu przekazanego do magazynu) | UI-05, UI-16 |
| `editing`, `busy` = false | `onSaveClicked`, `activeSession.value != null` | bez zmian, `message = COMMAND_ACTIVE` | brak wywołania magazynu | UI-15 |
| `editing`, `busy` = false | `onSaveClicked`, brak polecenia | `busy` | `store.save(input)` | UI-05, UI-16 |
| `busy` | `onSaveClicked` / `onClearConfirmed` | bez zmian | ignorowane | UI-16 |
| `busy` | `Saved` | `busy = false`, `editing = false`, `input = ""`, `message = SAVED` | – | UI-05 |
| `busy` | `Invalid` | `busy = false`, `inputError = INVALID` | – | UI-05 |
| `busy` | `Failed` | `busy = false`, `message = SAVE_FAILED` | – | UI-05 |
| `editing`, `busy` = false | `onEditCancelled` | `editing = false`, `input = ""`, `inputError = null` | – | UI-05 |
| `canClear` | `onClearClicked` | `confirmingClear` (albo `COMMAND_ACTIVE` przy poleceniu) | – | UI-06, UI-15 |
| `confirmingClear` | `onClearConfirmed`, `activeSession.value != null` | `confirmingClear = false`, `message = COMMAND_ACTIVE` | brak wywołania magazynu | UI-15 |
| `confirmingClear`, `busy` = false | `onClearConfirmed`, brak polecenia | `confirmingClear = false`, `busy` | `store.clear()` | UI-06, UI-16 |
| `busy` (clear) | `Cleared` / `Failed` | `busy = false`, `message = CLEARED` / `CLEAR_FAILED` | – | UI-06 |
| `confirmingClear` | `onClearDismissed` | `confirmingClear = false` | – | UI-06 |
| dowolny | `activeSession`: `null` → sesja | `confirmingClear = false`, `commandActive` | operacja w toku dokończona | UI-15 |

### Sekret TOTP – `TotpSecretViewModel`

`uiState` = `combine(store.state, activeSession != null, local)` jak wyżej; `init` wywołuje `store.refreshState()` raz (`viewModelScope.launch`). Kolektor `activeSession` przy przejściu `null` → sesja: `confirmingReplace = null`, `confirmingDelete = false`, `mode = NONE`, `pasteText = ""`, `cameraPermissionRequest = false`.

Wspólna procedura `accept(raw)` (wejście ze skanera albo z pola): (1) `activeSession.value != null` → `COMMAND_ACTIVE`, koniec; (2) `busy` → koniec; (3) `busy = true`; (4) `OtpauthParser.parse(raw)` – `Failure` → komunikat z tabeli UI-12, `busy = false`; `Success(secret)` → `store.save(secret)` → komunikat z tabeli, `busy = false`. `secret` i `raw` są tylko zmiennymi lokalnymi korutyny.

`onPasteSubmitted` celowo nie sprawdza `canSubmitPaste` (zawiera `!commandActive` i `!busy`), tylko `mode = PASTE` i niepusty tekst (inaczej wywołanie jest ignorowane): przy wyścigu ze startem polecenia (kolektor jeszcze nie zamknął okna) `accept` zgłasza `COMMAND_ACTIVE` (UI-15) zamiast cichego zignorowania. Przy `busy` pole jest czyszczone i okno zamykane (UI-11, UI-14), a `accept` kończy się w kroku (2) bez komunikatu i bez wywołania parsera i magazynu (UI-16). Dziś ta ścieżka jest nieosiągalna: okno wklejania nie otwiera się przy `busy`, a przycisk „Zapisz” wymaga `canSubmitPaste`.

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `canScan`, `display` ∈ {`CHECKING`, `PRESENT`} | `onScanClicked` | `confirmingReplace = SCAN` | – | UI-08 |
| `canPaste`, `display` ∈ {`CHECKING`, `PRESENT`} | `onPasteClicked` | `confirmingReplace = PASTE` | – | UI-08 |
| `canScan`, `display` ∈ {`ABSENT`, `INVALIDATED`} (`onScanClicked`) albo `confirmingReplace = SCAN` i `canScan` (`onReplaceConfirmed`) | sprawdzenie CAMERA = `true` | `confirmingReplace = null`, `mode = SCANNER`, `cameraDenial = NONE` | – | UI-08, UI-10 |
| j.w. | sprawdzenie CAMERA = `false` | `confirmingReplace = null`, `cameraPermissionRequest = true` | – | UI-08, UI-10 |
| `canPaste`, `display` ∈ {`ABSENT`, `INVALIDATED`} (`onPasteClicked`) albo `confirmingReplace = PASTE` i `canPaste` (`onReplaceConfirmed`) | – | `confirmingReplace = null`, `mode = PASTE`, `pasteText = ""` | – | UI-08, UI-11 |
| `confirmingReplace = SCAN` i `!canScan` albo `confirmingReplace = PASTE` i `!canPaste` (stan liczony ze źródeł w chwili akcji) | `onReplaceConfirmed` | `confirmingReplace = null`; skaner, prośba o CAMERA i okno wklejania nie są otwierane | bez komunikatu | UI-15, UI-16 |
| `confirmingReplace ≠ null` | `onReplaceDismissed` | `confirmingReplace = null` | – | UI-08 |
| `cameraPermissionRequest` | `onCameraPermissionRequestLaunched` | `cameraPermissionRequest = false` | – | UI-10 |
| dowolny (nie wymaga oczekującej prośby ani `cameraPermissionRequest`) | `onCameraPermissionResult(r)` | CAMERA = `true` → `cameraDenial = NONE` oraz `mode = SCANNER` tylko przy `canScan` (liczonym ze źródeł w chwili wyniku), inaczej `mode` bez zmian; CAMERA = `false` → `cameraDenial = classify([CAMERA]) { r }` | – | UI-10, UI-15, UI-16 |
| `mode = SCANNER` | `onQrScanned(raw)` | `mode = NONE` | `accept(raw)` | UI-09, UI-12 |
| `mode ≠ SCANNER` | `onQrScanned(raw)` | bez zmian | ignorowane | UI-09 |
| `mode = SCANNER` | `onCameraFailed` | `mode = NONE`, `message = CAMERA_UNAVAILABLE` | – | UI-09 |
| `mode ≠ SCANNER` | `onCameraFailed` / `onScannerClosed` | bez zmian | ignorowane | UI-09 |
| `mode = SCANNER` | `onScannerClosed` | `mode = NONE` | – | UI-09 |
| `mode = PASTE` | `onPasteTextChanged(t)` | `pasteText = t` | – | UI-11 |
| `mode = PASTE`, `pasteText` niepusty po usunięciu znaków białych (bez sprawdzania `canSubmitPaste`) | `onPasteSubmitted` | `raw = pasteText`; `pasteText = ""`, `mode = NONE` | `clearClipboardIfPasted(clipboard, raw)`, potem `accept(raw)` | UI-11, UI-14, UI-24 |
| `mode = PASTE` | `onPasteDismissed` | `text = pasteText`; `pasteText = ""`, `mode = NONE` | `clearClipboardIfPasted(clipboard, text)` | UI-11, UI-24 |
| `canDelete` | `onDeleteClicked` | `confirmingDelete` (albo `COMMAND_ACTIVE`) | – | UI-13, UI-15 |
| `confirmingDelete` | `onDeleteConfirmed`, `activeSession.value != null` | `confirmingDelete = false`, `message = COMMAND_ACTIVE` | brak wywołania magazynu | UI-15 |
| `confirmingDelete`, `busy` = false | `onDeleteConfirmed`, brak polecenia | `confirmingDelete = false`, `busy` | `store.delete()` | UI-13, UI-16 |
| `busy` (delete) | `Deleted` / `Failed` | `busy = false`, `message = DELETED` / `DELETE_FAILED` | – | UI-13 |
| dowolny | `onStop` | `text = pasteText` (gdy `mode = PASTE`); `pasteText = ""`, `mode = NONE` | przy `mode = PASTE`: `clearClipboardIfPasted(clipboard, text)` | UI-14, UI-24 |
| `cameraDenial ≠ NONE` | `onResume`, CAMERA = `true` | `cameraDenial = NONE` | – | UI-10 |

Odwzorowanie wyników na `SecretMessage` (UI-12):

| Wynik | `SecretMessage` |
| --- | --- |
| `OtpauthError.NOT_OTPAUTH_URI`, `MALFORMED_URI`, `UNSUPPORTED_TYPE` | `NOT_TOTP_QR` |
| `UNSUPPORTED_ALGORITHM`, `UNSUPPORTED_DIGITS`, `UNSUPPORTED_PERIOD` | `UNSUPPORTED_PARAMETERS` |
| `MISSING_SECRET`, `DUPLICATE_PARAMETER`, `EMPTY_SECRET`, `INVALID_BASE32` | `INVALID_SECRET` |
| `SaveResult.Saved` | `SAVED` |
| `SaveResult.Locked` | `LOCKED` |
| `SaveResult.Failed(KEY_GENERATION)` | `KEY_GENERATION_FAILED` |
| `SaveResult.Failed(CRYPTO)`, `Failed(STORAGE)` | `SAVE_FAILED` |
| `DeleteResult.Deleted` / `Failed` | `DELETED` / `DELETE_FAILED` |

`when` po `OtpauthError` jest wyczerpujący (bez `else`), żeby nowa wartość wymusiła decyzję w kodzie.

### Uprawnienia SMS – `SmsPermissionsViewModel`

`MutableStateFlow<SmsPermissionsUiState>` (bez `combine`): `init` czyta `canSend()`, `canReceive()`; `onResume` i `onPermissionsResult` czytają ponownie. `onPermissionsResult` liczy `missing` z nowego odczytu i `PermissionDenialClassifier.classify(missing, shouldShowRationale)`.

### Teksty (`strings.xml`, UI-20)

Klucze są wiążące; treści – jak w spec (drobne zmiany redakcyjne w recenzji PR). Każde zadanie dodaje swój blok kluczy z komentarzem XML `<!-- 008: <obszar> -->`.

| Klucz | Treść |
| --- | --- |
| `nav_commands` | Polecenia |
| `nav_history` | Historia |
| `nav_settings` | Ustawienia |
| `placeholder_commands_text` | Polecenia pojawią się w kolejnej wersji. Do tego czasu skonfiguruj aplikację w zakładce Ustawienia. |
| `placeholder_history_text` | Historia poleceń pojawi się w kolejnej wersji. |
| `action_save` | Zapisz |
| `action_cancel` | Anuluj |
| `action_delete` | Usuń |
| `action_open_app_settings` | Otwórz ustawienia aplikacji |
| `settings_command_active` | Trwa polecenie. Numer i sekret możesz zmienić po jego zakończeniu. |
| `msg_command_active` | Trwa polecenie – spróbuj po jego zakończeniu. |
| `number_section_title` | Numer telefonu AAPS |
| `number_loading` | Wczytywanie… |
| `number_not_configured` | Nie ustawiono numeru AAPS |
| `number_load_failed` | Nie udało się wczytać numeru. Ustaw go ponownie. |
| `number_action_set` | Ustaw numer |
| `number_action_change` | Zmień |
| `number_field_label` | Numer telefonu z AAPS |
| `number_error_invalid` | Nieprawidłowy numer. Podaj co najmniej 9 cyfr, opcjonalnie z „+” na początku. |
| `number_clear_title` | Usunąć numer AAPS? |
| `number_clear_text` | Bez numeru aplikacja nie wyśle poleceń. |
| `number_msg_saved` | Zapisano numer AAPS |
| `number_msg_save_failed` | Nie udało się zapisać numeru. Spróbuj ponownie. |
| `number_msg_cleared` | Usunięto numer AAPS |
| `number_msg_clear_failed` | Nie udało się usunąć numeru. Spróbuj ponownie. |
| `secret_section_title` | Sekret TOTP |
| `secret_checking` | Sprawdzanie… |
| `secret_absent` | Brak sekretu TOTP. Zeskanuj kod QR z AAPS. |
| `secret_present` | Sekret TOTP zapisany |
| `secret_invalidated` | Sekret TOTP przestał działać (np. po zmianie blokady ekranu). Zeskanuj ponownie kod QR z AAPS. |
| `secret_action_scan` | Skanuj QR |
| `secret_action_paste` | Wklej URI |
| `secret_action_delete` | Usuń sekret |
| `secret_replace_title` | Zastąpić zapisany sekret TOTP? |
| `secret_replace_text` | Kody z poprzedniego sekretu przestaną działać w aplikacji. |
| `secret_replace_confirm` | Zastąp |
| `secret_delete_title` | Usunąć sekret TOTP? |
| `secret_delete_text` | Polecenia wymagające kodu (CARBS) nie będą działać do ponownego zeskanowania QR. |
| `secret_paste_title` | Wklej URI z AAPS |
| `secret_paste_label` | URI otpauth:// |
| `secret_scanner_hint` | Skieruj aparat na kod QR pokazany w AAPS. |
| `secret_camera_denied` | Bez dostępu do aparatu nie zeskanujesz kodu QR. Możesz wkleić URI z AAPS. |
| `secret_msg_saved` | Zapisano sekret TOTP |
| `secret_msg_locked` | Odblokuj telefon i spróbuj ponownie. |
| `secret_msg_key_generation_failed` | Nie udało się utworzyć klucza szyfrowania. Spróbuj ponownie. |
| `secret_msg_save_failed` | Nie udało się zapisać sekretu. Spróbuj ponownie. |
| `secret_msg_not_totp_qr` | To nie jest kod QR TOTP z AAPS. |
| `secret_msg_unsupported_parameters` | Nieobsługiwane parametry TOTP. Wymagane: SHA1, 6 cyfr, 30 s. |
| `secret_msg_invalid_secret` | Kod nie zawiera poprawnego sekretu TOTP. |
| `secret_msg_deleted` | Usunięto sekret TOTP |
| `secret_msg_delete_failed` | Nie udało się usunąć sekretu. Spróbuj ponownie. |
| `secret_msg_camera_unavailable` | Nie udało się uruchomić aparatu. Możesz wkleić URI z AAPS. |
| `sms_section_title` | Uprawnienia SMS |
| `sms_all_granted` | Wysyłanie i odbiór SMS dozwolone |
| `sms_missing_label` | Brak uprawnień: |
| `sms_permission_send` | wysyłanie SMS |
| `sms_permission_receive` | odbiór SMS |
| `sms_action_request` | Zezwól |
| `sms_denied` | Bez uprawnień SMS aplikacja nie wyśle polecenia ani nie odbierze odpowiedzi AAPS. |

`NumberMessage.COMMAND_ACTIVE` i `SecretMessage.COMMAND_ACTIVE` → `msg_command_active` (ten sam tekst; test różnych identyfikatorów w UI-20 dotyczy wartości w obrębie jednego typu).

### Zależności Gradle (UI-22)

Najnowsze stabilne wersje z `dl.google.com/android/maven2` (sprawdzone 2026-10-21). Wykonawca dodaje je do `gradle/libs.versions.toml` i `app/build.gradle.kts` (`implementation`).

| Alias w katalogu | Artefakt | Wersja | Po co |
| --- | --- | --- | --- |
| `androidx-navigation-compose` | `androidx.navigation:navigation-compose` | 2.10.2 (`navigation`) | `NavHost`, trasy tekstowe |
| `androidx-hilt-lifecycle-viewmodel-compose` | `androidx.hilt:hilt-lifecycle-viewmodel-compose` | 1.4.0 (`hiltAndroidx`) | `hiltViewModel()` (`androidx.hilt.lifecycle.viewmodel.compose`) |
| `androidx-lifecycle-viewmodel-compose` | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 (`lifecycle`, istniejąca) | `viewModelScope`, `ViewModel` w Compose |
| `androidx-lifecycle-runtime-compose` | `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 (`lifecycle`) | `collectAsStateWithLifecycle`, `LifecycleEventEffect` |
| `androidx-camera-camera2` | `androidx.camera:camera-camera2` | 1.6.2 (`camerax`) | implementacja CameraX |
| `androidx-camera-lifecycle` | `androidx.camera:camera-lifecycle` | 1.6.2 | wiązanie z cyklem życia |
| `androidx-camera-view` | `androidx.camera:camera-view` | 1.6.2 | `PreviewView`, `LifecycleCameraController` |
| `androidx-camera-mlkit-vision` | `androidx.camera:camera-mlkit-vision` | 1.6.2 | `MlKitAnalyzer` |
| `mlkit-barcode-scanning` | `com.google.mlkit:barcode-scanning` | 17.3.0 (`mlkitBarcode`) | model QR w paczce, offline |

Uwagi:

- `hilt-navigation-compose` nie jest potrzebne (od 1.3 `hiltViewModel()` jest w `hilt-lifecycle-viewmodel-compose`; `ViewModelStoreOwner` wpisu nawigacji działa bez niego).
- `com.google.mlkit:barcode-scanning` zawiera model w paczce (aar ok. 9,9 MB dla wszystkich ABI; APK debug urośnie o kilka MB). Wariant `play-services-mlkit-barcode-scanning` (model z Usług Google Play) jest wykluczony (UI-22).
- Zależności przechodnie ML Kit dodają do połączonego manifestu: `MlKitInitProvider`, `MlKitComponentDiscoveryService`, usługi i receiver `com.google.android.datatransport` (nieeksportowane) oraz `INTERNET` i `ACCESS_NETWORK_STATE` z `transport-backend-cct`/`transport-runtime`. Decyzja użytkownika (P1): aplikacja bez sieci – manifest aplikacji dostaje `xmlns:tools="http://schemas.android.com/tools"` oraz `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` i to samo dla `ACCESS_NETWORK_STATE`. Test manifestu (UI-22) sprawdza oba wpisy z `tools:node="remove"`; `SmsManifestTest` (SMS-15) przechodzi bez zmian (jego lista wykluczonych uprawnień nie obejmuje `INTERNET` ani `ACCESS_NETWORK_STATE`). Komponenty ML Kit i `datatransport` zostają w połączonym manifeście (nieeksportowane; bez sieci telemetria się nie wysyła). Brak uprawnień sieciowych w APK potwierdza W8.
- Testy: bez nowych zależności (JUnit 5, MockK, Turbine, `kotlinx-coroutines-test` już są). `ViewModel` z `lifecycle-viewmodel` działa w JVM; dyspozytor główny zastępuje `MainDispatcherExtension` (`Dispatchers.setMain(StandardTestDispatcher())`).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Trzy ViewModele na ekranie Ustawień (po jednym na sekcję) | jeden `SettingsViewModel` | rozłączne pliki i testy → zadania równoległe; każdy ViewModel ma 2–3 zależności; `SmsPermissionsViewModel` użyje też 009 |
| Skaner jako tryb ekranu Ustawień, nie trasa nawigacji | trasa `settings/scan` z wynikiem przez `SavedStateHandle` | wynik przez `SavedStateHandle` trafiłby do stanu instancji zapisywanego przez system; tryb w tym samym ViewModelu trzyma wartość QR tylko lokalnie (UI-14) |
| Pytanie o zastąpienie przed skanem/wklejeniem | pytanie po odczycie QR | po odczycie ViewModel musiałby trzymać sekret, dopóki okno jest otwarte; przed odczytem nie ma czego trzymać (UI-08, UI-14) |
| `UNKNOWN` traktowany jak `PRESENT` (pytanie o zastąpienie, akcje dostępne) | blokada do poznania stanu | `refreshState()` przy błędzie zostawia `UNKNOWN` (SECRET-12) – blokada mogłaby trwać do końca procesu; pytanie chroni istniejący sekret |
| Tekst pola wklejania w stanie ViewModelu, czyszczony przy `ON_STOP` | `rememberSaveable` w kompozycji; zachowanie przy obrocie | `rememberSaveable` zapisuje tekst w `Bundle` stanu instancji (może trafić na dysk); `ON_STOP` obejmuje przejście w tło (miniatura w ostatnich aplikacjach). Koszt: obrót ekranu zamyka okno i skaner – akceptowany |
| Klawiatura typu hasło + maskowanie pola URI | zwykłe pole tekstowe | słownik klawiatury nie uczy się fragmentów sekretu; brak podglądu przez ramię |
| Klasyfikacja trwałej odmowy po wyniku prośby (`shouldShowRequestPermissionRationale`), bez zapisu na dysku | zapamiętywanie „pytano już” w `SharedPreferences` | brak nowych danych na dysku; po restarcie kolejna prośba bez okna i tak zwraca odmowę → trwała. „Zezwól” zostaje dostępne, bo system bywa niejednoznaczny (zamknięcie okna Wstecz) |
| Blokada zmiany sekretu w trakcie polecenia (UI-15) | blokada tylko numeru (wymaganie Spec 007) | dla poprawności niepotrzebna (sesja ma własną kopię sekretu), ale jedna reguła dla obu ustawień jest prostsza, bez wątpliwości „którym sekretem potwierdzono” i bez uruchamiania aparatu w trakcie polecenia (spec, Bezpieczeństwo) |
| Sprawdzenie `activeSession.value` w chwili akcji, nie tylko w stanie ekranu | tylko nieaktywne przyciski | stan ekranu jest asynchroniczny (kolektor); sprawdzenie w akcji zamyka wyścig między startem polecenia a dotknięciem (UI-15) |
| ViewModele zależą od klasy `SessionCoordinator`, w testach MockK | nowy interfejs portu „polecenie w toku” | brak zmian w `session`; MockK mockuje klasy finalne; jedno źródło prawdy (SESSION-22) |
| `CameraPermission` jako port z implementacją w `ui.platform` | sprawdzenie w kompozycji i przekazanie wartości do ViewModelu | ViewModel sam decyduje o otwarciu skanera – cały przepływ uprawnienia testowalny w JVM; wzór jak `SmsPermissions` |
| Trasy tekstowe Navigation Compose | trasy typowane (`@Serializable`) | bez wtyczki `kotlinx-serialization`; trzy stałe trasy bez argumentów |
| Ikony zakładek jako zasoby wektorowe | `material-icons-extended` / `-core` | brak dużej zależności; Material 3 z BOM nie dostarcza ikon przechodnio |
| ML Kit z modelem w paczce + `MlKitAnalyzer` z `camera-mlkit-vision` | model z Usług Google Play; ZXing | decyzja użytkownika (offline, tylko QR); `MlKitAnalyzer` + `LifecycleCameraController` to najkrótsze połączenie CameraX z ML Kit |
| `SharingStarted.Eagerly` w `stateIn` | `WhileSubscribed(5000)` | ViewModele są lekkie; `uiState.value` aktualne w testach bez subskrybenta; brak kosztownych przepływów do zatrzymywania |
| `SessionModule` w `session/platform/` | `otp/ClockModule`, nowy moduł w `ui` | konwencja `<pakiet>/platform/<Nazwa>Module.kt`; koordynator należy do `session` |

## Mapowanie wymagań na testy

Wszystkie testy JVM w `app/src/test/java/pl/twojanazwa/aapsremote/`. Konwencja: identyfikator wymagania jest w nazwie każdej metody testowej (nazwa w backtickach, np. `` `UI-12 bledny Base32 daje INVALID_SECRET bez zapisu` ``); tabela niżej mapuje wymagania na klasy testów. Pomocnicze: `ui/MainDispatcherExtension.kt`, `ui/SessionDoubles.kt` (`activeSession` jako `MutableStateFlow<CommandSession?>`, `mockk<CommandSession>()`), `ui/settings/number/FakeAapsNumberSettingsStore.kt`, `ui/settings/secret/FakeTotpSecretStore.kt` (wyniki z kolejki, opcjonalna bramka `CompletableDeferred`, zapamiętany `TotpSecret`), `ui/permissions/FakeCameraPermission.kt`, `ui/settings/secret/FakeSecretClipboard.kt` (tekst ustawiany w teście, licznik `clear()`); `sms/FakeSmsPermissions.kt` istnieje.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| UI-01 | `ui/navigation/AppDestinationTest` – jednostkowy; W1 |
| UI-02 | W1 |
| UI-03 | `ui/navigation/NavigationStringsTest` – plik `strings.xml`; W1 |
| UI-04 | `ui/settings/number/AapsNumberViewModelStateTest` – ViewModel (Turbine) |
| UI-05 | `ui/settings/number/AapsNumberViewModelEditTest` – ViewModel |
| UI-06 | `ui/settings/number/AapsNumberViewModelClearTest` – ViewModel |
| UI-07 | `ui/settings/secret/TotpSecretViewModelStateTest` – ViewModel (Turbine) |
| UI-08 | `ui/settings/secret/TotpSecretViewModelReplaceTest` – ViewModel |
| UI-09 | `ui/settings/secret/TotpSecretViewModelScanTest` – ViewModel; W3 |
| UI-10 | `ui/settings/secret/TotpSecretViewModelCameraPermissionTest`, `ui/permissions/PermissionDenialClassifierTest` – jednostkowe; W4 |
| UI-11 | `ui/settings/secret/TotpSecretViewModelPasteTest` – ViewModel + refleksja konstruktora + odczyt źródeł; W5 |
| UI-12 | `ui/settings/secret/TotpSecretViewModelSaveTest` – ViewModel (wszystkie `OtpauthError`, wszystkie `SaveResult`) |
| UI-13 | `ui/settings/secret/TotpSecretViewModelDeleteTest` – ViewModel; W6 |
| UI-14 | `ui/settings/secret/TotpSecretViewModelRetentionTest` – refleksja, bramka zapisu, `onStop` |
| UI-15 | `ui/settings/number/AapsNumberViewModelLockTest`, `ui/settings/secret/TotpSecretViewModelLockTest`, `ui/permissions/SmsPermissionsViewModelTest` (brak blokady) – ViewModel; ręcznie w pierwszym PR 009 |
| UI-16 | `AapsNumberViewModelLockTest`, `TotpSecretViewModelLockTest` – bramki zapisu |
| UI-17 | `ui/permissions/SmsPermissionsViewModelTest` – ViewModel; W7 |
| UI-18 | `ui/permissions/SmsPermissionsViewModelTest`, `PermissionDenialClassifierTest`; W7 |
| UI-19 | `session/platform/SessionModuleTest` – refleksja + wywołanie metod modułu z atrapami; `assembleDebug` |
| UI-20 | `ui/settings/number/NumberTextsTest`, `ui/settings/secret/SecretTextsTest`, `ui/settings/permissions/PermissionTextsTest`, `NavigationStringsTest` – odwzorowanie i klucze w `strings.xml`; W1–W7 |
| UI-21 | `ui/UiNoLoggingTest` – źródła `ui/` i `session/platform/`; `ui/settings/number/NumberRedactionTest`, `ui/settings/secret/SecretRedactionTest` – `toString()` |
| UI-22 | `ui/UiManifestTest` – manifest; `ui/UiDependenciesTest` – `libs.versions.toml`; W8 |
| UI-23 | `ui/settings/secret/TotpSecretViewModelSecureWindowTest` – znacznik `secureWindow`; W9 |
| UI-24 | `ui/settings/secret/ClipboardClearingTest` (funkcja `clearClipboardIfPasted`), `ui/settings/secret/TotpSecretViewModelClipboardTest` (zapis, anulowanie, `onStop`) – atrapa schowka; W10 |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Wymagania częściowe oznaczono „(część)”.

**Zależności i równoległość:** 1 → {2, 3, 4, 5} równolegle (rozłączne pliki); 6 ← 5; 7 ← 6; 8 ← 2, 3, 4; 9 ← 2, 6; 10 ← 7, 9. Zadania 8 i 9 mogą iść równolegle, ale oba zmieniają `SettingsScreen.kt` i `strings.xml` – drugi PR robi rebase na pierwszym.

**Pliki wspólne:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `ui/platform/UiModule.kt`, `ui/permissions/PermissionDenial.kt`, `ui/permissions/CameraPermission.kt`, `ui/settings/secret/SecretClipboard.kt` – tylko zadanie 1 (typy używane przez zadania 3 i 5–7 powstają raz, przed nimi); `MainActivity.kt` – tylko zadanie 2; `strings.xml` – zadania 2, 8, 9, 10 (każde dopisuje własny blok z komentarzem `<!-- 008: … -->`, bez zmian cudzych kluczy); `ui/settings/SettingsScreen.kt` – tworzy 2, zmieniają 8, 9, 10; `ui/settings/secret/TotpSecretViewModel.kt` i `TotpSecretUiState.kt` – 5, 6, 7 kolejno.

1. **Fundament: zależności, manifest, moduł sesji, typy uprawnień** – `gradle/libs.versions.toml`, `app/build.gradle.kts` (wszystkie zależności z tabeli), `AndroidManifest.xml` (`CAMERA`, `uses-feature camera.any required=false`; `tools:node="remove"` dla `INTERNET` i `ACCESS_NETWORK_STATE`), `session/platform/SessionModule.kt`, `ui/permissions/PermissionDenial.kt` (z klasyfikatorem), `ui/permissions/CameraPermission.kt`, `ui/platform/AndroidCameraPermission.kt`, `ui/settings/secret/SecretClipboard.kt` (port i `clearClipboardIfPasted`), `ui/platform/AndroidSecretClipboard.kt`, `ui/platform/UiModule.kt`; testy `SessionModuleTest`, `UiManifestTest`, `UiDependenciesTest`, `UiNoLoggingTest`, `PermissionDenialClassifierTest`, `ClipboardClearingTest`, pomocnicze `MainDispatcherExtension`, `SessionDoubles`, `FakeCameraPermission`, `FakeSecretClipboard`. Wymagania: UI-19, UI-21 (logowanie), UI-22, UI-10 i UI-18 (klasyfikacja odmowy), UI-24 (logika porównania); `assembleDebug`; W8. Bez zależności.
2. **Nawigacja i zaślepki** – `ui/navigation/AppDestination.kt`, `AppScaffold.kt`, `ui/commands/CommandsPlaceholderScreen.kt`, `ui/history/HistoryPlaceholderScreen.kt`, `ui/settings/SettingsScreen.kt` (szkielet bez sekcji), `res/drawable/ic_nav_*.xml`, `MainActivity.kt`, `strings.xml` (blok nawigacji, zaślepek, `action_*`); testy `AppDestinationTest`, `NavigationStringsTest`. Wymagania: UI-01, UI-02, UI-03, UI-20 (część); W1. Zależy od 1.
3. **Uprawnienia SMS – ViewModel** – `ui/permissions/SmsPermissionsUiState.kt`, `SmsPermissionsViewModel.kt`; test `SmsPermissionsViewModelTest`. Wymagania: UI-17, UI-18 (logika), UI-15 (uprawnienia bez blokady). Zależy od 1; równolegle z 2, 4, 5.
4. **Numer – ViewModel** – `ui/settings/number/NumberDisplay.kt`, `NumberMessage.kt`, `AapsNumberUiState.kt`, `AapsNumberViewModel.kt`; testy `FakeAapsNumberSettingsStore`, `AapsNumberViewModelStateTest`, `…EditTest`, `…ClearTest`, `…LockTest`, `NumberRedactionTest`. Wymagania: UI-04, UI-05, UI-06, UI-15 (numer), UI-16 (numer), UI-21 (`toString` numeru). Zależy od 1; równolegle z 2, 3, 5.
5. **Sekret – ViewModel: stan, wklejanie, zapis** – `ui/settings/secret/SecretDisplay.kt`, `SecretMessage.kt`, `TotpSecretUiState.kt`, `TotpSecretViewModel.kt` (konstruktor z `CameraPermission` i `SecretClipboard`, bez logiki skanera i schowka); testy `FakeTotpSecretStore`, `TotpSecretViewModelStateTest`, `…ReplaceTest` (ścieżka wklejania), `…PasteTest`, `…SaveTest`. Wymagania: UI-07, UI-08 (wklejanie), UI-11 (logika), UI-12, UI-14 (część: refleksja, czyszczenie pola przy zapisie). Zależy od 1; równolegle z 2, 3, 4.
6. **Sekret – ViewModel: usunięcie, blokady, `onStop`, schowek** – `TotpSecretViewModel.kt`, `TotpSecretUiState.kt`; testy `TotpSecretViewModelDeleteTest`, `…RetentionTest`, `…LockTest`, `…ClipboardTest`, `SecretRedactionTest`. Wymagania: UI-13, UI-14, UI-15 (sekret), UI-16 (sekret), UI-21 (`toString` sekretu), UI-24 (ViewModel). Zależy od 5.
7. **Sekret – ViewModel: skaner i CAMERA** – `TotpSecretViewModel.kt`, `TotpSecretUiState.kt`; testy `TotpSecretViewModelScanTest`, `…CameraPermissionTest`, `…ReplaceTest` (ścieżka skanu), `…SecureWindowTest`. Wymagania: UI-08 (skan), UI-09 (logika), UI-10 (logika), UI-23 (znacznik). Zależy od 6.
8. **Ekran: sekcje numeru i uprawnień SMS** – `ui/settings/number/NumberSection.kt`, `NumberTexts.kt`, `ui/settings/permissions/SmsPermissionsSection.kt`, `PermissionTexts.kt`, `SettingsScreen.kt` (sekcje, baner, Snackbar, `ON_RESUME`, prośba o SMS, intencja ustawień), `strings.xml` (bloki numeru, SMS, `settings_command_active`, `msg_command_active`); testy `NumberTextsTest`, `PermissionTextsTest`. Wymagania: UI-04 – UI-06 (widok), UI-17, UI-18 (widok), UI-20 (część). PR opisuje W2, W7 oraz ze Spec 007: W1 (bez `BG`), W2, W6 (numer); ze Spec 005: W6. Zależy od 2, 3, 4.
9. **Ekran: sekcja sekretu i wklejanie** – `ui/settings/secret/SecretSection.kt`, `PasteUriDialog.kt`, `SecretTexts.kt`, `SettingsScreen.kt` (sekcja sekretu, okna, `ON_STOP`), `strings.xml` (blok sekretu bez skanera); test `SecretTextsTest`. Wymagania: UI-07, UI-08, UI-11, UI-12, UI-13 (widok), UI-20 (część). PR opisuje W5, W6, W10 i – jako pierwszy PR zapisujący sekret – punkty Spec 006 możliwe bez odczytu (podział P3 ze spec): W1 (zapis, `KeyInfo`), W5, W6, W7, W8. Zależy od 2, 6.
10. **Skaner QR (CameraX + ML Kit) i `FLAG_SECURE`** – `ui/settings/secret/QrScanner.kt`, `SecureWindowEffect.kt`, `SecretSection.kt`/`SettingsScreen.kt` (tryb skanera, `BackHandler`, prośba o CAMERA, tekst odmowy, przycisk ustawień), `strings.xml` (`secret_scanner_hint`, `secret_camera_denied`, `secret_msg_camera_unavailable`); bez nowych testów automatycznych (warstwa kamery), rozszerzenie `SecretTextsTest` o nowe klucze. Wymagania: UI-09, UI-10, UI-23 (widok). PR opisuje W3, W4, W9 i ponownie W5–W7 ze Spec 006 dla zapisu ze skanera. Zależy od 7, 9.
