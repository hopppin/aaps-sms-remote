package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.FakeSmsSender
import pl.twojanazwa.aapsremote.session.IncomingSmsSource
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.testSession

/** SMS-04 z trwającą sesją zmiany profilu (PROF-19): potwierdzenie z obcego numeru nie dociera do sesji. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomingSmsHubProfileSessionTest {
    @Test
    fun `PROF-19 SMS-04 potwierdzenie zmiany profilu z innego numeru niz AAPS nie trafia do sesji po kodzie`() = runTest {
        val f = HubFixture(clock = QueueClock(SessionTestFixtures.T0 + 60_000))
        val sender = FakeSmsSender()
        val command = (Command.ProfileSwitch.of(100) as ProfileSwitchValidation.Valid).command
        val session = testSession(command, sender, IncomingSmsSource { f.hub.messages() }, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        f.registrar.deliver(RawSmsPart(HubFixture.AAPS, AapsSamples.profileCodeRequest, 1L))
        runCurrent()
        assertEquals(listOf("0504714321"), sender.sentCodeCopies)
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)

        f.registrar.deliver(RawSmsPart(HubFixture.OTHER, AapsSamples.profileSwitchDone, 2L))
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CONFIRMATION, session.state.value.phase)
        assertTrue(session.state.value.unexpectedMessages.isEmpty())

        session.cancel()
        runCurrent()
        assertTrue(run.await().outcome is SessionOutcome.Unknown)
    }
}
