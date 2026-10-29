package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.parser.AapsSamples
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCancelTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun carbs1(): Command = (Command.Carbs.of(1, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

    @Test
    fun `SESSION-17 CARBS 1 anulowane w AWAITING_CODE_REQUEST ignoruje pozniejsza prosbe o kod`() = runTest {
        val credentials = SessionTestFixtures.credentials()
        val session = testSession(carbs1(), sender, source, credentials)
        val run = async { session.run() }
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        advanceTimeBy(30_000)
        session.cancel()
        runCurrent()
        assertEquals(SessionOutcome.Cancelled, run.await().outcome)
        advanceTimeBy(30_000)
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 60_000))
        runCurrent()
        assertTrue(credentials.pin.isCleared)
        assertEquals(listOf("CARBS 1"), sender.sentCommands)
        assertEquals(1, sender.sendCount)
    }

    @Test
    fun `SESSION-17 CARBS 1 anulowane w PREPARING_CODE konczy sie Cancelled bez kodu`() = runTest {
        val credentials = SessionTestFixtures.credentials()
        val session = testSession(carbs1(), sender, source, credentials)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(58_000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 58_000))
        runCurrent()
        assertEquals(SessionPhase.PREPARING_CODE, session.state.value.phase)
        advanceTimeBy(500)
        session.cancel()
        runCurrent()
        assertEquals(SessionOutcome.Cancelled, run.await().outcome)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(sender.sentCodeCopies.isEmpty())
        assertTrue(credentials.pin.isCleared)
    }

    @Test
    fun `SESSION-17 BG anulowane w AWAITING_REPLY konczy sie Cancelled`() = runTest {
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        assertEquals(SessionPhase.AWAITING_REPLY, session.state.value.phase)
        session.cancel()
        runCurrent()
        assertEquals(SessionOutcome.Cancelled, run.await().outcome)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
    }

    @Test
    fun `SESSION-17 anulowanie w NOT_STARTED konczy od razu a run zwraca Cancelled bez SMS-a i subskrypcji`() =
        runTest {
            val credentials = SessionTestFixtures.credentials()
            val session = testSession(carbs1(), sender, source, credentials)
            session.cancel()
            assertEquals(SessionPhase.FINISHED, session.state.value.phase)
            assertEquals(SessionOutcome.Cancelled, session.state.value.result?.outcome)
            assertTrue(credentials.pin.isCleared)
            val result = session.run()
            assertEquals(SessionOutcome.Cancelled, result.outcome)
            assertEquals(0, sender.sendCount)
            assertEquals(0, source.subscriptionCount.value)
            // Wynik można odebrać tylko raz.
            assertThrows(IllegalStateException::class.java) { runBlocking { session.run() } }
        }

    @Test
    fun `SESSION-17 cancel wstrzymane po zmianie cyklu zycia - run czeka na zakonczenie i zwraca Cancelled`() =
        runTest {
            val credentials = SessionTestFixtures.credentials()
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val base = virtualClock()
            val blockingClock = EpochClock {
                entered.complete(Unit)
                release.await()
                base.nowEpochMillis()
            }
            val session = testSession(carbs1(), sender, source, credentials, clock = blockingClock)
            val canceller = Thread { session.cancel() }
            canceller.start()
            // cancel() jest w finish(): PIN już wyczyszczony, FINISHED jeszcze nieopublikowane.
            withContext(Dispatchers.Default) { entered.await() }
            val run = async(Dispatchers.Default) { session.run() }
            withContext(Dispatchers.Default) { delay(200) }
            assertTrue(run.isActive)
            assertTrue(session.state.value.phase != SessionPhase.FINISHED)
            release.countDown()
            val result = run.await()
            canceller.join()
            assertEquals(SessionOutcome.Cancelled, result.outcome)
            assertTrue(credentials.pin.isCleared)
            assertEquals(SessionPhase.FINISHED, session.state.value.phase)
            assertEquals(0, sender.sendCount)
        }

    @Test
    fun `SESSION-17 CARBS 1 w AWAITING_CODE_REQUEST - anulowanie z zewnatrz daje Cancelled i CancellationException`() =
        runTest {
            val credentials = SessionTestFixtures.credentials()
            val session = testSession(carbs1(), sender, source, credentials)
            val run = async { session.run() }
            runCurrent()
            assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
            run.cancel()
            runCurrent()
            assertEquals(SessionPhase.FINISHED, session.state.value.phase)
            assertEquals(SessionOutcome.Cancelled, session.state.value.result?.outcome)
            assertTrue(credentials.pin.isCleared)
            var thrown: Throwable? = null
            try {
                run.await()
            } catch (e: CancellationException) {
                thrown = e
            }
            assertTrue(thrown is CancellationException)
        }

    @Test
    fun `SESSION-17 SESSION-20 anulowanie w SENDING_COMMAND - zawieszone send nie konczy sie po FINISHED`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var sentAfterGate = 0
        val gatedSender = SmsSender {
            gate.await()
            sentAfterGate++
            SendResult.Sent
        }
        val session = testSession(Command.Bg, gatedSender, source)
        val run = async { session.run() }
        runCurrent()
        assertEquals(SessionPhase.SENDING_COMMAND, session.state.value.phase)
        session.cancel()
        runCurrent()
        assertEquals(SessionOutcome.Cancelled, run.await().outcome)
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, sentAfterGate)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
    }

    @Test
    fun `SESSION-07 SESSION-20 limit w SENDING_COMMAND - zawieszone send nie konczy sie po FINISHED`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var sentAfterGate = 0
        val gatedSender = SmsSender {
            gate.await()
            sentAfterGate++
            SendResult.Sent
        }
        val session = testSession(Command.Bg, gatedSender, source)
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.SENDING_COMMAND), run.await().outcome)
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, sentAfterGate)
    }

    @Test
    fun `SESSION-17 po Completed anulowanie nie zmienia wyniku`() = runTest {
        val session = testSession(Command.Bg, sender, source)
        val run = async { session.run() }
        runCurrent()
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0))
        runCurrent()
        val result = run.await()
        assertTrue(result.outcome is SessionOutcome.Completed)
        session.cancel()
        runCurrent()
        assertEquals(result, session.state.value.result)
    }

    @Test
    fun `SESSION-17 CARBS 1 anulowane w AWAITING_CONFIRMATION daje Unknown CANCELLED_AFTER_CODE`() = runTest {
        val credentials = SessionTestFixtures.credentials()
        val session = testSession(carbs1(), sender, source, credentials)
        val run = async { session.run() }
        runCurrent()
        requestCodeAt(source, 60_000)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        session.cancel()
        runCurrent()
        assertEquals(SessionOutcome.Unknown(UnknownReason.CANCELLED_AFTER_CODE, null), run.await().outcome)
        assertTrue(credentials.pin.isCleared)
    }

    @Test
    fun `SESSION-17 CARBS 1 anulowane w SENDING_CODE przy zawieszonym porcie daje Unknown CANCELLED_AFTER_CODE`() =
        runTest {
            var captured: CharArray? = null
            val session = testSession(
                carbs1(), hangingCodeSender { captured = it }, source, SessionTestFixtures.credentials(),
            )
            val run = async { session.run() }
            runCurrent()
            requestCodeAt(source, 60_000)
            assertEquals(SessionPhase.SENDING_CODE, session.state.value.phase)
            session.cancel()
            runCurrent()
            assertEquals(SessionOutcome.Unknown(UnknownReason.CANCELLED_AFTER_CODE, null), run.await().outcome)
            assertTrue(checkNotNull(captured).all { it == '\u0000' })
        }

    @Test
    fun `SESSION-17 CARBS 1 w AWAITING_CONFIRMATION - anulowanie z zewnatrz daje Unknown i CancellationException`() =
        runTest {
            val session = testSession(carbs1(), sender, source, SessionTestFixtures.credentials())
            val run = async { session.run() }
            runCurrent()
            requestCodeAt(source, 60_000)
            run.cancel()
            runCurrent()
            assertEquals(SessionPhase.FINISHED, session.state.value.phase)
            assertEquals(
                SessionOutcome.Unknown(UnknownReason.CANCELLED_AFTER_CODE, null),
                session.state.value.result?.outcome,
            )
            var thrown: Throwable? = null
            try {
                run.await()
            } catch (e: CancellationException) {
                thrown = e
            }
            assertTrue(thrown is CancellationException)
        }
}
