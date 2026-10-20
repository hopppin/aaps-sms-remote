package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommandSessionCreationTest {

    private fun carbs20(): Command = (Command.Carbs.of(20) as CarbsValidation.Valid).command

    private fun TestScope.session(command: Command, credentials: CodeCredentials?): CommandSession {
        val clock = virtualClock()
        return CommandSession(
            command = command,
            credentials = credentials,
            sender = FakeSmsSender(),
            incoming = FakeIncomingSmsSource(),
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
    }

    @Test
    fun `SESSION-02 CARBS bez danych do kodu rzuca IllegalArgumentException`() = runTest {
        val e = assertThrows<IllegalArgumentException> { session(carbs20(), null) }
        assertFalse(e.message.orEmpty().contains("20"))
    }

    @Test
    fun `SESSION-02 BG z danymi do kodu rzuca wyjatek i czysci PIN`() = runTest {
        val credentials = SessionTestFixtures.credentials()
        val e = assertThrows<IllegalArgumentException> { session(Command.Bg, credentials) }
        assertTrue(credentials.pin.isCleared)
        assertFalse(e.message.orEmpty().contains(SessionTestFixtures.PIN_TEXT))
    }

    @Test
    fun `SESSION-02 BG i PUMP bez danych do kodu tworza sesje w fazie NOT_STARTED`() = runTest {
        for (command in listOf(Command.Bg, Command.Pump)) {
            val session = session(command, null)
            assertEquals(SessionPhase.NOT_STARTED, session.state.value.phase)
            assertEquals("CommandSession(phase=NOT_STARTED)", session.toString())
        }
    }
}
