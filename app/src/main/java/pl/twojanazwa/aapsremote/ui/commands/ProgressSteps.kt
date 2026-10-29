package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionTimeouts

enum class ProgressStepId { SEND_COMMAND, AWAIT_REPLY, AWAIT_CODE_REQUEST, PREPARE_CODE, SEND_CODE, AWAIT_CONFIRMATION }

enum class StepStatus { DONE, CURRENT, PENDING }

data class ProgressStep(val id: ProgressStepId, val status: StepStatus)

object ProgressSteps {
    private val simpleSteps = listOf(ProgressStepId.SEND_COMMAND, ProgressStepId.AWAIT_REPLY)
    private val carbsSteps = listOf(
        ProgressStepId.SEND_COMMAND,
        ProgressStepId.AWAIT_CODE_REQUEST,
        ProgressStepId.PREPARE_CODE,
        ProgressStepId.SEND_CODE,
        ProgressStepId.AWAIT_CONFIRMATION,
    )

    /** CMDUI-17. Faza niemożliwa dla polecenia albo FINISHED → wszystkie PENDING. */
    fun of(command: CommandId, phase: SessionPhase): List<ProgressStep> {
        val ids = if (command == CommandId.CARBS || command == CommandId.BOLUS || command == CommandId.PROFILE_SWITCH) carbsSteps else simpleSteps
        val current = currentIndex(phase)
        return ids.mapIndexed { index, id ->
            val status = when {
                current == null || current > ids.size -> StepStatus.PENDING
                index < current - 1 -> StepStatus.DONE
                index == current - 1 -> StepStatus.CURRENT
                else -> StepStatus.PENDING
            }
            ProgressStep(id, status)
        }
    }

    /** Numer bieżącego kroku (od 1) wg tabeli faz; null dla FINISHED. */
    private fun currentIndex(phase: SessionPhase): Int? = when (phase) {
        SessionPhase.NOT_STARTED, SessionPhase.SENDING_COMMAND -> 1
        SessionPhase.AWAITING_REPLY, SessionPhase.AWAITING_CODE_REQUEST -> 2
        SessionPhase.PREPARING_CODE -> 3
        SessionPhase.SENDING_CODE -> 4
        SessionPhase.AWAITING_CONFIRMATION -> 5
        SessionPhase.FINISHED -> null
    }

    /** Argument minut kroku (AWAIT_REPLY, AWAIT_CODE_REQUEST → reply; AWAIT_CONFIRMATION → confirmation; inne → null). */
    fun minutesArg(id: ProgressStepId, timeouts: SessionTimeouts = SessionTimeouts()): Int? = when (id) {
        ProgressStepId.AWAIT_REPLY, ProgressStepId.AWAIT_CODE_REQUEST -> timeouts.reply.inWholeMinutes.toInt()
        ProgressStepId.AWAIT_CONFIRMATION -> timeouts.confirmation.inWholeMinutes.toInt()
        else -> null
    }
}

enum class CancelMode { CANCEL, STOP_WAITING, NONE }

/** CMDUI-18. Fazy przed kodem → CANCEL; SENDING_CODE, AWAITING_CONFIRMATION → STOP_WAITING; FINISHED → NONE. */
fun SessionPhase.cancelMode(): CancelMode = when (this) {
    SessionPhase.NOT_STARTED, SessionPhase.SENDING_COMMAND, SessionPhase.AWAITING_REPLY,
    SessionPhase.AWAITING_CODE_REQUEST, SessionPhase.PREPARING_CODE -> CancelMode.CANCEL
    SessionPhase.SENDING_CODE, SessionPhase.AWAITING_CONFIRMATION -> CancelMode.STOP_WAITING
    SessionPhase.FINISHED -> CancelMode.NONE
}
