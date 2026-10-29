package pl.twojanazwa.aapsremote.commands

/** LIM-01, LIM-02. Tylko przez of(); toString() bez wartości (LIM-12). */
class CommandLimits private constructor(
    val carbsMaxGrams: Int,
    val bolusMaxCentiUnits: Int,
    val bolusMinIntervalMinutes: Int,
) {
    /** LIM-07: floor(carbsMaxGrams * 4 / 5). */
    val carbsExtraConfirmationAboveGrams: Int get() = carbsMaxGrams * 4 / 5

    /** LIM-08: CarbsLimits.QUICK_VALUES <= progu. */
    val carbsQuickValues: List<Int>
        get() = CarbsLimits.QUICK_VALUES.filter { it <= carbsExtraConfirmationAboveGrams }

    /** BOL-04: ilość > połowy limitu, czyli 2 * ilość > bolusMaxCentiUnits (Long - bez przepełnienia). */
    fun bolusRequiresExtraConfirmation(centiUnits: Int): Boolean =
        centiUnits.toLong() * 2 > bolusMaxCentiUnits

    override fun equals(other: Any?): Boolean = other is CommandLimits &&
        other.carbsMaxGrams == carbsMaxGrams &&
        other.bolusMaxCentiUnits == bolusMaxCentiUnits &&
        other.bolusMinIntervalMinutes == bolusMinIntervalMinutes

    override fun hashCode(): Int =
        (carbsMaxGrams * 31 + bolusMaxCentiUnits) * 31 + bolusMinIntervalMinutes

    override fun toString(): String = "CommandLimits(***)"

    companion object {
        val DEFAULT: CommandLimits = CommandLimits(50, 500, 15)

        /** LIM-02. Nigdy nie rzuca. */
        fun of(carbsMaxGrams: Int, bolusMaxCentiUnits: Int, bolusMinIntervalMinutes: Int): LimitsValidation {
            val errors = buildMap {
                rangeError(carbsMaxGrams, LimitRanges.CARBS_MAX_MIN, LimitRanges.CARBS_MAX_MAX, 1)
                    ?.let { put(LimitField.CARBS_MAX, it) }
                rangeError(bolusMaxCentiUnits, LimitRanges.BOLUS_MAX_MIN, LimitRanges.BOLUS_MAX_MAX, LimitRanges.BOLUS_STEP)
                    ?.let { put(LimitField.BOLUS_MAX, it) }
                rangeError(bolusMinIntervalMinutes, LimitRanges.INTERVAL_MIN, LimitRanges.INTERVAL_MAX, 1)
                    ?.let { put(LimitField.BOLUS_INTERVAL, it) }
            }
            return if (errors.isEmpty()) {
                LimitsValidation.Valid(CommandLimits(carbsMaxGrams, bolusMaxCentiUnits, bolusMinIntervalMinutes))
            } else {
                LimitsValidation.Invalid(errors)
            }
        }

        internal fun rangeError(value: Int, min: Int, max: Int, step: Int): LimitValueError? = when {
            value < min -> LimitValueError.BELOW_MIN
            value > max -> LimitValueError.ABOVE_MAX
            value % step != 0 -> LimitValueError.NOT_STEP
            else -> null
        }
    }
}
