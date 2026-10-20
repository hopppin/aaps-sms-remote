package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class CarbsSessionBeforeCodeTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()
    private val chat = "Czesc, kupisz chleb?"

    private fun carbs(grams: Int): Command = (Command.Carbs.of(grams) as CarbsValidation.Valid).command

    private suspend fun TestScope.emitAt(millis: Long, body: String) {
        advanceTimeBy(millis - currentTime)
        runCurrent()
        source.emit(IncomingSms(body, SessionTestFixtures.T0 + millis))
        runCurrent()
    }

    private fun TestScope.start(grams: Int): Pair<CommandSession, Deferred<SessionResult>> {
        val session = testSession(carbs(grams), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        return session to run
    }

    /** Sesja CARBS 1 w PREPARING_CODE: prośba w 58 000, kod dostępny dopiero w 59 000. */
    private suspend fun TestScope.preparing(): Pair<CommandSession, Deferred<SessionResult>> {
        val (session, run) = start(1)
        emitAt(58_000, AapsSamples.carbsCodeRequest)
        assertEquals(SessionPhase.PREPARING_CODE, session.state.value.phase)
        return session to run
    }

    private fun assertFailed(reason: FailureReason, text: String, result: SessionResult) {
        val outcome = result.outcome as SessionOutcome.Failed
        assertEquals(reason, outcome.reason)
        assertEquals(text, outcome.text)
    }

    @Test
    fun `SESSION-11 prosba o 1 g przy CARBS 10 konczy sesje bez kodu`() = runTest {
        val (_, run) = start(10)
        emitAt(5_000, AapsSamples.carbsCodeRequest)
        assertFailed(FailureReason.AMOUNT_MISMATCH, AapsSamples.carbsCodeRequest, run.await())
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @Test
    fun `SESSION-11 potwierdzenie 1 g przy CARBS 2 konczy sesje niezgodnoscia ilosci`() = runTest {
        val (_, run) = start(2)
        emitAt(5_000, AapsSamples.carbsDone)
        assertFailed(FailureReason.AMOUNT_MISMATCH, AapsSamples.carbsDone, run.await())
    }

    @Test
    fun `SESSION-11 potwierdzenie przy CARBS 1 w AWAITING_CODE_REQUEST jest nieoczekiwane a prosba o kod dziala`() = runTest {
        val (session, run) = start(1)
        emitAt(5_000, AapsSamples.carbsDone)
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        assertEquals(listOf(AapsSamples.carbsDone), session.state.value.unexpectedMessages.map { it.body })
        emitAt(60_000, AapsSamples.carbsCodeRequest)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        run.cancel()
    }

    @Test
    fun `SESSION-11 zly kod w AWAITING_CODE_REQUEST daje UNEXPECTED_REPLY`() = runTest {
        val (_, run) = start(1)
        emitAt(5_000, AapsSamples.wrongCode)
        assertFailed(FailureReason.UNEXPECTED_REPLY, AapsSamples.wrongCode, run.await())
    }

    @Test
    fun `SESSION-11 obca tresc w AWAITING_CODE_REQUEST daje UNRECOGNIZED_REPLY`() = runTest {
        val (_, run) = start(1)
        emitAt(5_000, chat)
        assertFailed(FailureReason.UNRECOGNIZED_REPLY, chat, run.await())
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(SessionPhase::class, names = ["AWAITING_CODE_REQUEST", "PREPARING_CODE"])
    fun `SESSION-11 wynik niemozliwy dla CARBS (BG, PUMP) daje UNRECOGNIZED_REPLY w obu fazach`(phase: SessionPhase) =
        runTest {
            for (body in listOf(AapsSamples.bg, AapsSamples.pump)) {
                val s = FakeSmsSender()
                val src = FakeIncomingSmsSource()
                val session = testSession(carbs(1), s, src, SessionTestFixtures.credentials())
                val run = async { session.run() }
                runCurrent()
                if (phase == SessionPhase.PREPARING_CODE) {
                    val at = currentTime + 58_000
                    advanceTimeBy(58_000)
                    src.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + at))
                    runCurrent()
                }
                assertEquals(phase, session.state.value.phase)
                src.emit(IncomingSms(body, SessionTestFixtures.T0 + currentTime))
                runCurrent()
                assertFailed(FailureReason.UNRECOGNIZED_REPLY, body, run.await())
                assertTrue(s.sentCodeCopies.isEmpty())
            }
        }

    @Test
    fun `SESSION-11 obca tresc w PREPARING_CODE przerywa przygotowanie - kod nie wychodzi`() = runTest {
        val (_, run) = preparing()
        emitAt(58_500, chat)
        assertFailed(FailureReason.UNRECOGNIZED_REPLY, chat, run.await())
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @Test
    fun `SESSION-11 potwierdzenie w PREPARING_CODE jest nieoczekiwane a kod wychodzi w 59000`() = runTest {
        val (session, run) = preparing()
        emitAt(58_500, AapsSamples.carbsDone)
        assertEquals(SessionPhase.PREPARING_CODE, session.state.value.phase)
        assertEquals(1, session.state.value.unexpectedMessages.size)
        assertTrue(sender.sentCodeCopies.isEmpty())
        advanceTimeBy(500)
        runCurrent()
        assertEquals(59_000, currentTime)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        run.cancel()
    }

    @Test
    fun `SESSION-11 druga prosba o kod w PREPARING_CODE daje UNEXPECTED_REPLY bez kodu`() = runTest {
        val (_, run) = preparing()
        emitAt(58_500, AapsSamples.carbsCodeRequest)
        assertFailed(FailureReason.UNEXPECTED_REPLY, AapsSamples.carbsCodeRequest, run.await())
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @Test
    fun `SESSION-11 zly kod w PREPARING_CODE daje UNEXPECTED_REPLY bez kodu`() = runTest {
        val (_, run) = preparing()
        emitAt(58_500, AapsSamples.wrongCode)
        assertFailed(FailureReason.UNEXPECTED_REPLY, AapsSamples.wrongCode, run.await())
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(sender.sentCodeCopies.isEmpty())
    }

    @Test
    fun `SESSION-11 niezgodna ilosc w PREPARING_CODE daje AMOUNT_MISMATCH bez kodu`() = runTest {
        val (_, run) = preparing()
        emitAt(58_500, "Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em")
        assertFailed(
            FailureReason.AMOUNT_MISMATCH,
            "Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em",
            run.await(),
        )
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(sender.sentCodeCopies.isEmpty())
    }
}
