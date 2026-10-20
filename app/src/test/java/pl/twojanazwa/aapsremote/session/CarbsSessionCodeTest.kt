package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.AapsPin

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionCodeTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun carbs(grams: Int): Command = (Command.Carbs.of(grams) as CarbsValidation.Valid).command

    private suspend fun TestScope.requestAt(millis: Long, body: String = AapsSamples.carbsCodeRequest) {
        advanceTimeBy(millis - currentTime)
        runCurrent()
        source.emit(IncomingSms(body, SessionTestFixtures.T0 + millis))
        runCurrent()
    }

    @Test
    fun `SESSION-08 prosba o kod w chwili 60000 daje dokladnie jeden SMS z kodem`() = runTest {
        val session = testSession(carbs(1), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestAt(60_000)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        run.cancel()
    }

    @Test
    fun `SESSION-08 syntetyczna prosba o 20 g przy CARBS 20 wysyla kod`() = runTest {
        val session = testSession(carbs(20), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestAt(
            60_000,
            "Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em",
        )
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        run.cancel()
    }

    @Test
    fun `SESSION-08 druga prosba o kod w AWAITING_CONFIRMATION nie wysyla drugiego kodu`() = runTest {
        val session = testSession(carbs(1), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestAt(60_000)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        requestAt(61_000)
        assertEquals(1, sender.sentCodeCopies.size)
        // SESSION-13: ponowna prosba o kod po wyslaniu kodu konczy sesje, kod nie jest wysylany ponownie.
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertEquals(SessionOutcome.Unknown(UnknownReason.UNEXPECTED_REPLY, AapsSamples.carbsCodeRequest), run.await().outcome)
    }

    @Test
    fun `SESSION-09 kod wysylany w chwili 60000 a chwila wyslania kodu to 1111111111000`() = runTest {
        val session = testSession(carbs(1), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestAt(60_000)
        assertEquals(60_000, currentTime)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        session.cancel()
        runCurrent()
        assertEquals(1111111111000, run.await().codeSentAtEpochMillis)
    }

    @Test
    fun `SESSION-09 kodowi zostala 1000 ms - sesja czeka w PREPARING_CODE i wysyla w chwili 59000`() = runTest {
        val session = testSession(carbs(1), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestAt(58_000)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(SessionPhase.PREPARING_CODE, session.state.value.phase)
        assertTrue(sender.sentCodeCopies.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(59_000, currentTime)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        run.cancel()
    }

    @Test
    fun `SESSION-09 PIN 987654 daje SMS 050471987654`() = runTest {
        val credentials = CodeCredentials(AapsPin.fromChars("987654".toCharArray()), SessionTestFixtures.secret())
        val session = testSession(carbs(1), sender, source, credentials)
        val run = async { session.run() }
        runCurrent()
        requestAt(60_000)
        assertEquals(listOf("050471987654"), sender.sentCodeCopies)
        assertEquals(12, sender.sentCodeCopies.single().length)
        run.cancel()
    }

    @Test
    fun `SESSION-09 w chwili wysylania PIN jest wyczyszczony a tablica po sesji wyzerowana`() = runTest {
        val credentials = SessionTestFixtures.credentials()
        var captured: CharArray? = null
        var pinClearedAtSend: Boolean? = null
        val capturingSender = SmsSender { message ->
            if (message is OutgoingSms.CodeReply) {
                captured = message.chars
                pinClearedAtSend = credentials.pin.isCleared
            }
            SendResult.Sent
        }
        val session = testSession(carbs(1), capturingSender, source, credentials)
        val run = async { session.run() }
        runCurrent()
        requestAt(60_000)
        assertEquals(true, pinClearedAtSend)
        session.cancel()
        runCurrent()
        run.await()
        val chars = checkNotNull(captured)
        assertEquals(10, chars.size)
        assertTrue(chars.all { it == '\u0000' })
    }

    @Test
    fun `SESSION-09 po wyniku wyslano dla kodu faza to AWAITING_CONFIRMATION`() = runTest {
        val session = testSession(carbs(1), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestAt(60_000)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        run.cancel()
    }
}
