package pl.twojanazwa.aapsremote.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.ui.commands.ResultMapper

class HistoryResultParityTest {
    private val command: Command = (Command.Carbs.of(20, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

    private fun session(outcome: SessionOutcome) = SessionResult(
        command = command,
        startedAtEpochMillis = 1L,
        commandSentAtEpochMillis = 2L,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = 3L,
        outcome = outcome,
        unexpectedMessages = emptyList(),
    )

    private fun assertParity(history: HistoryOutcome, session: SessionOutcome) {
        val h = HistoryEntryMapper.result(historyEntry(history))
        val s = ResultMapper.map(session(session))
        assertEquals(s.kind, h.kind, "$history")
        assertEquals(s.message, h.message, "$history")
        assertEquals(s.messageArg, h.messageArg, "$history")
        assertEquals(s.codeNotSent, h.codeNotSent, "$history")
        assertEquals(s.unknownWarning, h.unknownWarning, "$history")
    }

    @Test
    fun `HISTUI-04 FailureReason jak w ResultMapper`() {
        FailureReason.entries.forEach {
            assertParity(HistoryOutcome.Failed(it), SessionOutcome.Failed(it, null))
        }
    }

    @Test
    fun `HISTUI-04 UnknownReason jak w ResultMapper`() {
        UnknownReason.entries.forEach {
            assertParity(HistoryOutcome.Unknown(it), SessionOutcome.Unknown(it, null))
        }
    }

    @Test
    fun `HISTUI-04 TimedOut w trzech fazach jak w ResultMapper`() {
        listOf(
            SessionPhase.SENDING_COMMAND,
            SessionPhase.AWAITING_REPLY,
            SessionPhase.AWAITING_CODE_REQUEST,
        ).forEach { assertParity(HistoryOutcome.TimedOut(it), SessionOutcome.TimedOut(it)) }
    }
}
