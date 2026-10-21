package pl.twojanazwa.aapsremote.ui.settings.number

data class AapsNumberUiState(
    val display: NumberDisplay = NumberDisplay.Loading,
    val editing: Boolean = false,
    val input: String = "",
    val inputError: NumberInputError? = null,
    val busy: Boolean = false,
    val commandActive: Boolean = false,
    val confirmingClear: Boolean = false,
    val message: NumberMessage? = null,
) {
    private val idle: Boolean get() = !busy && !commandActive
    val canSet: Boolean
        get() = idle && (display is NumberDisplay.NotConfigured || display is NumberDisplay.LoadFailed)
    val canChange: Boolean get() = idle && display is NumberDisplay.Configured
    val canClear: Boolean get() = idle && display is NumberDisplay.Configured
    val canSave: Boolean get() = idle && editing

    /** UI-21: bez numeru (display) i wpisanego tekstu (input). */
    override fun toString(): String =
        "AapsNumberUiState(display=$display, editing=$editing, inputLength=${input.length}, " +
            "inputError=$inputError, busy=$busy, commandActive=$commandActive, " +
            "confirmingClear=$confirmingClear, message=$message)"
}
