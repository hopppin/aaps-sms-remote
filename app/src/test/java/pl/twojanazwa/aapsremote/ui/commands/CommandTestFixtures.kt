package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.data.FakeCommandHistory
import pl.twojanazwa.aapsremote.data.SessionHistoryRecorder
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.security.AapsPin
import pl.twojanazwa.aapsremote.session.FakeIncomingSmsSource
import pl.twojanazwa.aapsremote.session.FakeSmsSender
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions
import pl.twojanazwa.aapsremote.ui.settings.number.FakeAapsNumberSettingsStore
import pl.twojanazwa.aapsremote.ui.settings.secret.FakeTotpSecretStore

object CommandTestFixtures {
    /** Chwila z RFC 6238 (T = 1111111051 s). */
    const val T0: Long = 1111111051000

    const val PIN_TEXT = "4321"

    fun pin(): AapsPin = AapsPin.fromChars(PIN_TEXT.toCharArray())

    fun carbs(grams: Int): Command.Carbs = (Command.Carbs.of(grams) as CarbsValidation.Valid).command
}

/** Zestaw atrap i prawdziwy koordynator na czasie wirtualnym [testScope]; launcher składany z nich. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandTestEnv(testScope: TestScope) {
    val clock = EpochClock { CommandTestFixtures.T0 + testScope.testScheduler.currentTime }
    val sender = FakeSmsSender()
    val incoming = FakeIncomingSmsSource()
    val coordinator = SessionCoordinator(
        sender,
        incoming,
        clock,
        UsableTotpProvider(clock),
        testScope.testScheduler.timeSource,
    )
    val numbers = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    val permissions = FakeSmsPermissions()
    val secrets = FakeTotpSecretStore()
    val history = FakeCommandHistory()
    val currentRun = CurrentRun()

    /** Zakres sesji i zapisu historii; czas wirtualny testu. */
    val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
    val recorder = SessionHistoryRecorder(history, appScope)

    fun launcher(coordinator: SessionCoordinator = this.coordinator) =
        CommandLauncher(coordinator, numbers, permissions, secrets, recorder, currentRun, appScope)

    fun setNumber(state: AapsNumberState) = numbers.setState(state)
}
