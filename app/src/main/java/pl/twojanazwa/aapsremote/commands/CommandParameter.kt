package pl.twojanazwa.aapsremote.commands

/** Parametr polecenia jako dane (CMD-01, CMD-06). Tylko stałe katalogu. */
sealed interface CommandParameter {
    data object None : CommandParameter

    /** Ilość insuliny w setnych jednostki (BOL-01). */
    data class InsulinUnits(
        val minCentiUnits: Int,
        val stepCentiUnits: Int,
    ) : CommandParameter

    /** Procent zmiany profilu; numer profilu jest stały i równy 1 (PROF-01). */
    data class Percent(
        val minPercent: Int,
        val maxPercent: Int,
        val defaultPercent: Int,
    ) : CommandParameter

    data class Grams(
        val min: Int,
        val quickValues: List<Int>,
    ) : CommandParameter
}
