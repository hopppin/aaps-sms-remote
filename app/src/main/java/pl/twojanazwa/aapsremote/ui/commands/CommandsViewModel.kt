package pl.twojanazwa.aapsremote.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
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
    private val numbers: AapsNumberSettingsStore,
    private val secrets: TotpSecretStore,
    private val permissions: SmsPermissions,
) : ViewModel() {
    /** Część stanu własna ViewModelu (początkowy odczyt uprawnień, CMDUI-03); gotowość dokłada `combine`. */
    private val local = MutableStateFlow(CommandsUiState(smsGranted = readPermissions()))

    val uiState: StateFlow<CommandsUiState> = combine(
        numbers.state,
        secrets.state,
        coordinator.activeSession,
        local,
    ) { number, secret, session, own ->
        own.copy(
            number = number.toReadiness(),
            secret = secret.toReadiness(),
            commandActive = session != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, compose())

    init {
        viewModelScope.launch { secrets.refreshState() }
    }

    /** ON_RESUME zakładki i po wyniku prośby o uprawnienia (CMDUI-03). */
    fun refreshPermissions() {
        val granted = readPermissions()
        local.update { it.copy(smsGranted = granted) }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    fun onBgClicked() = startCommand(Command.Bg)

    fun onPumpClicked() = startCommand(Command.Pump)

    /**
     * Procedura wspólna (design 009): `launching` i `activeSession` sprawdzane synchronicznie na głównym wątku,
     * więc podwójne dotknięcie nie woła launchera drugi raz. Sesję uruchamia launcher w zakresie aplikacji,
     * nie `viewModelScope` (CMDUI-13).
     */
    private fun startCommand(command: Command) {
        if (local.value.launching) return
        if (coordinator.activeSession.value != null) {
            local.update { it.copy(message = CommandMessage.COMMAND_ACTIVE) }
            return
        }
        if (!compose().canSendNoCode) return
        local.update { it.copy(launching = true) }
        viewModelScope.launch {
            val result = try {
                launcher.launch(command, null)
            } finally {
                local.update { it.copy(launching = false) }
            }
            if (result is LaunchResult.Rejected) {
                local.update { it.copy(message = result.reason.toMessage()) }
            }
        }
    }

    private fun readPermissions(): Boolean = permissions.canSend() && permissions.canReceive()

    /** Aktualny stan liczony od razu ze źródeł (bez opóźnienia `stateIn`). */
    private fun compose(): CommandsUiState =
        local.value.copy(
            number = numbers.state.value.toReadiness(),
            secret = secrets.state.value.toReadiness(),
            commandActive = coordinator.activeSession.value != null,
        )
}
