package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionIncomingTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun TestScope.session(command: Command): CommandSession {
        val clock = virtualClock()
        return CommandSession(
            command = command,
            credentials = null,
            sender = sender,
            incoming = source,
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
    }

    @Test
    fun `SESSION-05 wiadomosc w trakcie wysylania jest ignorowana jako starsza od chwili wysylki`() = runTest {
        sender.delayMillis = 2000
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(1000)
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 + 1000))
        runCurrent()
        assertEquals(SessionPhase.SENDING_COMMAND, session.state.value.phase)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        assertTrue(session.state.value.unexpectedMessages.isEmpty())
        run.cancel()
    }

    @Test
    fun `SESSION-05 wiadomosc o T0 minus 1 jest ignorowana a o T0 konczy sesje`() = runTest {
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 - 1))
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        assertTrue(session.state.value.unexpectedMessages.isEmpty())
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0))
        runCurrent()
        assertTrue(run.await().outcome is SessionOutcome.Completed)
    }

    @Test
    fun `SESSION-05 wyjatek strumienia odbioru nie konczy sesji - faza pozostaje AWAITING_REPLY`() = runTest {
        source.failure = IllegalStateException("blad strumienia")
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        assertTrue(run.isActive)
        run.cancel()
    }
}
