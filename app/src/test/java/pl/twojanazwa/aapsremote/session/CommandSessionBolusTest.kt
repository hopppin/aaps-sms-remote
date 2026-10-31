package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitsValidation
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.parser.BolusRefusal
import pl.twojanazwa.aapsremote.otp.EpochClock
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionBolusTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()
    private val limits10 = (CommandLimits.of(50, 1000, 15) as LimitsValidation.Valid).limits

    private fun bolus(centiUnits: Int): Command =
        (Command.Bolus.of(centiUnits, limits10) as BolusValidation.Valid).command

    private fun TestScope.start(command: Command): Pair<CommandSession, Deferred<SessionResult>> {
        val session = testSession(command, sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        return session to run
    }

    private fun TestScope.startWithParser(
        command: Command,
        parse: (String, pl.twojanazwa.aapsremote.parser.ExpectedCommand) -> AapsReply,
    ): Pair<CommandSession, Deferred<SessionResult>> {
        val clock = virtualClock()
        val session = CommandSession(
            command, SessionTestFixtures.credentials(), sender, source, clock, UsableTotpProvider(clock),
            timeSource = testScheduler.timeSource, parse = parse,
        )
        val run = async { session.run() }
        runCurrent()
        return session to run
    }

    private suspend fun TestScope.deliver(millis: Long, body: String) = deliverAt(source, millis, body)

    private fun assertNoCode() = assertEquals(emptyList<String>(), sender.sentCodeCopies)

    private fun assertOutcome(expected: SessionOutcome, result: SessionResult) =
        assertEquals(expected, result.outcome)

    @Test
    fun `BOL-10 prosba o kod 0,10 w chwili 60000 wysyla kod i czeka na potwierdzenie`() = runTest {
        val (session, _) = start(bolus(10))
        deliver(60_000, AapsSamples.bolusCodeRequest)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        session.cancel()
        runCurrent()
    }

    @Test
    fun `BOL-10 obcieta ilosc w prosbie konczy sesje AMOUNT_MISMATCH bez kodu`() = runTest {
        val (_, run) = start(bolus(600))
        deliver(60_000, AapsSamples.bolusCodeRequestTruncated)
        val result = run.await()
        assertOutcome(SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, AapsSamples.bolusCodeRequestTruncated), result)
        assertEquals(1, sender.sentCommands.size)
        assertTrue(sender.sentCommands.single().startsWith("BOLUS"))
        assertNoCode()
    }

    @Test
    fun `BOL-10 odmowa AAPS przed prosba o kod to UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val (_, run) = start(bolus(10))
        deliver(30_000, "Inny bolus w kolejce")
        assertOutcome(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, "Inny bolus w kolejce"), run.await())
        assertNoCode()
    }

    @Test
    fun `BOL-10 potwierdzenie przed kodem jest wiadomoscia nieoczekiwana a brak odpowiedzi to TimedOut`() = runTest {
        val (session, run) = start(bolus(120))
        deliver(30_000, AapsSamples.bolusDone)
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        assertEquals(1, session.state.value.unexpectedMessages.size)
        advanceTimeBy(180_000 - currentTime)
        runCurrent()
        assertOutcome(SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST), run.await())
        assertNoCode()
    }

    /** Prosba w 58 000: kod bliski wygasniecia czeka na nastepny okres (PREPARING_CODE). */
    private suspend fun TestScope.preparing(): Pair<CommandSession, Deferred<SessionResult>> {
        val pair = start(bolus(10))
        deliver(58_000, AapsSamples.bolusCodeRequest)
        assertEquals(SessionPhase.PREPARING_CODE, pair.first.state.value.phase)
        return pair
    }

    @Test
    fun `BOL-10 druga prosba o kod w PREPARING_CODE to UNEXPECTED_REPLY bez kodu`() = runTest {
        val (_, run) = preparing()
        deliver(58_500, AapsSamples.bolusCodeRequest)
        assertOutcome(SessionOutcome.Failed(FailureReason.UNEXPECTED_REPLY, AapsSamples.bolusCodeRequest), run.await())
        assertNoCode()
    }

    @Test
    fun `BOL-10 niezgodna ilosc w PREPARING_CODE to AMOUNT_MISMATCH bez kodu`() = runTest {
        val (_, run) = preparing()
        deliver(58_500, AapsSamples.bolusCodeRequestTruncated)
        assertEquals(FailureReason.AMOUNT_MISMATCH, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    @Test
    fun `BOL-10 zly kod przed kodem to UNEXPECTED_REPLY`() = runTest {
        val (_, run) = start(bolus(10))
        deliver(30_000, AapsSamples.wrongCode)
        assertEquals(FailureReason.UNEXPECTED_REPLY, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    private suspend fun TestScope.confirming(centiUnits: Int): Pair<CommandSession, Deferred<SessionResult>> {
        val pair = start(bolus(centiUnits))
        deliver(60_000, bolusRequestFor(centiUnits))
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, pair.first.state.value.phase)
        return pair
    }

    private fun bolusRequestFor(centiUnits: Int): String =
        AapsSamples.bolusCodeRequest.replace("0,10", "%d,%02d".format(centiUnits / 100, centiUnits % 100))

    @Test
    fun `BOL-11 potwierdzenie 1,20 po kodzie daje BolusConfirmed`() = runTest {
        val (_, run) = confirming(120)
        deliver(120_000, AapsSamples.bolusDone)
        assertOutcome(SessionOutcome.BolusConfirmed(120, AapsSamples.bolusDone), run.await())
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `BOL-11 zly kod po kodzie to WRONG_CODE`() = runTest {
        val (_, run) = confirming(120)
        deliver(120_000, AapsSamples.wrongCode)
        assertEquals(FailureReason.WRONG_CODE, (run.await().outcome as SessionOutcome.Failed).reason)
    }

    @Test
    fun `BOL-11 nierozpoznana odpowiedz po kodzie to Unknown UNRECOGNIZED_REPLY`() = runTest {
        val (_, run) = confirming(120)
        deliver(120_000, "Bolus nie zostal podany")
        assertOutcome(SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "Bolus nie zostal podany"), run.await())
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `BOL-11 inna ilosc w potwierdzeniu to Unknown AMOUNT_MISMATCH`() = runTest {
        val (_, run) = confirming(125)
        deliver(120_000, AapsSamples.bolusDone)
        assertEquals(UnknownReason.AMOUNT_MISMATCH, (run.await().outcome as SessionOutcome.Unknown).reason)
    }

    @Test
    fun `BOL-11 prosba o kod po kodzie to Unknown UNEXPECTED_REPLY bez drugiego kodu`() = runTest {
        val (_, run) = confirming(10)
        deliver(120_000, AapsSamples.bolusCodeRequest)
        assertEquals(UnknownReason.UNEXPECTED_REPLY, (run.await().outcome as SessionOutcome.Unknown).reason)
        assertEquals(1, sender.sentCodeCopies.size)
        assertEquals(1, sender.sentCommands.size)
    }

    @Test
    fun `BOL-11 potwierdzenie sprzed wyslania kodu jest wiadomoscia nieoczekiwana`() = runTest {
        val (session, _) = confirming(120)
        source.emit(IncomingSms(AapsSamples.bolusDone, SessionTestFixtures.T0 + 59_000))
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        assertEquals(1, session.state.value.unexpectedMessages.size)
        session.cancel()
        runCurrent()
    }

    @Test
    fun `BOL-11 toString wyniku i stanu nie zawiera ilosci`() = runTest {
        val (session, run) = confirming(120)
        deliver(120_000, AapsSamples.bolusDone)
        val result = run.await()
        for (s in listOf(result.outcome.toString(), session.state.value.toString(), result.toString())) {
            assertFalse(s.contains("120") || s.contains("1,20"), s)
        }
    }

    @Test
    fun `BOL-19 prosba o kod CARBS w sesji BOLUS to UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val (_, run) = start(bolus(10))
        deliver(60_000, AapsSamples.carbsCodeRequest)
        assertEquals(FailureReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    @Test
    fun `BOL-19 prosba o kod profilu w sesji BOLUS to UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val (_, run) = start(bolus(10))
        // Typ profilowy (spec 013) jeszcze nie istnieje (AapsReply jest sealed), wiec decyduje prawdziwy parser.
        deliver(60_000, PROFILE_REQUEST)
        assertEquals(FailureReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    @Test
    fun `BOL-19 prosba o kod BOLUS w sesji CARBS to UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val (_, run) = start(carbsCommand(1))
        deliver(60_000, AapsSamples.bolusCodeRequest)
        assertEquals(FailureReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    @Test
    fun `BOL-19 podstawiony parser zwracajacy CodeRequest w sesji BOLUS to UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val (_, run) = startWithParser(bolus(10)) { _, _ -> AapsReply.CodeRequest(1, "x") }
        deliver(60_000, "dowolna tresc")
        assertEquals(FailureReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    @Test
    fun `BOL-19 podstawiony parser zwracajacy CarbsDone po kodzie to Unknown UNRECOGNIZED_REPLY`() = runTest {
        var calls = 0
        val (_, run) = startWithParser(bolus(10)) { _, _ ->
            if (calls++ == 0) AapsReply.BolusCodeRequest(10, "x") else AapsReply.CarbsDone(1, "x")
        }
        deliver(60_000, "pierwsza")
        deliver(120_000, "druga")
        assertEquals(UnknownReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Unknown).reason)
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `BOL-10 prosba z inna ilosc niz wyslana (parser zwraca 0,99) nie wysyla kodu`() = runTest {
        val (_, run) = startWithParser(bolus(10)) { _, _ -> AapsReply.BolusCodeRequest(99, "x") }
        deliver(60_000, "dowolna tresc")
        assertEquals(FailureReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Failed).reason)
        assertNoCode()
    }

    @Test
    fun `BOL-11 potwierdzenie z inna ilosc niz wyslana (parser zwraca 0,99) to Unknown UNRECOGNIZED_REPLY`() = runTest {
        var calls = 0
        val (_, run) = startWithParser(bolus(10)) { _, _ ->
            if (calls++ == 0) AapsReply.BolusCodeRequest(10, "x") else AapsReply.BolusDone(99, "x")
        }
        deliver(60_000, "pierwsza")
        deliver(120_000, "druga")
        assertEquals(UnknownReason.UNRECOGNIZED_REPLY, (run.await().outcome as SessionOutcome.Unknown).reason)
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `BOL-20 podstawiony parser zwracajacy BolusRefused lub BolusFailed w sesji CARBS to UNRECOGNIZED_REPLY bez kodu`() =
        runTest {
            val replies = listOf(AapsReply.BolusRefused(BolusRefusal.INTERVAL, "x"), AapsReply.BolusFailed("x"))
            for ((i, reply) in replies.withIndex()) {
                val (_, run) = startWithParser(carbsCommand(1)) { _, _ -> reply }
                deliver(60_000L * (i + 1), "dowolna tresc")
                assertOutcome(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, "dowolna tresc"), run.await())
            }
            assertNoCode()
        }

    @Test
    fun `BOL-10 limit przygotowania kodu 10 s - 9999 ms PREPARING_CODE a 10000 ms CODE_PREPARATION_TIMEOUT`() = runTest {
        val stopped = 1111111109000
        val session = testSession(
            bolus(10), sender, source, SessionTestFixtures.credentials(), clock = EpochClock { stopped },
        )
        val run = async { session.run() }
        runCurrent()
        source.emit(IncomingSms(AapsSamples.bolusCodeRequest, stopped))
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(SessionPhase.PREPARING_CODE, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertOutcome(SessionOutcome.Failed(FailureReason.CODE_PREPARATION_TIMEOUT, null), run.await())
        assertNoCode()
    }

    @Test
    fun `BOL-10 prosba starsza niz 240 s to CODE_REQUEST_EXPIRED bez kodu`() = runTest {
        val base = 1111110811000
        val session = testSession(
            bolus(10), sender, source, SessionTestFixtures.credentials(),
            timeouts = SessionTimeouts(reply = 10.minutes), clock = EpochClock { base + currentTime },
        )
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(300_000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.bolusCodeRequest, 1111110870999))
        runCurrent()
        assertOutcome(SessionOutcome.Failed(FailureReason.CODE_REQUEST_EXPIRED, AapsSamples.bolusCodeRequest), run.await())
        assertNoCode()
    }

    @Test
    fun `BOL-11 limit potwierdzenia 6 min konczy sesje Unknown CONFIRMATION_TIMEOUT dopiero w chwili 420000`() = runTest {
        val (session, run) = confirming(10)
        advanceTimeBy(419_999 - currentTime)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        advanceTimeBy(1)
        runCurrent()
        assertOutcome(SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null), run.await())
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `BOL-11 anulowanie po wyslaniu kodu daje Unknown CANCELLED_AFTER_CODE`() = runTest {
        val (session, run) = confirming(10)
        session.cancel()
        runCurrent()
        assertOutcome(SessionOutcome.Unknown(UnknownReason.CANCELLED_AFTER_CODE, null), run.await())
        assertEquals(1, sender.sentCodeCopies.size)
    }

    private companion object {
        const val PROFILE_REQUEST = "Aby przelaczyc profil na LocalProfile1 100% odpowiedz kodem z aplikacji " +
            "Authenticator dla: Jan, z doklejonym PIN-em"
    }

    @Test
    fun `BOL-20 odmowa odstep przed prosba o kod to Failed AAPS_BOLUS_INTERVAL bez kodu`() = runTest {
        val body = AapsSamples.bolusRefusedInterval
        val (_, run) = start(bolus(10))
        deliver(30_000, body)
        val result = run.await()
        assertOutcome(SessionOutcome.Failed(FailureReason.AAPS_BOLUS_INTERVAL, body), result)
        assertNoCode()
        assertEquals(listOf("BOLUS 0.1"), sender.sentCommands)
        assertFalse(result.toString().contains(body.lines().first()))
    }

    @Test
    fun `BOL-20 odmowa kolejka przed prosba o kod to Failed AAPS_BOLUS_QUEUE bez kodu`() = runTest {
        val body = AapsSamples.bolusRefusedQueue
        val (_, run) = start(bolus(10))
        deliver(30_000, body)
        val result = run.await()
        assertOutcome(SessionOutcome.Failed(FailureReason.AAPS_BOLUS_QUEUE, body), result)
        assertNoCode()
        assertFalse(result.toString().contains(body.lines().first()))
    }

    @Test
    fun `BOL-20 odmowa kolejka w PREPARING_CODE to Failed AAPS_BOLUS_QUEUE bez kodu`() = runTest {
        val (_, run) = preparing()
        deliver(58_500, AapsSamples.bolusRefusedQueue)
        assertOutcome(SessionOutcome.Failed(FailureReason.AAPS_BOLUS_QUEUE, AapsSamples.bolusRefusedQueue), run.await())
        assertNoCode()
    }

    @Test
    fun `BOL-20 odmowa odstep po kodzie to Unknown UNRECOGNIZED_REPLY bez drugiego kodu`() = runTest {
        val body = AapsSamples.bolusRefusedInterval
        val (_, run) = confirming(10)
        deliver(120_000, body)
        assertOutcome(SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, body), run.await())
        assertEquals(1, sender.sentCodeCopies.size)
        assertEquals(listOf("BOLUS 0.1"), sender.sentCommands)
    }

    @Test
    fun `BOL-20 odmowa odstep w sesji CARBS to Failed UNRECOGNIZED_REPLY`() = runTest {
        val body = AapsSamples.bolusRefusedInterval
        val (_, run) = start(carbsCommand(1))
        deliver(30_000, body)
        assertOutcome(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, body), run.await())
        assertNoCode()
    }

    @Test
    fun `BOL-20 podstawiony parser zwracajacy BolusRefused w AWAITING_CODE_REQUEST to Failed bez kodu`() = runTest {
        val replies = mapOf(
            BolusRefusal.INTERVAL to FailureReason.AAPS_BOLUS_INTERVAL,
            BolusRefusal.QUEUE to FailureReason.AAPS_BOLUS_QUEUE,
        )
        for ((i, e) in replies.entries.withIndex()) {
            val (_, run) = startWithParser(bolus(10)) { _, _ -> AapsReply.BolusRefused(e.key, "x") }
            deliver(60_000L * (i + 1), "dowolna tresc")
            assertOutcome(SessionOutcome.Failed(e.value, "dowolna tresc"), run.await())
        }
        assertNoCode()
    }

    @Test
    fun `SESSION-08 BOL-20 prosba o kod po odmowie nie dostaje kodu`() = runTest {
        val (session, run) = start(bolus(10))
        deliver(30_000, AapsSamples.bolusRefusedInterval)
        val result = run.await()
        deliver(60_000, AapsSamples.bolusCodeRequest)
        assertOutcome(SessionOutcome.Failed(FailureReason.AAPS_BOLUS_INTERVAL, AapsSamples.bolusRefusedInterval), result)
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertNoCode()
        assertEquals(listOf("BOLUS 0.1"), sender.sentCommands)
    }

    @Test
    fun `BOL-21 blad bolusa po kodzie to Unknown BOLUS_FAILED i dokladnie 2 SMS-y`() = runTest {
        val body = AapsSamples.bolusFailed
        val (_, run) = confirming(120)
        deliver(120_000, body)
        val result = run.await()
        assertOutcome(SessionOutcome.Unknown(UnknownReason.BOLUS_FAILED, body), result)
        assertEquals(listOf("BOLUS 1.2", "0504714321"), sender.sentCommands + sender.sentCodeCopies)
        assertFalse(result.toString().contains("94%"))
        assertFalse(result.toString().contains(body.lines().first()))
    }

    @Test
    fun `BOL-21 blad bolusa sprzed chwili wyslania kodu trafia do nieoczekiwanych`() = runTest {
        val session = testSession(
            bolus(120), delayedCodeSender(3_000), source, SessionTestFixtures.credentials(),
        )
        val run = async { session.run() }
        runCurrent()
        deliver(60_000, bolusRequestFor(120))
        assertEquals(SessionPhase.SENDING_CODE, session.state.value.phase)
        deliver(62_000, AapsSamples.bolusFailed)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        assertEquals(listOf(AapsSamples.bolusFailed), session.state.value.unexpectedMessages.map { it.body })
        session.cancel()
        runCurrent()
        run.await()
    }

    @Test
    fun `BOL-21 blad bolusa przed prosba o kod to Failed UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val body = AapsSamples.bolusFailed
        val (_, run) = start(bolus(10))
        deliver(30_000, body)
        assertOutcome(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, body), run.await())
        assertNoCode()
        assertEquals(listOf("BOLUS 0.1"), sender.sentCommands)
    }

    @Test
    fun `BOL-21 blad bolusa w PREPARING_CODE to Failed UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val body = AapsSamples.bolusFailed
        val (_, run) = preparing()
        deliver(58_500, body)
        assertOutcome(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, body), run.await())
        assertNoCode()
        assertEquals(listOf("BOLUS 0.1"), sender.sentCommands)
    }

    @Test
    fun `BOL-20 odmowa kolejka po wyslaniu kodu to Unknown UNRECOGNIZED_REPLY i dokladnie jeden kod`() = runTest {
        val body = AapsSamples.bolusRefusedQueue
        val (_, run) = confirming(10)
        deliver(120_000, body)
        assertOutcome(SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, body), run.await())
        assertEquals(1, sender.sentCodeCopies.size)
        assertEquals(listOf("BOLUS 0.1"), sender.sentCommands)
    }

    @Test
    fun `BOL-20 odmowa odebrana w SENDING_CODE z chwila przed wyslaniem kodu to Unknown UNRECOGNIZED_REPLY i jeden kod`() =
        runTest {
            val body = AapsSamples.bolusRefusedQueue
            sender.delayMillis = 3_000
            val session = testSession(bolus(120), sender, source, SessionTestFixtures.credentials())
            val run = async { session.run() }
            runCurrent()
            deliver(60_000, bolusRequestFor(120))
            assertEquals(SessionPhase.SENDING_CODE, session.state.value.phase)
            // Odmowa w chwili 62 000 < 63 000 (wysłanie kodu): po "wysłano" trafia do obsługi po kodzie, nie jest odrzucona.
            deliver(62_000, body)
            advanceTimeBy(1_000)
            runCurrent()
            assertOutcome(SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, body), run.await())
            assertEquals(1, sender.sentCodeCopies.size)
            assertEquals(listOf("BOLUS 1.2"), sender.sentCommands)
        }
}
