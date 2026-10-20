package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionAfterCodeTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private suspend fun TestScope.outcomeAfter(grams: Int, body: String): SessionOutcome {
        val session = testSession(carbsCommand(grams), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        // Prośba o kod dla tej samej ilości, więc CARBS 2 dostaje kod; potem AAPS potwierdza 1 g.
        deliverAt(source, 60_000, AapsSamples.carbsCodeRequest.replace("1g", "${grams}g"))
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        deliverAt(source, 70_000, body)
        return run.await().outcome
    }

    @Test
    fun `SESSION-13 zly kod daje Failed WRONG_CODE z trescia`() = runTest {
        assertEquals(
            SessionOutcome.Failed(FailureReason.WRONG_CODE, AapsSamples.wrongCode),
            outcomeAfter(1, AapsSamples.wrongCode),
        )
    }

    @Test
    fun `SESSION-13 nierozpoznana tresc daje Unknown UNRECOGNIZED_REPLY z trescia`() = runTest {
        assertEquals(
            SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "Czesc, kupisz chleb?"),
            outcomeAfter(1, "Czesc, kupisz chleb?"),
        )
    }

    @Test
    fun `SESSION-13 wynik niemozliwy dla CARBS daje Unknown UNRECOGNIZED_REPLY`() = runTest {
        assertEquals(
            SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, AapsSamples.bg),
            outcomeAfter(1, AapsSamples.bg),
        )
    }

    @Test
    fun `SESSION-13 potwierdzenie innej ilosci daje Unknown AMOUNT_MISMATCH`() = runTest {
        assertEquals(
            SessionOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH, AapsSamples.carbsDone),
            outcomeAfter(2, AapsSamples.carbsDone),
        )
    }

    @Test
    fun `SESSION-13 ponowna prosba o kod daje Unknown UNEXPECTED_REPLY i nie wysyla drugiego kodu`() = runTest {
        assertEquals(
            SessionOutcome.Unknown(UnknownReason.UNEXPECTED_REPLY, AapsSamples.carbsCodeRequest),
            outcomeAfter(1, AapsSamples.carbsCodeRequest),
        )
        assertEquals(1, sender.sentCodeCopies.size)
        assertEquals(2, sender.sendCount)
    }
}
