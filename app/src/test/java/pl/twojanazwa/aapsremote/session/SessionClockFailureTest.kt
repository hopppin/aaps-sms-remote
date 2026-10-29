package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionClockFailureTest {

    @Test
    fun `SESSION-19 zegar rzuca przy kazdym odczycie - BG konczy Failed INTERNAL_ERROR bez SMS-a`() = runTest {
        val clock = EpochClock { throw IllegalStateException("zegar") }
        val sender = FakeSmsSender()
        val session = CommandSession(
            command = Command.Bg,
            credentials = null,
            sender = sender,
            incoming = FakeIncomingSmsSource(),
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
        val result = session.run()
        assertEquals(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null), result.outcome)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertEquals(0, sender.sendCount)
        assertNull(result.startedAtEpochMillis)
        assertNull(result.finishedAtEpochMillis)
    }

    @Test
    fun `SESSION-19 zegar rzuca od chwili 60000 - prosba o kod daje Failed INTERNAL_ERROR bez kodu`() = runTest {
        val sender = FakeSmsSender()
        val source = FakeIncomingSmsSource()
        val credentials = SessionTestFixtures.credentials()
        val clock = EpochClock {
            if (testScheduler.currentTime < 60_000) {
                SessionTestFixtures.T0 + testScheduler.currentTime
            } else {
                throw IllegalStateException("zegar")
            }
        }
        val session = CommandSession(
            command = (Command.Carbs.of(1, CommandLimits.DEFAULT) as pl.twojanazwa.aapsremote.commands.CarbsValidation.Valid).command,
            credentials = credentials,
            sender = sender,
            incoming = source,
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        source.emit(IncomingSms(pl.twojanazwa.aapsremote.parser.AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 60_000))
        runCurrent()
        val result = run.await()
        assertEquals(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null), result.outcome)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertTrue(credentials.pin.isCleared)
        assertTrue(sender.sentCodeCopies.isEmpty())
        assertEquals(SessionTestFixtures.T0, result.finishedAtEpochMillis)
    }

    @Test
    fun `SESSION-19 zegar rzuca od 60001 po wyslaniu kodu - potwierdzenie w 120000 daje CarbsConfirmed i chwile 60000`() =
        runTest {
            val sender = FakeSmsSender()
            val source = FakeIncomingSmsSource()
            val clock = EpochClock {
                if (testScheduler.currentTime > 60_000) {
                    throw IllegalStateException("zegar")
                }
                SessionTestFixtures.T0 + testScheduler.currentTime
            }
            val session = testSession(carbsCommand(1), sender, source, SessionTestFixtures.credentials(), clock = clock)
            val run = async { session.run() }
            runCurrent()
            requestCodeAt(source, 60_000)
            assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
            deliverAt(source, 120_000, AapsSamples.carbsDone)
            val result = run.await()
            assertEquals(SessionOutcome.CarbsConfirmed(1, AapsSamples.carbsDone), result.outcome)
            assertEquals(SessionPhase.FINISHED, session.state.value.phase)
            assertEquals(SessionTestFixtures.T0 + 60_000, result.finishedAtEpochMillis)
        }

    @Test
    fun `SESSION-19 zegar rzuca przy odczycie chwili wysylki - Failed INTERNAL_ERROR i ostatni poprawny odczyt`() =
        runTest {
            var reads = 0
            val clock = EpochClock {
                if (reads++ == 0) SessionTestFixtures.T0 else throw IllegalStateException("zegar")
            }
            val session = CommandSession(
                command = Command.Bg,
                credentials = null,
                sender = FakeSmsSender(),
                incoming = FakeIncomingSmsSource(),
                clock = clock,
                totp = UsableTotpProvider(clock),
                timeSource = testTimeSource,
            )
            val result = session.run()
            assertEquals(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null), result.outcome)
            assertEquals(SessionPhase.FINISHED, session.state.value.phase)
            assertEquals(SessionTestFixtures.T0, result.startedAtEpochMillis)
            assertNull(result.commandSentAtEpochMillis)
            assertEquals(SessionTestFixtures.T0, result.finishedAtEpochMillis)
        }
}
