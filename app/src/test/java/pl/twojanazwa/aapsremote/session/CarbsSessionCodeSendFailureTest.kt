package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionCodeSendFailureTest {

    private val source = FakeIncomingSmsSource()
    private val failed = SessionOutcome.Unknown(UnknownReason.CODE_SEND_FAILED, null)

    @Test
    fun `SESSION-16 blad wysylania kodu daje Unknown CODE_SEND_FAILED bez ponowienia`() = runTest {
        val credentials = SessionTestFixtures.credentials()
        val sender = FakeSmsSender().apply { codeResult = SendResult.Failed }
        val session = testSession(carbsCommand(1), sender, source, credentials)
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        assertEquals(failed, run.await().outcome)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, sender.sendCount)
        assertTrue(credentials.pin.isCleared)
        assertTrue(sender.codeArrays.single().all { it == '\u0000' })
    }

    @Test
    fun `SESSION-16 wyjatek portu kodu daje Unknown CODE_SEND_FAILED bez tresci wyjatku`() = runTest {
        val sender = FakeSmsSender()
        val throwing = SmsSender { message ->
            if (message is OutgoingSms.CodeReply) throw IllegalStateException("x")
            sender.send(message)
        }
        val session = testSession(carbsCommand(1), throwing, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        val outcome = run.await().outcome
        assertEquals(failed, outcome)
        assertFalse(outcome.toString().contains("x"))
    }

    @Test
    fun `SESSION-16 wiadomosci odlozone w SENDING_CODE sa porzucane przy bledzie wysylania kodu`() = runTest {
        val session = testSession(
            carbsCommand(1), delayedCodeSender(3_000, SendResult.Failed), source, SessionTestFixtures.credentials(),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        deliverAt(source, 62_000, AapsSamples.carbsDone)
        advanceTimeBy(1_000)
        runCurrent()
        val result = run.await()
        assertEquals(failed, result.outcome)
        assertTrue(result.unexpectedMessages.isEmpty())
    }
}
