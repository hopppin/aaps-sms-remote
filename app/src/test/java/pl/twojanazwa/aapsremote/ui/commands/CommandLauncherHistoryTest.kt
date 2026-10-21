package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.data.SessionHistoryRecorder
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.StartResult
import pl.twojanazwa.aapsremote.session.deliverAt

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLauncherHistoryTest {

    @Test
    fun `CMDUI-14 po Started track wywolany raz ze stanem sesji z activeSession`() = runTest {
        val env = CommandTestEnv(this)
        val recorder = mockk<SessionHistoryRecorder>(relaxed = true)

        assertEquals(LaunchResult.Started, env.launcher(recorder = recorder).launch(Command.Bg, null))

        val session = checkNotNull(env.coordinator.activeSession.value)
        verify(exactly = 1) { recorder.track(any()) }
        // Wywołanie już zaszło przed powrotem z launch; weryfikacja referencji stanu.
        verify(exactly = 1) { recorder.track(match { it === session.state }) }
        session.cancel()
        runCurrent()
    }

    @Test
    fun `CMDUI-14 przy odrzuceniach track nie jest wywolywany`() = runTest {
        val env = CommandTestEnv(this)
        val recorder = mockk<SessionHistoryRecorder>(relaxed = true)
        val launcher = env.launcher(recorder = recorder)

        // START_FAILED
        launcher.launch(Command.Bg, CommandTestFixtures.pin())
        // NUMBER_*
        env.setNumber(AapsNumberState.Loading)
        launcher.launch(Command.Bg, null)
        env.setNumber(AapsNumberState.NotConfigured)
        launcher.launch(Command.Bg, null)
        env.setNumber(AapsNumberState.LoadFailed)
        launcher.launch(Command.Bg, null)
        env.setNumber(AapsNumberState.Loading)
        env.numbers.setConfigured("+447700900123")
        // SMS_PERMISSIONS_MISSING
        env.permissions.receive = false
        launcher.launch(Command.Bg, null)
        env.permissions.receive = true
        // SECRET_*
        env.secrets.loadResults.addAll(
            listOf(LoadResult.Absent, LoadResult.Invalidated, LoadResult.Locked, LoadResult.Unavailable),
        )
        repeat(4) { launcher.launch(CommandTestFixtures.carbs(20), CommandTestFixtures.pin()) }

        verify(exactly = 0) { recorder.track(any()) }
    }

    @Test
    fun `CMDUI-14 Busy z koordynatora i COMMAND_ACTIVE nie wywoluja track`() = runTest {
        val env = CommandTestEnv(this)
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val recorder = mockk<SessionHistoryRecorder>(relaxed = true)
        val coordinator = mockk<SessionCoordinator>()
        every { coordinator.activeSession } returns MutableStateFlow(null)
        every { coordinator.start(any(), any(), any(), any()) } returns StartResult.Busy

        env.launcher(coordinator, recorder).launch(CommandTestFixtures.carbs(20), CommandTestFixtures.pin())

        verify(exactly = 0) { recorder.track(any()) }
    }

    @Test
    fun `CMDUI-14 prawdziwy rejestrator zapisuje dokladnie jeden wpis dla BG zakonczonego Completed`() = runTest {
        val env = CommandTestEnv(this)

        assertEquals(LaunchResult.Started, env.launcher().launch(Command.Bg, null))
        runCurrent()
        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        advanceUntilIdle()

        val recorded = env.history.recorded.single()
        assertTrue(recorded.outcome is SessionOutcome.Completed)
        assertEquals(Command.Bg, recorded.command)
        assertSame(null, env.coordinator.activeSession.value)
    }
}
