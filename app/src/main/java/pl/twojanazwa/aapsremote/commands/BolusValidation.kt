package pl.twojanazwa.aapsremote.commands

/** Wynik walidacji ilości bolusa. Nie przechowuje tekstu wejściowego (BOL-06). */
sealed interface BolusValidation {
    /** toString() zawiera tylko zredagowane polecenie. */
    data class Valid(val command: Command.Bolus) : BolusValidation

    /** Tylko rodzaj błędu, bez tekstu wejściowego. */
    data class Invalid(val error: BolusAmountError) : BolusValidation
}
