package pl.twojanazwa.aapsremote.commands

/** Wynik walidacji ilości CARBS. Nie przechowuje tekstu wejściowego (CMD-09). */
sealed interface CarbsValidation {
    /** toString() zawiera tylko zredagowane polecenie. */
    data class Valid(val command: Command.Carbs) : CarbsValidation

    /** Tylko rodzaj błędu, bez tekstu wejściowego. */
    data class Invalid(val error: CarbsAmountError) : CarbsValidation
}

enum class CarbsAmountError { EMPTY, INVALID_CHARACTERS, BELOW_MIN, ABOVE_MAX }
