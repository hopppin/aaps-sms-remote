package pl.twojanazwa.aapsremote.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.AapsSamples

class SessionRedactionTest {

    private fun carbs37(): Command {
        val valid = Command.Carbs.of(37) as pl.twojanazwa.aapsremote.commands.CarbsValidation.Valid
        return valid.command
    }

    @Test
    fun `SESSION-23 toString AapsPin nie zawiera PIN-u`() {
        assertFalse(SessionTestFixtures.pin().toString().contains("4321"))
    }

    @Test
    fun `SESSION-23 toString odpowiedzi z kodem nie zawiera kodu ani PIN-u`() {
        val text = OutgoingSms.CodeReply("0504714321".toCharArray()).toString()
        assertFalse(text.contains("050471"))
        assertFalse(text.contains("4321"))
    }

    @Test
    fun `SESSION-23 toString wiadomosci stanu i wyniku nie zawiera tresci`() {
        val body = AapsSamples.bg
        val sms = IncomingSms(body, SessionTestFixtures.T0)
        val result = SessionResult(
            command = Command.Bg,
            startedAtEpochMillis = SessionTestFixtures.T0,
            commandSentAtEpochMillis = SessionTestFixtures.T0,
            codeSentAtEpochMillis = null,
            finishedAtEpochMillis = SessionTestFixtures.T0,
            outcome = SessionOutcome.Completed(AapsReply.PumpStatus(body), body),
            unexpectedMessages = listOf(sms),
        )
        val state = SessionState(SessionPhase.FINISHED, listOf(sms), result)
        for (text in listOf(sms.toString(), state.toString(), result.toString())) {
            assertFalse(text.contains("210"))
            body.lines().filter { it.isNotBlank() }.forEach { assertFalse(text.contains(it.trim())) }
        }
    }

    @Test
    fun `SESSION-23 toString wyniku CarbsConfirmed i stanu nie zawiera ilosci`() {
        val confirmation = "Pomyslnie wprowadzono 37 g weglowodanow"
        val result = SessionResult(
            command = carbs37(),
            startedAtEpochMillis = SessionTestFixtures.T0,
            commandSentAtEpochMillis = SessionTestFixtures.T0,
            codeSentAtEpochMillis = SessionTestFixtures.T0,
            finishedAtEpochMillis = SessionTestFixtures.T0,
            outcome = SessionOutcome.CarbsConfirmed(37, confirmation),
            unexpectedMessages = emptyList(),
        )
        val state = SessionState(SessionPhase.FINISHED, emptyList(), result)
        assertFalse(result.toString().contains("37"))
        assertFalse(state.toString().contains("37"))
    }

    @Test
    fun `SESSION-23 toString CodeCredentials i CommandText bez danych`() {
        assertFalse(SessionTestFixtures.credentials().toString().contains("4321"))
        assertFalse(OutgoingSms.CommandText("CARBS 37").toString().contains("37"))
    }
}
