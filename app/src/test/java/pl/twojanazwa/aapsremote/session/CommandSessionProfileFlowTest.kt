package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.Deferred
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
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.smsText
import pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.parser.ExpectedCommand

/** PROF-11, PROF-12, PROF-07 (część): sesje PROFILE STATUS / LIST / zmiana profilu. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionProfileFlowTest {
    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun switch(percent: Int): Command =
        (Command.ProfileSwitch.of(percent) as ProfileSwitchValidation.Valid).command

    private fun TestScope.start(
        command: Command,
        parse: ((String, ExpectedCommand) -> AapsReply)? = null,
    ): Pair<CommandSession, Deferred<SessionResult>> {
        val clock = virtualClock()
        val session = if (parse == null) {
            testSession(command, sender, source, credentialsFor(command))
        } else {
            CommandSession(
                command, credentialsFor(command), sender, source, clock, UsableTotpProvider(clock),
                timeSource = testScheduler.timeSource, parse = parse,
            )
        }
        val run = async { session.run() }
        runCurrent()
        return session to run
    }

    private suspend fun TestScope.deliver(millis: Long, body: String) = deliverAt(source, millis, body)

    private fun credentialsFor(command: Command) =
        if (command.requiresExtraConfirmation(CommandLimits.DEFAULT) || command.smsText().let { it.startsWith("PROFILE 1") || it.startsWith("CARBS") || it.startsWith("BOLUS") }) SessionTestFixtures.credentials() else null

    private fun assertNoCode() = assertEquals(emptyList<String>(), sender.sentCodeCopies)

    private val request = AapsSamples.profileCodeRequest
    private val requestOther = request.replace("LocalProfile1", "LocalProfile2")
    private val request115 = request.replace("100%", "115%")

    private val local1 get() = switch(100)

    @Test
    fun `PROF-11 PROFILE LIST z lista dwoch pozycji konczy sie Completed`() = runTest {
        val (_, run) = start(Command.ProfileList)
        assertEquals(listOf("PROFILE LIST"), sender.sentCommands)
        deliver(60_000, AapsSamples.profileList)
        val outcome = run.await().outcome as SessionOutcome.Completed
        assertEquals(AapsSamples.profileList, outcome.text)
        assertEquals(2, (outcome.reply as AapsReply.ProfileListReport).items.size)
        assertNoCode()
    }

    @Test
    fun `PROF-11 PROFILE STATUS konczy sie Completed a bledny format Failed UNRECOGNIZED_REPLY z trescia`() = runTest {
        val (_, run) = start(Command.ProfileStatus)
        assertEquals(listOf("PROFILE STATUS"), sender.sentCommands)
        deliver(60_000, AapsSamples.profileStatus)
        val outcome = run.await().outcome as SessionOutcome.Completed
        assertTrue(outcome.reply is AapsReply.ProfileStatusReport)

        val (_, bad) = start(Command.ProfileStatus)
        deliver(120_000, AapsSamples.profileBadFormat)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.profileBadFormat),
            bad.await().outcome,
        )
    }

    @Test
    fun `PROF-11 raport innego typu konczy sesje Failed UNRECOGNIZED_REPLY`() = runTest {
        val (_, status) = start(Command.ProfileStatus)
        deliver(60_000, AapsSamples.profileList)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.profileList),
            status.await().outcome,
        )
        val (_, list) = start(Command.ProfileList)
        deliver(120_000, AapsSamples.profileStatus)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.profileStatus),
            list.await().outcome,
        )
    }

    @Test
    fun `PROF-11 druga linia obrony - wstrzykniety raport niezgodny z poleceniem nie konczy sie Completed`() = runTest {
        val (_, run) = start(Command.ProfileList) { body, _ -> AapsReply.ProfileStatusReport(body) }
        deliver(60_000, AapsSamples.profileList)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.profileList),
            run.await().outcome,
        )
    }

    @Test
    fun `PROF-11 druga linia obrony - BgReport w sesji PROFILE STATUS nie konczy sie Completed`() = runTest {
        val (_, run) = start(Command.ProfileStatus) { body, _ -> AapsReply.BgReport(210, body) }
        deliver(60_000, AapsSamples.bg)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.bg),
            run.await().outcome,
        )
    }

    @Test
    fun `PROF-11 druga linia obrony - PumpStatus w sesji PROFILE STATUS nie konczy sie Completed`() = runTest {
        val (_, run) = start(Command.ProfileStatus) { body, _ -> AapsReply.PumpStatus(body) }
        deliver(60_000, AapsSamples.pump)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.pump),
            run.await().outcome,
        )
    }

    @Test
    fun `PROF-12 zgodna prosba o kod w chwili 60000 wysyla PROFILE 1 i kod`() = runTest {
        val (session, _) = start(local1)
        assertEquals(listOf("PROFILE 1 100"), sender.sentCommands)
        deliver(60_000, request)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        session.cancel()
        runCurrent()
    }

    @Test
    fun `PROF-12 inna nazwa profilu przy zgodnym procencie daje kod`() = runTest {
        val (session, _) = start(local1)
        deliver(60_000, requestOther)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        session.cancel()
        runCurrent()
    }

    @Test
    fun `PROF-12 nazwa z liczba nie myli procentu - decyduje procent sesji`() = runTest {
        val zima = request.replace("LocalProfile1", "Zima 200")
        val (_, run) = start(switch(200))
        deliver(60_000, zima)
        assertEquals(SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, zima), run.await().outcome)
    }

    @Test
    fun `PROF-12 nazwa z liczba przy zgodnym procencie 100 daje kod`() = runTest {
        val zima = request.replace("LocalProfile1", "Zima 200")
        val (session, _) = start(local1)
        deliver(60_000, zima)
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        session.cancel()
        runCurrent()
    }

    @Test
    fun `PROF-12 prosba sprzed wyslania polecenia jest pominieta i nie daje kodu`() = runTest {
        val (session, _) = start(local1)
        source.emit(IncomingSms(request, SessionTestFixtures.T0 - 1_000))
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, session.state.value.phase)
        assertNoCode()
        session.cancel()
        runCurrent()
    }

    @Test
    fun `PROF-12 inny procent to AMOUNT_MISMATCH a bledny format UNRECOGNIZED_REPLY, oba bez kodu`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, request115)
        assertEquals(SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, request115), run.await().outcome)

        val (_, bad) = start(switch(115))
        deliver(120_000, AapsSamples.profileBadFormat)
        assertEquals(
            SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, AapsSamples.profileBadFormat),
            bad.await().outcome,
        )
        assertNoCode()
    }

    @Test
    fun `PROF-12 druga linia obrony - wstrzykniete prosby z innym procentem nie wysylaja kodu`() = runTest {
        val bodies = listOf(request115, request.replace("100%", "1000%"))
        for ((i, body) in bodies.withIndex()) {
            val (_, run) = start(local1) { b, _ -> AapsReply.ProfileCodeRequest(b) }
            deliver(60_000L * (i + 1), body)
            assertEquals(SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, body), run.await().outcome)
        }
        assertNoCode()
    }

    @Test
    fun `PROF-12 po kodzie nieznane potwierdzenie to Unknown UNRECOGNIZED_REPLY z trescia`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, request)
        deliver(65_000, "Profil zmieniony")
        assertEquals(
            SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "Profil zmieniony"),
            run.await().outcome,
        )
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `PROF-12 po kodzie zly kod to Failed WRONG_CODE`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, request)
        deliver(65_000, AapsSamples.wrongCode)
        assertEquals(SessionOutcome.Failed(FailureReason.WRONG_CODE, AapsSamples.wrongCode), run.await().outcome)
    }

    @Test
    fun `PROF-12 po kodzie kolejna prosba o kod to Unknown UNEXPECTED_REPLY bez drugiego kodu`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, request)
        deliver(65_000, request)
        assertEquals(SessionOutcome.Unknown(UnknownReason.UNEXPECTED_REPLY, request), run.await().outcome)
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `PROF-12 po kodzie niezgodny profil to Unknown AMOUNT_MISMATCH`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, request)
        deliver(65_000, request115)
        assertEquals(SessionOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH, request115), run.await().outcome)
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `PROF-12 zly kod przed kodem to Failed UNEXPECTED_REPLY`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, AapsSamples.wrongCode)
        assertEquals(SessionOutcome.Failed(FailureReason.UNEXPECTED_REPLY, AapsSamples.wrongCode), run.await().outcome)
        assertNoCode()
    }

    @Test
    fun `PROF-12 brak potwierdzenia w 6 min to Unknown CONFIRMATION_TIMEOUT`() = runTest {
        val (_, run) = start(local1)
        deliver(60_000, request)
        advanceTimeBy(60_000 + 360_000 - currentTime)
        runCurrent()
        assertEquals(SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null), run.await().outcome)
        assertEquals(1, sender.sentCodeCopies.size)
    }

    @Test
    fun `PROF-12 prosba o kod bolusa lub CARBS w sesji zmiany profilu to Failed UNRECOGNIZED_REPLY bez kodu`() =
        runTest {
            for ((i, body) in listOf(AapsSamples.bolusCodeRequest, AapsSamples.carbsCodeRequest).withIndex()) {
                val (_, run) = start(local1)
                deliver(60_000L * (i + 1), body)
                assertEquals(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, body), run.await().outcome)
            }
            assertNoCode()
        }


    @Test
    fun `PROF-12 sesje CARBS i BOLUS z prosba o zmiane profilu to UNRECOGNIZED_REPLY bez kodu`() = runTest {
        val carbs = (Command.Carbs.of(1, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        val bolus = (Command.Bolus.of(10, CommandLimits.DEFAULT) as BolusValidation.Valid).command
        for ((i, c) in listOf(carbs, bolus).withIndex()) {
            val (_, run) = start(c)
            deliver(60_000L * (i + 1), request)
            assertEquals(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, request), run.await().outcome)
        }
        assertNoCode()
    }
}
