package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionConfirmationTimeoutTest {

    private val source = FakeIncomingSmsSource()
    private val timeout = SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null)

    @Test
    fun `SESSION-15 limit potwierdzenia konczy sesje dopiero w chwili 420000`() = runTest {
        val session = testSession(carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        advanceTimeBy(419_999 - currentTime)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(timeout, run.await().outcome)
    }

    @Test
    fun `SESSION-15 potwierdzenie w chwili 419999 daje CarbsConfirmed`() = runTest {
        val session = testSession(carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        deliverAt(source, 419_999, AapsSamples.carbsDone)
        assertEquals(SessionOutcome.CarbsConfirmed(1, AapsSamples.carbsDone), run.await().outcome)
    }

    @Test
    fun `SESSION-15 potwierdzenie w chwili 420000 daje Unknown CONFIRMATION_TIMEOUT`() = runTest {
        val session = testSession(carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        deliverAt(source, 420_000, AapsSamples.carbsDone)
        assertEquals(timeout, run.await().outcome)
    }

    @Test
    fun `SESSION-15 wynik portu kodu dokladnie w chwili 420000 daje Unknown CONFIRMATION_TIMEOUT`() = runTest {
        val session = testSession(
            carbsCommand(1), delayedCodeSender(360_000), source, SessionTestFixtures.credentials(),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        advanceTimeBy(360_000)
        runCurrent()
        assertEquals(timeout, run.await().outcome)
    }

    @Test
    fun `SESSION-15 limit potwierdzenia 60000 ms konczy sesje w chwili 120000`() = runTest {
        val session = testSession(
            carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials(),
            timeouts = SessionTimeouts(confirmation = 60_000.milliseconds),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(120_000, currentTime)
        assertEquals(timeout, run.await().outcome)
    }

    @Test
    fun `SESSION-15 port kodu bez wyniku daje Unknown CONFIRMATION_TIMEOUT i zerowa tablice`() = runTest {
        var captured: CharArray? = null
        val session = testSession(
            carbsCommand(1), hangingCodeSender { captured = it }, source, SessionTestFixtures.credentials(),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        advanceTimeBy(420_000 - currentTime)
        runCurrent()
        assertEquals(timeout, run.await().outcome)
        assertTrue(checkNotNull(captured).all { it == '\u0000' })
    }

    @Test
    fun `SESSION-15 limit dziala przy zrodle czasu spoznionym o 1 ms`() = runTest {
        val session = testSession(
            carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials(),
            timeSource = laggingTimeSource(),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        advanceTimeBy(420_000 - currentTime + 5)
        runCurrent()
        assertEquals(timeout, run.await().outcome)
    }
}
