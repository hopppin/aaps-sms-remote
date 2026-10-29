package pl.twojanazwa.aapsremote.session

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionSendTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun carbs20(): Command = (Command.Carbs.of(20, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

    private fun TestScope.session(
        command: Command,
        credentials: CodeCredentials? = null,
        smsSender: SmsSender = sender,
    ): CommandSession {
        val clock = virtualClock()
        return CommandSession(
            command = command,
            credentials = credentials,
            sender = smsSender,
            incoming = source,
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
    }

    @Test
    fun `SESSION-03 BG wysyla jeden SMS BG i czeka na odpowiedz`() = runTest {
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        assertEquals(listOf("BG"), sender.sentCommands)
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        run.cancel()
    }

    @Test
    fun `SESSION-03 PUMP wysyla jeden SMS PUMP i czeka na odpowiedz`() = runTest {
        val session = session(Command.Pump)
        val run = async { session.run() }
        runCurrent()
        assertEquals(listOf("PUMP"), sender.sentCommands)
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        run.cancel()
    }

    @Test
    fun `SESSION-03 CARBS 20 wysyla jeden SMS i czeka na prosbe o kod`() = runTest {
        val session = session(carbs20(), SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        assertEquals(listOf("CARBS 20"), sender.sentCommands)
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        run.cancel()
    }

    @Test
    fun `SESSION-03 w chwili wywolania portu wysylania port odbioru ma subskrybenta`() = runTest {
        var subscribers = -1
        val probe = SmsSender {
            subscribers = source.subscriptionCount.value
            SendResult.Sent
        }
        val session = session(Command.Bg, smsSender = probe)
        val run = async { session.run() }
        runCurrent()
        assertEquals(1, subscribers)
        run.cancel()
    }

    @Test
    fun `SESSION-03 wysylanie trwa 2000 ms - przez ten czas faza SENDING_COMMAND`() = runTest {
        sender.delayMillis = 2000
        val session = session(Command.Bg)
        val run = async { session.run() }
        session.state.test {
            assertEquals(SessionPhase.NOT_STARTED, awaitItem().phase)
            runCurrent()
            assertEquals(SessionPhase.SENDING_COMMAND, awaitItem().phase)
            advanceTimeBy(1999)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(SessionPhase.AWAITING_REPLY, awaitItem().phase)
            assertEquals(2000, currentTime)
        }
        run.cancel()
    }

    @Test
    fun `SESSION-03 drugie uruchomienie rzuca IllegalStateException bez drugiego SMS-a`() = runTest {
        val session = session(Command.Bg)
        val run = async { session.run() }
        runCurrent()
        val e = assertThrows<IllegalStateException> { session.run() }
        assertEquals("session already started", e.message)
        runCurrent()
        assertEquals(1, sender.sendCount)
        run.cancel()
    }

    @Test
    fun `SESSION-04 blad wysylania CARBS konczy Failed SEND_FAILED w tej samej chwili`() = runTest {
        sender.delayMillis = 700
        sender.commandResult = SendResult.Failed
        val session = session(carbs20(), SessionTestFixtures.credentials())
        val result = async { session.run() }
        runCurrent()
        advanceTimeBy(700)
        runCurrent()
        val finished = result.await()
        assertEquals(700, currentTime)
        assertEquals(SessionOutcome.Failed(FailureReason.SEND_FAILED, null), finished.outcome)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertEquals(SessionTestFixtures.T0 + 700, finished.finishedAtEpochMillis)
        assertEquals(1, sender.sendCount)
    }

    @Test
    fun `SESSION-04 wyjatek portu wysylania daje SEND_FAILED bez tresci wyjatku`() = runTest {
        sender.failure = IllegalStateException("x-sekret")
        val session = session(Command.Bg)
        val result = session.run()
        assertEquals(SessionOutcome.Failed(FailureReason.SEND_FAILED, null), result.outcome)
        assertFalse(result.toString().contains("x-sekret"))
        assertFalse(session.state.value.toString().contains("x-sekret"))
    }

    @Test
    fun `SESSION-04 CancellationException z portu wysylania daje SEND_FAILED a nie zawieszenie`() = runTest {
        sender.failure = kotlinx.coroutines.CancellationException("timeout portu")
        val session = session(Command.Bg)
        val result = session.run()
        assertEquals(SessionOutcome.Failed(FailureReason.SEND_FAILED, null), result.outcome)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
    }

    @Test
    fun `SESSION-04 wiadomosc po bledzie wysylania nie zmienia wyniku`() = runTest {
        sender.commandResult = SendResult.Failed
        val session = session(Command.Bg)
        val result = async { session.run() }
        runCurrent()
        advanceTimeBy(1000)
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0 + 1000))
        runCurrent()
        assertEquals(SessionOutcome.Failed(FailureReason.SEND_FAILED, null), result.await().outcome)
        assertEquals(SessionOutcome.Failed(FailureReason.SEND_FAILED, null), session.state.value.result?.outcome)
        assertTrue(session.state.value.unexpectedMessages.isEmpty())
    }
}
