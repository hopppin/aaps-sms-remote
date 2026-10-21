package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.StartResult
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.session.requestCodeAt

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLauncherStartTest {

    @Test
    fun `CMDUI-13 CARBS 1 z PIN 4321 startuje wysyla polecenie i kod i konczy sie CarbsConfirmed`() = runTest {
        val env = CommandTestEnv(this)
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val result = env.launcher().launch(CommandTestFixtures.carbs(1), CommandTestFixtures.pin())
        assertEquals(LaunchResult.Started, result)
        val session = checkNotNull(env.coordinator.activeSession.value)
        runCurrent()
        assertEquals(listOf("CARBS 1"), env.sender.sentCommands)

        requestCodeAt(env.incoming)
        assertEquals(listOf("0504714321"), env.sender.sentCodeCopies)
        deliverAt(env.incoming, 120_000, AapsSamples.carbsDone)

        val state = session.state.value
        assertEquals(SessionPhase.FINISHED, state.phase)
        assertEquals(SessionOutcome.CarbsConfirmed(1, AapsSamples.carbsDone), state.result?.outcome)
    }

    @Test
    fun `CMDUI-13 BG bez PIN-u startuje sesje i wysyla polecenie`() = runTest {
        val env = CommandTestEnv(this)
        assertEquals(LaunchResult.Started, env.launcher().launch(Command.Bg, null))
        assertNotNull(env.coordinator.activeSession.value)
        runCurrent()
        assertEquals(listOf("BG"), env.sender.sentCommands)
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }

    @Test
    fun `CMDUI-13 Busy z koordynatora daje COMMAND_ACTIVE i wyczyszczony PIN`() = runTest {
        val env = CommandTestEnv(this)
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val coordinator = mockk<SessionCoordinator>()
        every { coordinator.activeSession } returns MutableStateFlow(null)
        every { coordinator.start(any(), any(), any(), any()) } returns StartResult.Busy
        val pin = CommandTestFixtures.pin()

        val result = env.launcher(coordinator).launch(CommandTestFixtures.carbs(20), pin)

        assertEquals(LaunchResult.Rejected(LaunchRejection.COMMAND_ACTIVE), result)
        assertTrue(pin.isCleared)
        verify(exactly = 1) { coordinator.start(any(), any(), any(), any()) }
    }

    @Test
    fun `CMDUI-13 niepoprawny zestaw polecenia i PIN-u daje START_FAILED bez wyjatku i bez odczytu sekretu`() = runTest {
        val env = CommandTestEnv(this)
        val launcher = env.launcher()
        val bgPin = CommandTestFixtures.pin()
        assertEquals(LaunchResult.Rejected(LaunchRejection.START_FAILED), launcher.launch(Command.Bg, bgPin))
        assertTrue(bgPin.isCleared)
        assertEquals(
            LaunchResult.Rejected(LaunchRejection.START_FAILED),
            launcher.launch(CommandTestFixtures.carbs(20), null),
        )
        assertEquals(0, env.secrets.loadCount)
        assertEquals(0, env.sender.sendCount)
        assertEquals(null, env.coordinator.activeSession.value)
    }
}
