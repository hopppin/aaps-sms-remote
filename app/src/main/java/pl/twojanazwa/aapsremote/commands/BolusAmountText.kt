package pl.twojanazwa.aapsremote.commands

/** BOL-03 kroki 1-4: tekst -> setne jednostki albo błąd składni; BOL-05: setne -> tekst SMS. */
object BolusAmountText {
    sealed interface Parsed {
        /** toString() bez ilości. */
        class Value(val centiUnits: Int) : Parsed {
            override fun equals(other: Any?): Boolean = other is Value && other.centiUnits == centiUnits
            override fun hashCode(): Int = centiUnits
            override fun toString(): String = "BolusAmountText.Value(***)"
        }

        data class Error(val error: BolusAmountError) : Parsed
    }

    /** Nigdy nie rzuca; bez trim(). Zakres i krok oceniają wywołujący (limity). */
    fun parse(input: String): Parsed {
        if (input.isEmpty()) return Parsed.Error(BolusAmountError.EMPTY)
        val parts = input.split(',', '.')
        val valid = parts.size <= 2 && parts.all { p -> p.isNotEmpty() && p.all { it in '0'..'9' } }
        if (!valid) return Parsed.Error(BolusAmountError.INVALID_CHARACTERS)
        val fraction = parts.getOrElse(1) { "" }
        if (fraction.length > 2) return Parsed.Error(BolusAmountError.TOO_MANY_DECIMALS)
        val whole = parts[0].trimStart('0')
        if (whole.length > 3) return Parsed.Error(BolusAmountError.ABOVE_MAX)
        val centi = (if (whole.isEmpty()) 0 else whole.toInt()) * 100 + fraction.padEnd(2, '0').toInt()
        return Parsed.Value(centi)
    }

    /** BOL-05: kropka dziesiętna, bez zer wiodących i końcowych. */
    fun toSms(centiUnits: Int): String {
        val whole = centiUnits / 100
        val fraction = (centiUnits % 100).toString().padStart(2, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
    }
}
