package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionNoRetryTest {

    @Test
    fun `SESSION-20 po FINISHED port odbioru nie ma subskrybenta sesji`() = runTest {
        val clock = virtualClock()
        val source = FakeIncomingSmsSource()
        val sender = FakeSmsSender().apply { commandResult = SendResult.Failed }
        val session = CommandSession(
            command = Command.Bg,
            credentials = null,
            sender = sender,
            incoming = source,
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
        session.run()
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertEquals(0, source.subscriptionCount.value)
    }
}
