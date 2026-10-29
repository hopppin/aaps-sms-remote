package pl.twojanazwa.aapsremote.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.security.AapsPin
import pl.twojanazwa.aapsremote.security.TotpSecretStore
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.sms.SmsPermissions

/**
 * Panel poleceń: gotowość (CMDUI-02 – CMDUI-05), `BG`/`PUMP` jednym dotknięciem (CMDUI-05, CMDUI-06)
 * i komunikaty (CMDUI-12). Pole CARBS i przekazanie do przebiegu (`CurrentRun`) dochodzą w kolejnych zadaniach.
 * Nie czyta sekretu (`load()`), tylko jego stan.
 */
@HiltViewModel
class CommandsViewModel @Inject constructor(
    private val launcher: CommandLauncher,
    private val coordinator: SessionCoordinator,
    private val currentRun: CurrentRun,
    private val numbers: AapsNumberSettingsStore,
    private val secrets: TotpSecretStore,
    private val permissions: SmsPermissions,
    private val limitsStore: CommandLimitsStore,
) : ViewModel() {
    /** Część stanu własna ViewModelu (początkowy odczyt uprawnień, CMDUI-03); gotowość dokłada `combine`. */
    private val local = MutableStateFlow(CommandsUiState(smsGranted = readPermissions()))

    val uiState: StateFlow<CommandsUiState> = combine(
        numbers.state,
        secrets.state,
        coordinator.activeSession,
        limitsStore.state,
        local,
    ) { number, secret, session, limits, own ->
        own.copy(
            number = number.toReadiness(),
            secret = secret.toReadiness(),
            commandActive = session != null,
            limits = limits,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, compose())

    init {
        viewModelScope.launch { secrets.refreshState() }
        // Kolektor tylko wyłącza przekazanie; drugi warunek chroni przed spóźnioną wartością null (CMDUI-23).
        viewModelScope.launch {
            currentRun.state.collect { run ->
                if (run == null && currentRun.state.value == null) local.update { it.copy(runHandoff = false) }
            }
        }
    }

    /** ON_RESUME zakładki i po wyniku prośby o uprawnienia (CMDUI-03). */
    fun refreshPermissions() {
        val granted = readPermissions()
        local.update { it.copy(smsGranted = granted) }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    fun onBgClicked() = startNoCode(Command.Bg)

    fun onPumpClicked() = startNoCode(Command.Pump)

    fun onCarbsInputChanged(text: String) {
        local.update { it.copy(carbsInput = text, carbsError = null) }
    }

    fun onCarbsNextClicked() {
        val state = compose()
        val limits = state.loadedLimits
        if (!state.canStartCarbs || limits == null) return
        when (val result = Command.Carbs.parse(local.value.carbsInput, limits)) {
            is CarbsValidation.Invalid ->
                local.update { it.copy(carbsError = result.error) }
            is CarbsValidation.Valid -> {
                val carbs = result.command
                if (carbs.requiresExtraConfirmation(limits)) {
                    local.update { it.copy(confirmingLargeGrams = carbs.grams) }
                } else {
                    local.update { it.copy(summaryGrams = carbs.grams, pinText = "", extraConfirmed = false) }
                }
            }
        }
    }

    fun onQuickValueClicked(grams: Int) {
        if (!compose().canStartCarbs) return
        local.update {
            it.copy(
                extraConfirmed = false,
                carbsInput = grams.toString(),
                carbsError = null,
                summaryGrams = grams,
                confirmingLargeGrams = null,
                pinText = "",
            )
        }
    }

    fun onLargeAmountConfirmed() {
        val grams = local.value.confirmingLargeGrams ?: return
        local.update {
            it.copy(confirmingLargeGrams = null, summaryGrams = grams, pinText = "", extraConfirmed = true)
        }
    }

    fun onLargeAmountDismissed() {
        local.update { it.copy(confirmingLargeGrams = null) }
    }

    fun onPinChanged(text: String) {
        if (local.value.summaryGrams == null || local.value.launching) return
        if (text.length > PIN_MAX_LENGTH || !text.all { it in '0'..'9' }) return
        local.update { it.copy(pinText = text) }
    }

    /** PIN trafia z pola do [AapsPin] i pole jest czyszczone przed `launch` (CMDUI-11). */
    fun onSendClicked() {
        val state = compose()
        val grams = state.summaryGrams
        if (grams == null || !state.canSubmitPin) return
        val chars = state.pinText.toCharArray()
        local.update { it.copy(pinText = "") }
        val pin = AapsPin.fromChars(chars)
        val limits = state.loadedLimits
        if (limits == null) {
            pin.clear()
            local.update { it.copy(summaryGrams = null, message = CommandMessage.LIMITS_UNAVAILABLE) }
            return
        }
        val carbs = (Command.Carbs.of(grams, limits) as? CarbsValidation.Valid)?.command
        if (carbs == null) {
            // LIM-09: limit zmniejszony po otwarciu podsumowania; ilość nie jest przycinana.
            pin.clear()
            local.update { it.copy(summaryGrams = null, message = CommandMessage.AMOUNT_ABOVE_LIMIT) }
            return
        }
        startCommand(carbs, pin, state.extraConfirmed)
    }

    /** W trakcie uruchamiania „Anuluj” i Wstecz nie ukrywają podsumowania (CMDUI-10, CMDUI-11). */
    fun onSummaryDismissed() {
        if (local.value.launching) return
        local.update { it.copy(summaryGrams = null, pinText = "", extraConfirmed = false) }
    }

    /** Okno „Na pewno” -> zamknij; podsumowanie -> jak „Anuluj” (CMDUI-24). */
    fun onBack() {
        if (local.value.launching) return
        if (local.value.confirmingLargeGrams != null) onLargeAmountDismissed() else onSummaryDismissed()
    }

    /** Aplikacja schodzi z ekranu albo zmiana zakładki: PIN i okna znikają, tekst ilości zostaje (CMDUI-11). */
    fun onStop() {
        local.update { it.copy(pinText = "", summaryGrams = null, confirmingLargeGrams = null, extraConfirmed = false) }
    }

    private fun startNoCode(command: Command) {
        if (local.value.launching) return
        if (coordinator.activeSession.value == null && !compose().canSendNoCode) return
        startCommand(command, null)
    }

    /**
     * Procedura wspólna (design 009): `launching` i `activeSession` sprawdzane synchronicznie na głównym wątku,
     * więc podwójne dotknięcie nie woła launchera drugi raz. Sesję uruchamia launcher w zakresie aplikacji,
     * nie `viewModelScope` (CMDUI-13).
     */
    private fun startCommand(command: Command, pin: AapsPin?, extraConfirmed: Boolean = false) {
        if (local.value.launching) {
            pin?.clear()
            return
        }
        if (coordinator.activeSession.value != null) {
            pin?.clear()
            local.update { it.copy(message = CommandMessage.COMMAND_ACTIVE) }
            return
        }
        local.update { it.copy(launching = true) }
        // ATOMIC: korutyna rusza nawet po anulowaniu zakresu, więc launcher zawsze czyści PIN (CMDUI-11).
        viewModelScope.launch(start = CoroutineStart.ATOMIC) {
            val result = try {
                launcher.launch(command, pin, extraConfirmed)
            } finally {
                local.update { it.copy(launching = false) }
            }
            local.update {
                when (result) {
                    LaunchResult.Started -> it.copy(
                        summaryGrams = null, extraConfirmed = false, carbsInput = "", carbsError = null, runHandoff = true,
                    )
                    is LaunchResult.Rejected -> it.copy(summaryGrams = null, extraConfirmed = false, message = result.reason.toMessage())
                }
            }
        }
    }

    // Oba odczyty zawsze (bez skrótu &&), żeby liczba wywołań nie zależała od wartości.
    private fun readPermissions(): Boolean {
        val send = permissions.canSend()
        val receive = permissions.canReceive()
        return send && receive
    }

    /** Aktualny stan liczony od razu ze źródeł (bez opóźnienia `stateIn`). */
    private fun compose(): CommandsUiState =
        local.value.copy(
            number = numbers.state.value.toReadiness(),
            secret = secrets.state.value.toReadiness(),
            commandActive = coordinator.activeSession.value != null,
            limits = limitsStore.state.value,
        )
}

/** Najdłuższy PIN AAPS w polu (decyzja użytkownika, spec 009 P2). */
internal const val PIN_MAX_LENGTH = 12
