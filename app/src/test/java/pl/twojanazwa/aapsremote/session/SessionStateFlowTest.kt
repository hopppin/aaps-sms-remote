package pl.twojanazwa.aapsremote.session

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateFlowTest {

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
    fun `SESSION-21 BG fazy kolejno NOT_STARTED SENDING_COMMAND AWAITING_REPLY FINISHED`() = runTest {
        sender.delayMillis = 1000
        val session = session(Command.Bg)
        val run = async { session.run() }
        session.state.test {
            assertEquals(SessionPhase.NOT_STARTED, awaitItem().phase)
            runCurrent()
            assertEquals(SessionPhase.SENDING_COMMAND, awaitItem().phase)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(SessionPhase.AWAITING_REPLY, awaitItem().phase)
            advanceTimeBy(4000)
            source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 + 5000))
            runCurrent()
            assertEquals(SessionPhase.FINISHED, awaitItem().phase)
            expectNoEvents()
        }
        run.await()
    }

    @Test
    fun `SESSION-03 chwila wyslania polecenia to T0 plus 2000 w wyniku`() = runTest {
        sender.delayMillis = 2000
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(2000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 + 2000))
        runCurrent()
        assertEquals(SessionTestFixtures.T0 + 2000, run.await().commandSentAtEpochMillis)
    }

    @Test
    fun `SESSION-21 BG zakonczone TimedOut ma pusta chwile wysylki kodu`() = runTest {
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        val result = run.await()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY), result.outcome)
        assertEquals(null, result.codeSentAtEpochMillis)
    }
}
