package pl.twojanazwa.aapsremote.session

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.TotpCode
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionCodeValidityTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun carbs1(): Command = (Command.Carbs.of(1) as CarbsValidation.Valid).command

    /**
     * Polecenie wysłane w 1111110811000, prośba przetwarzana przy zegarze 1111111111000 (po 300 000 ms).
     * Zwraca wynik, gdy sesja się zakończyła, inaczej null (sesja anulowana).
     */
    private suspend fun TestScope.ageCase(receivedAt: Long): SessionResult? {
        val base = 1111110811000
        val clock = EpochClock { base + currentTime }
        val session = testSession(
            carbs1(), sender, source, SessionTestFixtures.credentials(),
            timeouts = SessionTimeouts(reply = 10.minutes), clock = clock,
        )
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(300_000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, receivedAt))
        runCurrent()
        if (session.state.value.phase != SessionPhase.FINISHED) {
            run.cancel()
            return null
        }
        return run.await()
    }

    @Test
    fun `SESSION-10 prosba w wieku 240000 ms jest przyjeta`() = runTest {
        assertNull(ageCase(1111110871000))
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
    }

    @Test
    fun `SESSION-10 prosba w wieku 240001 ms daje CODE_REQUEST_EXPIRED bez SMS-a z kodem`() = runTest {
        val result = checkNotNull(ageCase(1111110870999))
        assertEquals(
            SessionOutcome.Failed(FailureReason.CODE_REQUEST_EXPIRED, AapsSamples.carbsCodeRequest),
            result.outcome,
        )
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @Test
    fun `SESSION-10 prosba w wieku minus 1 ms daje CODE_REQUEST_EXPIRED bez SMS-a z kodem`() = runTest {
        val result = checkNotNull(ageCase(1111111111001))
        assertEquals(FailureReason.CODE_REQUEST_EXPIRED, (result.outcome as SessionOutcome.Failed).reason)
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @Test
    fun `SESSION-10 zatrzymany zegar - 9999 ms PREPARING_CODE a 10000 ms CODE_PREPARATION_TIMEOUT`() = runTest {
        val stopped = 1111111109000
        val clock = EpochClock { stopped }
        val session = testSession(carbs1(), sender, source, SessionTestFixtures.credentials(), clock = clock)
        val run = async { session.run() }
        runCurrent()
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, stopped))
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(SessionPhase.PREPARING_CODE, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SessionOutcome.Failed(FailureReason.CODE_PREPARATION_TIMEOUT, null), run.await().outcome)
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    private fun TestScope.sessionWithProviderDelay(delayMillis: Long): CommandSession {
        val totp = mockk<UsableTotpProvider>()
        coEvery { totp.awaitUsableCode(any()) } coAnswers {
            delay(delayMillis)
            TotpCode("050471", 1111111110000, 1111111140000)
        }
        return CommandSession(
            command = carbs1(),
            credentials = SessionTestFixtures.credentials(),
            sender = sender,
            incoming = source,
            clock = virtualClock(),
            totp = totp,
            timeSource = testScheduler.timeSource,
        )
    }

    @Test
    fun `SESSION-10 dostawca zwraca kod po 9999 ms - SMS z kodem w chwili 69999`() = runTest {
        val session = sessionWithProviderDelay(9_999)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 60_000))
        runCurrent()
        advanceTimeBy(9_998)
        runCurrent()
        assertTrue(sender.sentCodeCopies.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(69_999, currentTime)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        run.cancel()
    }

    @Test
    fun `SESSION-10 dostawca zwraca kod po 10000 ms - CODE_PREPARATION_TIMEOUT bez SMS-a z kodem`() = runTest {
        val session = sessionWithProviderDelay(10_000)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 60_000))
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(SessionOutcome.Failed(FailureReason.CODE_PREPARATION_TIMEOUT, null), run.await().outcome)
        assertTrue(sender.sentCodeCopies.isEmpty())
    }
}
