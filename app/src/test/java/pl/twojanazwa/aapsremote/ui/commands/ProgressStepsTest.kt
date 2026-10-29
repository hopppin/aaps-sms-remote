package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionTimeouts

class ProgressStepsTest {
    private val d = StepStatus.DONE
    private val c = StepStatus.CURRENT
    private val p = StepStatus.PENDING

    private fun statuses(command: CommandId, phase: SessionPhase) =
        ProgressSteps.of(command, phase).map { it.status }

    @Test
    fun `CMDUI-17 CARBS ma 5 krokow ze statusami wg tabeli dla kazdej fazy`() {
        val expected = mapOf(
            SessionPhase.NOT_STARTED to listOf(c, p, p, p, p),
            SessionPhase.SENDING_COMMAND to listOf(c, p, p, p, p),
            SessionPhase.AWAITING_REPLY to listOf(d, c, p, p, p),
            SessionPhase.AWAITING_CODE_REQUEST to listOf(d, c, p, p, p),
            SessionPhase.PREPARING_CODE to listOf(d, d, c, p, p),
            SessionPhase.SENDING_CODE to listOf(d, d, d, c, p),
            SessionPhase.AWAITING_CONFIRMATION to listOf(d, d, d, d, c),
        )
        expected.forEach { (phase, expectedStatuses) ->
            assertEquals(expectedStatuses, statuses(CommandId.CARBS, phase), "faza $phase")
        }
        assertEquals(
            listOf(
                ProgressStepId.SEND_COMMAND, ProgressStepId.AWAIT_CODE_REQUEST, ProgressStepId.PREPARE_CODE,
                ProgressStepId.SEND_CODE, ProgressStepId.AWAIT_CONFIRMATION,
            ),
            ProgressSteps.of(CommandId.CARBS, SessionPhase.NOT_STARTED).map { it.id },
        )
    }

    @Test
    fun `CMDUI-17 BG i PUMP maja 2 kroki w fazach wysylania i oczekiwania na odpowiedz`() {
        for (command in listOf(CommandId.BG, CommandId.PUMP)) {
            assertEquals(listOf(c, p), statuses(command, SessionPhase.SENDING_COMMAND))
            assertEquals(listOf(d, c), statuses(command, SessionPhase.AWAITING_REPLY))
            assertEquals(
                listOf(ProgressStepId.SEND_COMMAND, ProgressStepId.AWAIT_REPLY),
                ProgressSteps.of(command, SessionPhase.AWAITING_REPLY).map { it.id },
            )
        }
    }

    @Test
    fun `CMDUI-17 faza niemozliwa lub FINISHED daje wszystkie kroki oczekujace`() {
        assertEquals(listOf(p, p), statuses(CommandId.BG, SessionPhase.AWAITING_CONFIRMATION))
        assertEquals(listOf(p, p), statuses(CommandId.PUMP, SessionPhase.SENDING_CODE))
        assertEquals(listOf(p, p), statuses(CommandId.BG, SessionPhase.FINISHED))
        assertEquals(listOf(p, p, p, p, p), statuses(CommandId.CARBS, SessionPhase.FINISHED))
    }

    @Test
    fun `CMDUI-17 argumenty minut z domyslnych SessionTimeouts to 3 i 6`() {
        assertEquals(3, ProgressSteps.minutesArg(ProgressStepId.AWAIT_REPLY))
        assertEquals(3, ProgressSteps.minutesArg(ProgressStepId.AWAIT_CODE_REQUEST))
        assertEquals(6, ProgressSteps.minutesArg(ProgressStepId.AWAIT_CONFIRMATION))
        assertNull(ProgressSteps.minutesArg(ProgressStepId.SEND_COMMAND, SessionTimeouts()))
        assertNull(ProgressSteps.minutesArg(ProgressStepId.PREPARE_CODE))
        assertNull(ProgressSteps.minutesArg(ProgressStepId.SEND_CODE))
    }

    @Test
    fun `PROF-16 PROFILE_SWITCH ma kroki jak CARBS w kazdej fazie`() {
        SessionPhase.entries.forEach { phase ->
            assertEquals(
                ProgressSteps.of(CommandId.CARBS, phase),
                ProgressSteps.of(CommandId.PROFILE_SWITCH, phase),
                "faza $phase",
            )
        }
    }

    @Test
    fun `PROF-16 PROFILE_STATUS i PROFILE_LIST maja kroki jak BG w kazdej fazie`() {
        SessionPhase.entries.forEach { phase ->
            assertEquals(ProgressSteps.of(CommandId.BG, phase), ProgressSteps.of(CommandId.PROFILE_STATUS, phase))
            assertEquals(ProgressSteps.of(CommandId.BG, phase), ProgressSteps.of(CommandId.PROFILE_LIST, phase))
        }
    }

    @Test
    fun `CMDUI-18 tryb anulowania zalezy od fazy`() {
        val cancel = listOf(
            SessionPhase.NOT_STARTED, SessionPhase.SENDING_COMMAND, SessionPhase.AWAITING_REPLY,
            SessionPhase.AWAITING_CODE_REQUEST, SessionPhase.PREPARING_CODE,
        )
        cancel.forEach { assertEquals(CancelMode.CANCEL, it.cancelMode(), "faza $it") }
        assertEquals(CancelMode.STOP_WAITING, SessionPhase.SENDING_CODE.cancelMode())
        assertEquals(CancelMode.STOP_WAITING, SessionPhase.AWAITING_CONFIRMATION.cancelMode())
        assertEquals(CancelMode.NONE, SessionPhase.FINISHED.cancelMode())
    }
}
