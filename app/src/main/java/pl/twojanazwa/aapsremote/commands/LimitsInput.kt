package pl.twojanazwa.aapsremote.commands

enum class LimitField { CARBS_MAX, BOLUS_MAX, BOLUS_INTERVAL }

enum class LimitValueError { EMPTY, INVALID_CHARACTERS, TOO_MANY_DECIMALS, BELOW_MIN, ABOVE_MAX, NOT_STEP }

sealed interface LimitsValidation {
    /** toString() bez wartości (LIM-12): CommandLimits.toString() jest zredagowane. */
    data class Valid(val limits: CommandLimits) : LimitsValidation

    /** Błąd dla każdego błędnego pola; bez tekstu wejściowego. */
    data class Invalid(val errors: Map<LimitField, LimitValueError>) : LimitsValidation
}

/** LIM-03: tekst pól -> LimitsValidation. Nigdy nie rzuca. */
object LimitsInput {
    fun parse(carbsMax: String, bolusMax: String, interval: String): LimitsValidation {
        val carbs = intField(carbsMax, LimitRanges.CARBS_MAX_MIN, LimitRanges.CARBS_MAX_MAX)
        val bolus = bolusField(bolusMax)
        val minutes = intField(interval, LimitRanges.INTERVAL_MIN, LimitRanges.INTERVAL_MAX)
        val errors = buildMap {
            if (carbs is Field.Bad) put(LimitField.CARBS_MAX, carbs.error)
            if (bolus is Field.Bad) put(LimitField.BOLUS_MAX, bolus.error)
            if (minutes is Field.Bad) put(LimitField.BOLUS_INTERVAL, minutes.error)
        }
        if (errors.isNotEmpty()) return LimitsValidation.Invalid(errors)
        return CommandLimits.of((carbs as Field.Ok).value, (bolus as Field.Ok).value, (minutes as Field.Ok).value)
    }

    private sealed interface Field {
        class Ok(val value: Int) : Field
        class Bad(val error: LimitValueError) : Field
    }

    /** Jak CMD-04: ponad 3 cyfry znaczące to powyżej maksimum (bez przepełnienia). */
    private fun intField(text: String, min: Int, max: Int): Field {
        if (text.isEmpty()) return Field.Bad(LimitValueError.EMPTY)
        if (text.any { it !in '0'..'9' }) return Field.Bad(LimitValueError.INVALID_CHARACTERS)
        val significant = text.trimStart('0')
        if (significant.length > 3) return Field.Bad(LimitValueError.ABOVE_MAX)
        return range(if (significant.isEmpty()) 0 else significant.toInt(), min, max, 1)
    }

    private fun bolusField(text: String): Field = when (val p = BolusAmountText.parse(text)) {
        is BolusAmountText.Parsed.Error -> Field.Bad(
            when (p.error) {
                BolusAmountError.EMPTY -> LimitValueError.EMPTY
                BolusAmountError.INVALID_CHARACTERS -> LimitValueError.INVALID_CHARACTERS
                BolusAmountError.TOO_MANY_DECIMALS -> LimitValueError.TOO_MANY_DECIMALS
                BolusAmountError.BELOW_MIN -> LimitValueError.BELOW_MIN
                BolusAmountError.ABOVE_MAX -> LimitValueError.ABOVE_MAX
                BolusAmountError.NOT_STEP -> LimitValueError.NOT_STEP
            }
        )
        is BolusAmountText.Parsed.Value ->
            range(p.centiUnits, LimitRanges.BOLUS_MAX_MIN, LimitRanges.BOLUS_MAX_MAX, LimitRanges.BOLUS_STEP)
    }

    private fun range(value: Int, min: Int, max: Int, step: Int): Field =
        CommandLimits.rangeError(value, min, max, step)?.let { Field.Bad(it) } ?: Field.Ok(value)
}
