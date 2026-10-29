package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorProfileTest {
    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    @Test
    fun `PROF-11 aktywne CARBS 1 w AWAITING_CODE_REQUEST - start PROFILE LIST daje zajete i nie wysyla SMS`() = runTest {
        val clock = virtualClock()
        val coordinator = SessionCoordinator(sender, source, clock, UsableTotpProvider(clock), testScheduler.timeSource)
        val carbs = (Command.Carbs.of(1, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        val first = (coordinator.start(backgroundScope, carbs, SessionTestFixtures.credentials()) as StartResult.Started).session
        runCurrent()
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, first.state.value.phase)

        val result = coordinator.start(backgroundScope, Command.ProfileList, null)
        runCurrent()

        assertSame(StartResult.Busy, result)
        assertEquals(listOf("CARBS 1"), sender.sentCommands)
        assertSame(first, coordinator.activeSession.value)
        assertEquals(SessionPhase.AWAITING_CODE_REQUEST, first.state.value.phase)
    }
}
