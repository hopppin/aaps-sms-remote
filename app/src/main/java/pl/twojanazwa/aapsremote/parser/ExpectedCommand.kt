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

    /** Rzuca IllegalArgumentException dla centiUnits < 1 (komunikat bez danych SMS). */
    class Bolus(val centiUnits: Int) : ExpectedCommand {
        init {
            require(centiUnits >= 1) { "centiUnits must be >= 1" }
        }

        override fun equals(other: Any?): Boolean = other is Bolus && other.centiUnits == centiUnits

        override fun hashCode(): Int = centiUnits

        override fun toString(): String = "ExpectedCommand.Bolus(***)"
    }

    data object ProfileStatus : ExpectedCommand

    data object ProfileList : ExpectedCommand

    /** Rzuca IllegalArgumentException dla pustej nazwy, nazwy z LF albo percent < 1 (komunikaty bez danych). */
    class ProfileSwitch(val profileName: String, val percent: Int) : ExpectedCommand {
        init {
            require(profileName.isNotEmpty() && '\n' !in profileName) { "invalid profile name" }
            require(percent >= 1) { "percent must be >= 1" }
        }

        override fun equals(other: Any?): Boolean =
            other is ProfileSwitch && other.profileName == profileName && other.percent == percent

        override fun hashCode(): Int = 31 * profileName.hashCode() + percent

        override fun toString(): String = "ExpectedCommand.ProfileSwitch(***)"
    }
}
