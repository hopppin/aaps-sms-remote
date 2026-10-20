package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionReplyTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private suspend fun TestScope.reply(command: Command, body: String): SessionResult {
        val clock = virtualClock()
        val session = CommandSession(
            command = command,
            credentials = null,
            sender = sender,
            incoming = source,
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
        val run = async { session.run() }
        runCurrent()
        source.emit(IncomingSms(body, SessionTestFixtures.T0))
        runCurrent()
        return run.await()
    }

    @Test
    fun `SESSION-06 BG odpowiedz bg txt konczy Completed z BgReport 210 i trescia pliku`() = runTest {
        val outcome = reply(Command.Bg, AapsSamples.bg).outcome as SessionOutcome.Completed
        assertEquals(210, (outcome.reply as AapsReply.BgReport).glucoseMgdl)
        assertEquals(AapsSamples.bg, outcome.text)
    }

    @Test
    fun `SESSION-06 PUMP odpowiedz pump txt konczy Completed z PumpStatus i trescia pliku`() = runTest {
        val outcome = reply(Command.Pump, AapsSamples.pump).outcome as SessionOutcome.Completed
        assertTrue(outcome.reply is AapsReply.PumpStatus)
        assertEquals(AapsSamples.pump, outcome.text)
    }

    @Test
    fun `SESSION-06 BG obca tresc konczy Failed UNRECOGNIZED_REPLY z trescia`() = runTest {
        val text = "Czesc, kupisz chleb?"
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, text),
            reply(Command.Bg, text).outcome,
        )
    }

    @Test
    fun `SESSION-06 BG potwierdzenie CARBS z innego numeru konczy Failed UNRECOGNIZED_REPLY`() = runTest {
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.carbsDone),
            reply(Command.Bg, AapsSamples.carbsDone).outcome,
        )
    }

    @Test
    fun `SESSION-06 PUMP odpowiedz bg txt konczy Failed UNRECOGNIZED_REPLY`() = runTest {
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.bg),
            reply(Command.Pump, AapsSamples.bg).outcome,
        )
    }
}
