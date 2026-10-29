package pl.twojanazwa.aapsremote.data.platform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import pl.twojanazwa.aapsremote.data.CommandLimitsStorage

/**
 * Limity poleceń w pliku SharedPreferences `command_limits` (LIM-04). Zapis trzech kluczy
 * jednym `commit()` (bez `apply()`), żeby znać wynik. Bez logowania.
 */
class SharedPreferencesCommandLimitsStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : CommandLimitsStorage {

    override fun readInt(key: String): Int? {
        val prefs = prefs()
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    override fun writeAll(carbsMaxGrams: Int, bolusMaxCentiUnits: Int, bolusMinIntervalMinutes: Int): Boolean =
        prefs().edit()
            .putInt(CommandLimitsStorage.KEY_CARBS_MAX_GRAMS, carbsMaxGrams)
            .putInt(CommandLimitsStorage.KEY_BOLUS_MAX_CENTI_UNITS, bolusMaxCentiUnits)
            .putInt(CommandLimitsStorage.KEY_BOLUS_MIN_INTERVAL_MINUTES, bolusMinIntervalMinutes)
            .commit()

    private fun prefs() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val FILE_NAME = "command_limits"
    }
}
