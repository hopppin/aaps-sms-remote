package pl.twojanazwa.aapsremote.parser

/** Polecenie, na które czekamy odpowiedzi. toString() bez ilości (PARSER-10). */
sealed interface ExpectedCommand {
    data object Bg : ExpectedCommand

    data object Pump : ExpectedCommand

    /** Rzuca IllegalArgumentException dla grams < 1 (komunikat bez danych SMS). */
    class Carbs(val grams: Int) : ExpectedCommand {
        init {
            require(grams >= 1) { "grams must be >= 1" }
        }

        override fun equals(other: Any?): Boolean = other is Carbs && other.grams == grams

        override fun hashCode(): Int = grams

        override fun toString(): String = "ExpectedCommand.Carbs(***)"
    }
}
