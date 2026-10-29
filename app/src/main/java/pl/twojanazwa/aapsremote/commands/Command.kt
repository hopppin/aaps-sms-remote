package pl.twojanazwa.aapsremote.commands

/** Poprawne polecenie gotowe do wysłania. */
sealed interface Command {
    val id: CommandId

    /** CMD-05, LIM-07; false dla BG i PUMP. */
    fun requiresExtraConfirmation(limits: CommandLimits): Boolean

    data object Bg : Command {
        override val id: CommandId = CommandId.BG
        override fun requiresExtraConfirmation(limits: CommandLimits): Boolean = false
    }

    data object Pump : Command {
        override val id: CommandId = CommandId.PUMP
        override fun requiresExtraConfirmation(limits: CommandLimits): Boolean = false
    }

    /** Tylko przez of()/parse() – konstruktor prywatny (CMD-03). toString() bez ilości (CMD-09). */
    class Carbs private constructor(val grams: Int) : Command {
        override val id: CommandId = CommandId.CARBS
        override fun requiresExtraConfirmation(limits: CommandLimits): Boolean =
            grams > limits.carbsExtraConfirmationAboveGrams

        override fun equals(other: Any?): Boolean = other is Carbs && other.grams == grams

        override fun hashCode(): Int = grams

        override fun toString(): String = "Command.Carbs(***)"

        companion object {
            /** CMD-03, LIM-06. Nigdy nie rzuca. */
            fun of(grams: Int, limits: CommandLimits): CarbsValidation = when {
                grams < CarbsLimits.MIN_GRAMS -> CarbsValidation.Invalid(CarbsAmountError.BELOW_MIN)
                grams > limits.carbsMaxGrams -> CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX)
                else -> CarbsValidation.Valid(Carbs(grams))
            }

            /** CMD-04, LIM-06. Nigdy nie rzuca. Bez trim(). */
            fun parse(input: String, limits: CommandLimits): CarbsValidation {
                if (input.isEmpty()) return CarbsValidation.Invalid(CarbsAmountError.EMPTY)
                if (input.any { it !in '0'..'9' }) {
                    return CarbsValidation.Invalid(CarbsAmountError.INVALID_CHARACTERS)
                }
                val significant = input.trimStart('0')
                if (significant.isEmpty()) return of(0, limits)
                if (significant.length > 3) return CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX)
                return of(significant.toInt(), limits)
            }
        }
    }
}
