package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.SessionHistoryRecorder
import pl.twojanazwa.aapsremote.session.CommandSession
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionState
import pl.twojanazwa.aapsremote.session.StartResult
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CommandLauncherOrderingTest {

    private fun sessionWithState(): Pair<CommandSession, MutableStateFlow<SessionState>> {
        val state = MutableStateFlow(SessionState(SessionPhase.AWAITING_REPLY, emptyList(), null))
        val session = mockk<CommandSession>()
        every { session.state } returns state
        return session to state
    }

    @Test
    fun `CMDUI-16 drugie uruchomienie nie wchodzi miedzy start a publish pierwszego`() = runTest {
        val env = CommandTestEnv(this)
        val (first, firstState) = sessionWithState()
        val (second, secondState) = sessionWithState()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val coordinator = mockk<SessionCoordinator>()
        every { coordinator.activeSession } returns MutableStateFlow(null)
        val sessions = ArrayDeque(listOf(first, second))
        every { coordinator.start(any(), any(), any(), any()) } answers {
            events += "start"
            StartResult.Started(synchronized(sessions) { sessions.removeFirst() })
        }
        val inFirstTrack = CountDownLatch(1)
        val releaseFirstTrack = CountDownLatch(1)
        val recorder = mockk<SessionHistoryRecorder>()
        every { recorder.track(any()) } answers {
            events += "track"
            if (firstArg<Any>() === firstState) {
                inFirstTrack.countDown()
                assertTrue(releaseFirstTrack.await(10, TimeUnit.SECONDS))
            }
        }
        val launcher = env.launcher(coordinator, recorder)

        val a = thread { runBlocking { launcher.launch(Command.Bg, null) } }
        assertTrue(inFirstTrack.await(10, TimeUnit.SECONDS))
        val b = thread { runBlocking { launcher.launch(Command.Pump, null) } }
        // Drugie uruchomienie czeka przed start, dopóki pierwsze nie opublikuje.
        b.join(300)
        assertEquals(listOf("start", "track"), synchronized(events) { events.toList() })
        assertEquals(null, env.currentRun.state.value)

        releaseFirstTrack.countDown()
        a.join(10_000)
        b.join(10_000)

        assertEquals(listOf("start", "track", "start", "track"), synchronized(events) { events.toList() })
        // Ostatnie publikuje drugie uruchomienie, nie pierwsze.
        val published = checkNotNull(env.currentRun.state.value)
        assertEquals(Command.Pump, published.command)
        assertSame(secondState, published.state)
    }
}
