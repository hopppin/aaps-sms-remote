package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionReplyTimeoutTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun carbs1(): Command = (Command.Carbs.of(1) as CarbsValidation.Valid).command

    @Test
    fun `SESSION-07 BG bez odpowiedzi - 179999 ms AWAITING_REPLY a 180000 ms TimedOut`() = runTest {
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(179_999)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY), run.await().outcome)
    }

    @Test
    fun `SESSION-07 delay budzi sie przed terminem wedlug znacznika - licznik czeka i sesja konczy sie TimedOut`() =
        runTest {
            val session = testSession(Command.Bg, sender, source, timeSource = laggingTimeSource())
            val run = async { session.run() }
            runCurrent()
            advanceTimeBy(180_000)
            runCurrent()
            // Znacznik jeszcze nie uznaje terminu za minięty (spóźnione źródło), więc sesja czeka.
            assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY), run.await().outcome)
        }

    @Test
    fun `SESSION-07 BG bg txt w chwili 179999konczy sie Completed`() = runTest {
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(179_999)
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 + 179_999))
        runCurrent()
        assertTrue(run.await().outcome is SessionOutcome.Completed)
    }

    @Test
    fun `SESSION-07 BG bg txt w chwili 180000 daje TimedOut a nie Completed`() = runTest {
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 + 180_000))
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY), run.await().outcome)
    }

    @Test
    fun `SESSION-07 CARBS 1 prosba o kod w chwili 180000 daje TimedOut AWAITING_CODE_REQUEST bez kodu`() = runTest {
        val session = testSession(carbs1(), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 180_000))
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST), run.await().outcome)
        assertEquals(0, sender.sentCodeCopies.size)
        assertEquals(1, sender.sendCount)
    }

    @Test
    fun `SESSION-07 port wysylania zwraca Sent po dokladnie 180000 ms - TimedOut SENDING_COMMAND`() = runTest {
        sender.delayMillis = 180_000
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        val result = run.await()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.SENDING_COMMAND), result.outcome)
        assertNull(result.commandSentAtEpochMillis)
    }

    @Test
    fun `SESSION-07 CARBS 1 bez odpowiedzi po 180000 ms TimedOut AWAITING_CODE_REQUEST bez kodu`() = runTest {
        val session = testSession(carbs1(), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST), run.await().outcome)
        assertEquals(0, sender.sentCodeCopies.size)
    }

    @Test
    fun `SESSION-07 limit odpowiedzi 30000 ms - PUMP bez odpowiedzi TimedOut AWAITING_REPLY`() = runTest {
        val session = testSession(Command.Pump, sender, source, timeouts = SessionTimeouts(reply = 30.seconds))
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY), run.await().outcome)
    }

    @Test
    fun `SESSION-07 port wysylania nigdy nie zwraca wyniku - po 180000 ms TimedOut SENDING_COMMAND`() = runTest {
        sender.suspendForever = true
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.SENDING_COMMAND), run.await().outcome)
    }

    @Test
    fun `SESSION-07 Sent po 2000 ms - limit liczony od rozpoczecia wysylania a nie od Sent`() = runTest {
        sender.delayMillis = 2000
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(179_999)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_REPLY), run.await().outcome)
    }
}
