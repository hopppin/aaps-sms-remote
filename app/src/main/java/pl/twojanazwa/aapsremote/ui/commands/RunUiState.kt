package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.CommandId

/** Przebieg albo wynik bieżącego uruchomienia; bez referencji do sesji i bez treści SMS (CMDUI-21, CMDUI-26). */
sealed interface RunView {
    val commandId: CommandId
    val commandGrams: Int?
    val commandCentiUnits: Int?
    val profileNumber: Int?
    val profilePercent: Int?

    data class Active(
        override val commandId: CommandId,
        override val commandGrams: Int?,
        override val commandCentiUnits: Int?,
        val steps: List<ProgressStep>,
        val cancelMode: CancelMode,
        val unexpectedCount: Int,
        override val profileNumber: Int? = null,
        override val profilePercent: Int? = null,
    ) : RunView {
        /** CMDUI-26: bez ilości. */
        override fun toString(): String = "Active(commandId=$commandId, cancelMode=$cancelMode, ***)"
    }

    data class Finished(val result: ResultView) : RunView {
        override val commandId: CommandId get() = result.commandId
        override val commandGrams: Int? get() = result.commandGrams
        override val commandCentiUnits: Int? get() = result.commandCentiUnits
        override val profileNumber: Int? get() = result.profileNumber
        override val profilePercent: Int? get() = result.profilePercent
    }
}

data class RunUiState(
    val view: RunView? = null,
    val commandActive: Boolean = false,
    /** PROF-14: wybór pozycji listy jest możliwy tylko bez ABSENT / INVALIDATED. */
    val secret: SecretReadiness = SecretReadiness.UNKNOWN,
    val confirmingStopWaiting: Boolean = false,
    val confirmingExit: Boolean = false,
    /** Jednorazowe żądanie Activity.finish() (CMDUI-24); kasowane przez onExitHandled. */
    val exitRequested: Boolean = false,
) {
    /** CMDUI-22. */
    val keepScreenOn: Boolean get() = commandActive

    /** CMDUI-23: przebieg i wynik; to samo pole wybiera treść ekranu przebiegu. */
    val secureWindow: Boolean get() = view != null
    val interceptsBack: Boolean get() = view != null
}
