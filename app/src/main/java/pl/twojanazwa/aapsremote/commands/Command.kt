package pl.twojanazwa.aapsremote.commands

/** Poprawne polecenie gotowe do wysłania. */
sealed interface Command {
    val id: CommandId

    /** CMD-05; false dla BG i PUMP. */
    val requiresExtraConfirmation: Boolean

    data object Bg : Command {
        override val id: CommandId = CommandId.BG
        override val requiresExtraConfirmation: Boolean = false
    }

    data object Pump : Command {
        override val id: CommandId = CommandId.PUMP
        override val requiresExtraConfirmation: Boolean = false
    }

    /** Tylko przez of()/parse() – konstruktor prywatny (CMD-03). toString() bez ilości (CMD-09). */
    class Carbs private constructor(val grams: Int) : Command {
        override val id: CommandId = CommandId.CARBS
        override val requiresExtraConfirmation: Boolean
            get() = grams > CarbsLimits.EXTRA_CONFIRMATION_ABOVE_GRAMS

        override fun equals(other: Any?): Boolean = other is Carbs && other.grams == grams

        override fun hashCode(): Int = grams

        override fun toString(): String = "Command.Carbs(***)"

        companion object {
            /** CMD-03. Nigdy nie rzuca. */
            fun of(grams: Int): CarbsValidation = when {
                grams < CarbsLimits.MIN_GRAMS -> CarbsValidation.Invalid(CarbsAmountError.BELOW_MIN)
                grams > CarbsLimits.MAX_GRAMS -> CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX)
                else -> CarbsValidation.Valid(Carbs(grams))
            }

            /** CMD-04. Nigdy nie rzuca. Bez trim(). */
            fun parse(input: String): CarbsValidation {
                if (input.isEmpty()) return CarbsValidation.Invalid(CarbsAmountError.EMPTY)
                if (input.any { it !in '0'..'9' }) {
                    return CarbsValidation.Invalid(CarbsAmountError.INVALID_CHARACTERS)
                }
                val significant = input.trimStart('0')
                if (significant.isEmpty()) return of(0)
                if (significant.length > 2) return CarbsValidation.Invalid(CarbsAmountError.ABOVE_MAX)
                return of(significant.toInt())
            }
        }
    }
}
