package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandCatalog
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.AapsNumberState
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
 * `recorder` i `currentRun` są w konstruktorze zgodnie z design; ich użycie (historia, publikacja
 * uruchomienia) dochodzi w zadaniu 1c.
 */
@Suppress("UnusedPrivateProperty")
class CommandLauncher @Inject constructor(
    private val coordinator: SessionCoordinator,
    private val numbers: AapsNumberSettingsStore,
    private val permissions: SmsPermissions,
    private val secrets: TotpSecretProvider,
    private val recorder: SessionHistoryRecorder,
    private val currentRun: CurrentRun,
    @CommandRunScope private val scope: CoroutineScope,
) {
    /**
     * [pin] != null dokładnie dla CommandCatalog.definitionOf(command.id).requiresCode. Każdy Rejected i anulowanie
     * korutyny = pin wyczyszczony. Nie rzuca poza CancellationException.
     */
    suspend fun launch(command: Command, pin: AapsPin?): LaunchResult {
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
            val result = try {
                coordinator.start(scope, command, credentials)
            } catch (e: IllegalArgumentException) {
                return LaunchResult.Rejected(LaunchRejection.START_FAILED)
            }
            return when (result) {
                is StartResult.Started -> {
                    started = true
                    LaunchResult.Started
                }
                StartResult.Busy -> LaunchResult.Rejected(LaunchRejection.COMMAND_ACTIVE)
            }
        } finally {
            if (!started) pin?.clear()
        }
    }
}
