package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionConfirmationTest {

    private val source = FakeIncomingSmsSource()

    @Test
    fun `SESSION-12 potwierdzenie 1 g w chwili 120000 po kodzie z chwili 60000 daje CarbsConfirmed`() = runTest {
        val session = testSession(carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        deliverAt(source, 120_000, AapsSamples.carbsDone)
        val result = run.await()
        assertEquals(SessionOutcome.CarbsConfirmed(1, AapsSamples.carbsDone), result.outcome)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
    }

    @Test
    fun `SESSION-12 potwierdzenie sprzed chwili wyslania kodu trafia do nieoczekiwanych`() = runTest {
        val session = testSession(
            carbsCommand(1), delayedCodeSender(3_000), source, SessionTestFixtures.credentials(),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        assertEquals(SessionPhase.SENDING_CODE, session.state.value.phase)
        deliverAt(source, 62_000, AapsSamples.carbsDone)
        assertEquals(SessionPhase.SENDING_CODE, session.state.value.phase)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        assertEquals(listOf(AapsSamples.carbsDone), session.state.value.unexpectedMessages.map { it.body })
        session.cancel()
        runCurrent()
        assertEquals(1_111_111_114_000L, run.await().codeSentAtEpochMillis)
    }

    @Test
    fun `SESSION-12 potwierdzenie odebrane od chwili wyslania kodu i odlozone jest przetworzone po wyniku`() = runTest {
        val session = testSession(
            carbsCommand(1), delayedCodeSender(3_000), source, SessionTestFixtures.credentials(),
        )
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        // Znacznik odbioru = chwila wyslania kodu (63 000), ale dostarczone w trakcie wysylania.
        source.emit(IncomingSms(AapsSamples.carbsDone, SessionTestFixtures.T0 + 63_000))
        runCurrent()
        assertEquals(SessionPhase.SENDING_CODE, session.state.value.phase)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(SessionOutcome.CarbsConfirmed(1, AapsSamples.carbsDone), run.await().outcome)
    }

    @Test
    fun `SESSION-12 potwierdzenie w AWAITING_CODE_REQUEST nie jest CarbsConfirmed`() = runTest {
        val session = testSession(carbsCommand(1), FakeSmsSender(), source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        deliverAt(source, 30_000, AapsSamples.carbsDone)
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        session.cancel()
        runCurrent()
        assertFalse(run.await().outcome is SessionOutcome.CarbsConfirmed)
    }
}
