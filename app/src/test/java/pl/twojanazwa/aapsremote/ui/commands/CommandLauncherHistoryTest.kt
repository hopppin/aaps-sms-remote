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
import pl.twojanazwa.aapsremote.security.AapsPin
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

        val rejections = mutableListOf<LaunchRejection>()
        suspend fun launchRejected(command: Command, pin: AapsPin?) {
            val result = launcher.launch(command, pin)
            rejections += (result as LaunchResult.Rejected).reason
        }

        launchRejected(Command.Bg, CommandTestFixtures.pin())
        env.setNumber(AapsNumberState.Loading)
        launchRejected(Command.Bg, null)
        env.setNumber(AapsNumberState.NotConfigured)
        launchRejected(Command.Bg, null)
        env.setNumber(AapsNumberState.LoadFailed)
        launchRejected(Command.Bg, null)
        env.numbers.setConfigured("+447700900123")
        env.permissions.receive = false
        launchRejected(Command.Bg, null)
        env.permissions.receive = true
        env.secrets.loadResults.addAll(
            listOf(LoadResult.Absent, LoadResult.Invalidated, LoadResult.Locked, LoadResult.Unavailable),
        )
        repeat(4) { launchRejected(CommandTestFixtures.carbs(20), CommandTestFixtures.pin()) }

        assertEquals(
            listOf(
                LaunchRejection.START_FAILED,
                LaunchRejection.NUMBER_LOADING,
                LaunchRejection.NUMBER_MISSING,
                LaunchRejection.NUMBER_MISSING,
                LaunchRejection.SMS_PERMISSIONS_MISSING,
                LaunchRejection.SECRET_ABSENT,
                LaunchRejection.SECRET_INVALIDATED,
                LaunchRejection.SECRET_LOCKED,
                LaunchRejection.SECRET_UNAVAILABLE,
            ),
            rejections,
        )

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
