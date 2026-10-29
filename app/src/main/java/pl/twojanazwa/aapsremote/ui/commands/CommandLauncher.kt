package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandCatalog
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.data.SessionHistoryRecorder
import pl.twojanazwa.aapsremote.security.AapsPin
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.TotpSecretProvider
import pl.twojanazwa.aapsremote.session.CodeCredentials
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.StartResult
import pl.twojanazwa.aapsremote.sms.SmsPermissions
import javax.inject.Inject

/**
 * Sprawdzenia przed startem, odczyt sekretu tuż przed startem i start sesji w zakresie aplikacji
 * (CMDUI-12, CMDUI-13). Bez stanu: pola wyłącznie z zależnościami (CMDUI-15).
 *
 * Po starcie zapisuje sesję do historii (CMDUI-14) i publikuje uruchomienie w `CurrentRun` (CMDUI-16).
 */
class CommandLauncher @Inject constructor(
    private val coordinator: SessionCoordinator,
    private val numbers: AapsNumberSettingsStore,
    private val limitsStore: CommandLimitsStore,
    private val permissions: SmsPermissions,
    private val secrets: TotpSecretProvider,
    private val recorder: SessionHistoryRecorder,
    private val currentRun: CurrentRun,
    @CommandRunScope private val scope: CoroutineScope,
) {
    /**
     * [extraConfirmed] = okno „Na pewno” zatwierdzone (LIM-09). [pin] != null dokładnie dla CommandCatalog.definitionOf(command.id).requiresCode. Każdy Rejected i anulowanie
     * korutyny = pin wyczyszczony. Nie rzuca poza CancellationException (wyjątek ze startu sesji = START_FAILED).
     */
    suspend fun launch(command: Command, pin: AapsPin?, extraConfirmed: Boolean = false): LaunchResult {
        var started = false
        try {
            if ((pin != null) != CommandCatalog.definitionOf(command.id).requiresCode) {
                return LaunchResult.Rejected(LaunchRejection.START_FAILED)
            }
            if (coordinator.activeSession.value != null) {
                return LaunchResult.Rejected(LaunchRejection.COMMAND_ACTIVE)
            }
            when (numbers.state.value) {
                AapsNumberState.Loading -> return LaunchResult.Rejected(LaunchRejection.NUMBER_LOADING)
                AapsNumberState.NotConfigured, AapsNumberState.LoadFailed ->
                    return LaunchResult.Rejected(LaunchRejection.NUMBER_MISSING)
                is AapsNumberState.Configured -> Unit
            }
            if (!permissions.canSend() || !permissions.canReceive()) {
                return LaunchResult.Rejected(LaunchRejection.SMS_PERMISSIONS_MISSING)
            }
            if (command is Command.Carbs) {
                // LIM-09: jedna migawka stanu limitów na całe sprawdzenie; ilość nie jest przycinana.
                val limits = (limitsStore.state.value as? CommandLimitsState.Loaded)?.limits
                    ?: return LaunchResult.Rejected(LaunchRejection.LIMITS_UNAVAILABLE)
                if (command.grams > limits.carbsMaxGrams) {
                    return LaunchResult.Rejected(LaunchRejection.AMOUNT_ABOVE_LIMIT)
                }
                if (!extraConfirmed && command.requiresExtraConfirmation(limits)) {
                    return LaunchResult.Rejected(LaunchRejection.EXTRA_CONFIRMATION_REQUIRED)
                }
            }
            val credentials = if (pin == null) {
                null
            } else {
                when (val loaded = secrets.load()) {
                    is LoadResult.Loaded -> CodeCredentials(pin, loaded.secret)
                    LoadResult.Absent -> return LaunchResult.Rejected(LaunchRejection.SECRET_ABSENT)
                    LoadResult.Invalidated -> return LaunchResult.Rejected(LaunchRejection.SECRET_INVALIDATED)
                    LoadResult.Locked -> return LaunchResult.Rejected(LaunchRejection.SECRET_LOCKED)
                    LoadResult.Unavailable -> return LaunchResult.Rejected(LaunchRejection.SECRET_UNAVAILABLE)
                }
            }
            // Anulowanie po load() (lub w ścieżce bez kodu) nie może wystartować sesji; PIN czyści finally.
            currentCoroutineContext().ensureActive()
            // Odcinek start -> track -> publish jest nierozdzielny i bez zawieszania: sesja, która zdąży się
            // zakończyć po start, nie może po cudzym kolejnym uruchomieniu nadpisać CurrentRun (monitor `this`,
            // bez pola, bo CMDUI-15).
            return synchronized(this) {
                val result = try {
                    coordinator.start(scope, command, credentials)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    return@synchronized LaunchResult.Rejected(LaunchRejection.START_FAILED)
                }
                when (result) {
                    is StartResult.Started -> {
                        started = true
                        recorder.track(result.session.state)
                        currentRun.publish(CurrentRunData(command, result.session.state))
                        LaunchResult.Started
                    }
                    StartResult.Busy -> LaunchResult.Rejected(LaunchRejection.COMMAND_ACTIVE)
                }
            }
        } finally {
            if (!started) pin?.clear()
        }
    }
}
