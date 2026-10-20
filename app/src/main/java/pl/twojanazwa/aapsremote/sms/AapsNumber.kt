package pl.twojanazwa.aapsremote.sms

/**
 * Poprawny numer AAPS z ustawień (SMS-01, SMS-02). Nie loguje numeru: [toString] jest stałym tekstem.
 */
class AapsNumber private constructor(
    /** Adres docelowy: numer bez znaków białych, '-', '(', ')'; opcjonalny '+' i cyfry ASCII. */
    val destination: String,
    /** Ostatnie 9 cyfr. */
    private val suffix: String,
) {
    /** SMS-02: null / litera / mniej niż 9 cyfr daje false; inaczej ostatnie 9 cyfr == suffix. */
    fun matchesSender(sender: String?): Boolean {
        if (sender == null || sender.any { it.isLetter() }) return false
        val digits = sender.filter { it in '0'..'9' }
        return digits.length >= SUFFIX_DIGITS && digits.takeLast(SUFFIX_DIGITS) == suffix
    }

    override fun toString(): String = "AapsNumber(***)"

    companion object {
        const val SUFFIX_DIGITS: Int = 9

        private val FORMAT = Regex("^\\+?[0-9]+$")

        /** null = nieprawidłowa konfiguracja (SMS-01). Nie rzuca. */
        fun parse(raw: String?): AapsNumber? {
            if (raw == null) return null
            val candidate = raw.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
            if (!FORMAT.matches(candidate)) return null
            if (candidate.count { it in '0'..'9' } < SUFFIX_DIGITS) return null
            return AapsNumber(candidate, candidate.takeLast(SUFFIX_DIGITS))
        }
    }
}
