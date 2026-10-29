package pl.twojanazwa.aapsremote.data

/** Blokujący; tylko na @DataDispatcher, pod blokadą. Może rzucać. */
interface CommandLimitsStorage {
    /** null = brak klucza. ClassCastException przy innym typie. */
    fun readInt(key: String): Int?

    /** Wszystkie trzy wartości jednym edit()…commit(). */
    fun writeAll(carbsMaxGrams: Int, bolusMaxCentiUnits: Int, bolusMinIntervalMinutes: Int): Boolean

    companion object {
        const val KEY_CARBS_MAX_GRAMS = "carbs_max_grams"
        const val KEY_BOLUS_MAX_CENTI_UNITS = "bolus_max_centi_units"
        const val KEY_BOLUS_MIN_INTERVAL_MINUTES = "bolus_min_interval_minutes"
    }
}
