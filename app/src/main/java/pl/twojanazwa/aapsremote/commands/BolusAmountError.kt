package pl.twojanazwa.aapsremote.commands

/** Rodzaje błędów tekstu ilości insuliny (BOL-03). Bez tekstu wejściowego. */
enum class BolusAmountError { EMPTY, INVALID_CHARACTERS, TOO_MANY_DECIMALS, BELOW_MIN, ABOVE_MAX, NOT_STEP }
