package pl.twojanazwa.aapsremote.commands

/** Parametr polecenia jako dane (CMD-01, CMD-06). Tylko stałe katalogu. */
sealed interface CommandParameter {
    data object None : CommandParameter

    /** Ilość insuliny w setnych jednostki (BOL-01). */
    data class InsulinUnits(
        val minCentiUnits: Int,
        val stepCentiUnits: Int,
    ) : CommandParameter

    /** Numer i nazwa z listy profili oraz procent (PROF-01). */
    data class ProfileAndPercent(
        val minPercent: Int,
        val maxPercent: Int,
        val defaultPercent: Int,
    ) : CommandParameter

    data class Grams(
        val min: Int,
        val quickValues: List<Int>,
    ) : CommandParameter
}
