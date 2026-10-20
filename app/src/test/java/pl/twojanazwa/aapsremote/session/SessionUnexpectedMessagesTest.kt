package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class SessionUnexpectedMessagesTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()
    private val t0 = SessionTestFixtures.T0

    @Test
    fun `SESSION-18 dwie wiadomosci nieoczekiwane w kolejnosci odbioru a faza bez zmian`() = runTest {
        val command = (Command.Carbs.of(1) as CarbsValidation.Valid).command
        val session = testSession(command, sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        for (at in listOf(10_000L, 20_000L)) {
            advanceTimeBy(at - currentTime)
            runCurrent()
            source.emit(IncomingSms(AapsSamples.carbsDone, t0 + at))
            runCurrent()
        }
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        assertEquals(listOf(t0 + 10_000, t0 + 20_000), session.state.value.unexpectedMessages.map { it.receivedAtEpochMillis })
        assertEquals(0, sender.sentCodeCopies.size)

        advanceTimeBy(180_000 - currentTime)
        runCurrent()
        val result = run.await()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST), result.outcome)
        assertEquals(listOf(t0 + 10_000, t0 + 20_000), result.unexpectedMessages.map { it.receivedAtEpochMillis })
    }
}
