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
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.CommandLimitsState
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
    private val profileSelection: ProfileSelection,
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
        // Utrata limitów (nie `Loaded`) zamyka okno „Na pewno”, żeby niewidoczne okno nie trzymało ochrony okna.
        viewModelScope.launch {
            limitsStore.state.collect { limits ->
                if (limits !is CommandLimitsState.Loaded) local.update { it.copy(confirming = null) }
            }
        }
        // Kolektor tylko wyłącza przekazanie; drugi warunek chroni przed spóźnioną wartością null (CMDUI-23).
        viewModelScope.launch {
            currentRun.state.collect { run ->
                if (run == null && currentRun.state.value == null) local.update { it.copy(runHandoff = false) }
            }
        }
        // PROF-14: wybór z wyniku listy otwiera formularz (procent 100) i jest od razu zużywany.
        viewModelScope.launch {
            profileSelection.choice.collect { choice ->
                if (choice == null) return@collect
                local.update {
                    it.copy(profile = ProfileFormState(choice, Command.ProfileSwitch.DEFAULT_PERCENT.toString()))
                }
                profileSelection.clear()
            }
        }
    }

    /** ON_RESUME zakładki i po wyniku prośby o uprawnienia (CMDUI-03). */
    fun refreshPermissions() {
        val granted = readPermissions()
        local.update { it.copy(smsGranted = granted) }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null, messageArgs = emptyList()) }
    }

    fun onBgClicked() = startNoCode(Command.Bg)

    fun onPumpClicked() = startNoCode(Command.Pump)

    /** PROF-13: jak `BG`, bez sekretu i limitów. */
    fun onProfileStatusClicked() = startNoCode(Command.ProfileStatus)

    fun onProfileListClicked() = startNoCode(Command.ProfileList)

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
                    local.update { it.copy(confirming = carbs) }
                } else {
                    openSummary(carbs, extraConfirmed = false)
                }
            }
        }
    }

    fun onQuickValueClicked(grams: Int) {
        val state = compose()
        val limits = state.loadedLimits
        if (!state.canStartCarbs || limits == null) return
        // Idzie przez Command.Carbs.of, więc ponad limit nie otwiera podsumowania; próg „Na pewno” sprawdza dalej
        // launcher (LIM-09). Szybkie wartości są <= progu (LIM-08); gdyby jednak przekraczała, nie ma podsumowania.
        val carbs = (Command.Carbs.of(grams, limits) as? CarbsValidation.Valid)?.command ?: return
        if (carbs.requiresExtraConfirmation(limits)) return
        local.update {
            it.copy(carbsInput = grams.toString(), carbsError = null, confirming = null)
        }
        openSummary(carbs, extraConfirmed = false)
    }

    fun onLargeAmountConfirmed() {
        val carbs = local.value.confirming as? Command.Carbs ?: return
        openSummary(carbs, extraConfirmed = true)
    }

    fun onLargeAmountDismissed() {
        local.update { if (it.confirming is Command.Carbs) it.copy(confirming = null) else it }
    }

    /** BOL-13: zmiana tekstu usuwa błąd. */
    fun onBolusInputChanged(text: String) {
        local.update { it.copy(bolusInput = text, bolusError = null) }
    }

    /** BOL-13, BOL-14: walidacja z bieżącymi limitami; bolus nigdy nie startuje stąd, tylko otwiera okno albo podsumowanie. */
    fun onBolusNextClicked() {
        val state = compose()
        val limits = state.loadedLimits
        if (!state.canStartBolus || limits == null) return
        when (val result = Command.Bolus.parse(local.value.bolusInput, limits)) {
            is BolusValidation.Invalid -> local.update { it.copy(bolusError = result.error) }
            is BolusValidation.Valid -> {
                val bolus = result.command
                if (bolus.requiresExtraConfirmation(limits)) {
                    local.update { it.copy(confirming = bolus) }
                } else {
                    openSummary(bolus, extraConfirmed = false)
                }
            }
        }
    }

    /** `extraConfirmed = true` tylko po „Tak” (BOL-14, LIM-09). */
    fun onBolusLargeConfirmed() {
        val bolus = local.value.confirming as? Command.Bolus ?: return
        openSummary(bolus, extraConfirmed = true)
    }

    fun onBolusLargeDismissed() {
        local.update { if (it.confirming is Command.Bolus) it.copy(confirming = null) else it }
    }

    /** PROF-15: zmiana tekstu usuwa błąd. */
    fun onProfilePercentChanged(text: String) {
        local.update { s -> s.copy(profile = s.profile?.copy(percentText = text, error = null)) }
    }

    /** PROF-15: poprawny procent otwiera podsumowanie z PIN-em, niepoprawny zostawia błąd pod polem. */
    fun onProfileNextClicked() {
        val form = local.value.profile ?: return
        if (!compose().canStartProfile) return
        when (val result = Command.ProfileSwitch.parsePercent(form.choice, form.percentText)) {
            is ProfileSwitchValidation.Invalid ->
                local.update { it.copy(profile = form.copy(error = result.error)) }
            // Jedna aktualizacja: nigdy nie ma chwili bez formularza i bez podsumowania (secureWindow).
            is ProfileSwitchValidation.Valid -> local.update {
                it.copy(
                    profile = null, confirming = null, summary = SummaryState(result.command),
                    pinText = "", extraConfirmed = false,
                )
            }
        }
    }

    /** „Anuluj” formularza: wybór jest już zużyty, więc wystarczy zamknąć formularz. */
    fun onProfileFormCancelled() {
        if (local.value.launching) return
        local.update { it.copy(profile = null) }
    }

    private fun openSummary(command: Command, extraConfirmed: Boolean) {
        local.update {
            it.copy(confirming = null, summary = SummaryState(command), pinText = "", extraConfirmed = extraConfirmed)
        }
    }

    fun onPinChanged(text: String) {
        if (local.value.summary == null || local.value.launching) return
        if (text.length > PIN_MAX_LENGTH || !text.all { it in '0'..'9' }) return
        local.update { it.copy(pinText = text) }
    }

    /** PIN trafia z pola do [AapsPin] i pole jest czyszczone przed `launch` (CMDUI-11). */
    fun onSendClicked() {
        val state = compose()
        val summary = state.summary
        if (summary == null || !state.canSubmitPin) return
        val chars = state.pinText.toCharArray()
        local.update { it.copy(pinText = "") }
        val pin = AapsPin.fromChars(chars)
        val limits = state.loadedLimits
        // Zmiana profilu nie zależy od limitów ilości (procent sprawdzony w „Dalej”).
        if (limits == null && summary.command !is Command.ProfileSwitch) {
            pin.clear()
            local.update { it.copy(summary = null, message = CommandMessage.LIMITS_UNAVAILABLE) }
            return
        }
        // LIM-09: limit zmniejszony po otwarciu podsumowania; ilość nie jest przycinana.
        val command = when (val c = summary.command) {
            is Command.Carbs -> limits?.let { (Command.Carbs.of(c.grams, it) as? CarbsValidation.Valid)?.command }
            is Command.Bolus -> limits?.let { (Command.Bolus.of(c.centiUnits, it) as? BolusValidation.Valid)?.command }
            is Command.ProfileSwitch -> c
            Command.ProfileStatus, Command.ProfileList, Command.Bg, Command.Pump -> null
        }
        if (command == null) {
            pin.clear()
            local.update { it.copy(summary = null, message = CommandMessage.AMOUNT_ABOVE_LIMIT) }
            return
        }
        startCommand(command, pin, state.extraConfirmed, limits?.bolusMinIntervalMinutes ?: 0)
    }

    /** W trakcie uruchamiania „Anuluj” i Wstecz nie ukrywają podsumowania (CMDUI-10, CMDUI-11). */
    fun onSummaryDismissed() {
        if (local.value.launching) return
        local.update { it.copy(summary = null, pinText = "", extraConfirmed = false) }
    }

    /** Okno „Na pewno” -> zamknij; podsumowanie -> jak „Anuluj” (CMDUI-24). */
    fun onBack() {
        if (local.value.launching) return
        when {
            local.value.confirming != null -> local.update { it.copy(confirming = null) }
            local.value.profile != null -> local.update { it.copy(profile = null) }
            else -> onSummaryDismissed()
        }
    }

    /** Aplikacja schodzi z ekranu albo zmiana zakładki: PIN i okna znikają, tekst ilości zostaje (CMDUI-11). */
    fun onStop() {
        // PROF-15: wybór profilu też jest porzucany (lista mogła się zestarzeć).
        profileSelection.clear()
        local.update {
            it.copy(pinText = "", summary = null, confirming = null, extraConfirmed = false, profile = null)
        }
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
    private fun startCommand(command: Command, pin: AapsPin?, extraConfirmed: Boolean = false, intervalMinutes: Int = 0) {
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
                        summary = null, extraConfirmed = false, runHandoff = true,
                    ).let { s ->
                        when (command) {
                            is Command.Bolus -> s.copy(bolusInput = "", bolusError = null)
                            is Command.Carbs -> s.copy(carbsInput = "", carbsError = null)
                            else -> s
                        }
                    }
                    is LaunchResult.Rejected -> it.copy(
                        summary = null,
                        extraConfirmed = false,
                        message = result.reason.toMessage(),
                        // BOL-12: N = odstęp z limitów, M = minutesLeft z odrzucenia.
                        messageArgs = result.minutesLeft?.let { left -> listOf(intervalMinutes, left) }.orEmpty(),
                    )
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
