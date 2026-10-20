package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider

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
}
