package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult
import java.io.File

class ResultRedactionTest {

    private fun result(command: Command, outcome: SessionOutcome) = SessionResult(
        command = command,
        startedAtEpochMillis = 1L,
        commandSentAtEpochMillis = 2L,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = 3L,
        outcome = outcome,
        unexpectedMessages = emptyList(),
    )

    @Test
    fun `CMDUI-26 toString wyniku BG nie zawiera glikemii ani linii tresci`() {
        val text = File("src/test/resources/parser/aaps-3.4.2.6-pl/bg.txt").readText()
        val view = ResultMapper.map(
            result(Command.Bg, SessionOutcome.Completed(AapsReply.BgReport(210, text), text)),
        )
        val shown = view.toString()
        assertFalse(shown.contains("210"), shown)
        text.lines().filter { it.isNotBlank() }.forEach { assertFalse(shown.contains(it), shown) }
    }

    @Test
    fun `CMDUI-26 toString wyniku CarbsConfirmed nie zawiera ilosci ani tresci`() {
        val command = (Command.Carbs.of(37) as CarbsValidation.Valid).command
        val view = ResultMapper.map(
            result(command, SessionOutcome.CarbsConfirmed(37, "Tajna tresc SMS")),
        )
        val shown = view.toString()
        assertFalse(shown.contains("37"), shown)
        assertFalse(shown.contains("Tajna"), shown)
    }
}
