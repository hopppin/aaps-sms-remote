package pl.twojanazwa.aapsremote.data.platform

import android.content.Context
import android.content.SharedPreferences
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
        writeWithRollback(
            prefs(),
            mapOf(
                CommandLimitsStorage.KEY_CARBS_MAX_GRAMS to carbsMaxGrams,
                CommandLimitsStorage.KEY_BOLUS_MAX_CENTI_UNITS to bolusMaxCentiUnits,
                CommandLimitsStorage.KEY_BOLUS_MIN_INTERVAL_MINUTES to bolusMinIntervalMinutes,
            ),
        )

    private fun prefs() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val FILE_NAME = "command_limits"

        /**
         * Zapis jednym `commit()`. Gdy zwróci false, Android i tak zmienił pamięć, więc przywracamy
         * stan sprzed zapisu (brak klucza = usunięcie, inaczej dawna wartość tego samego typu).
         */
        internal fun writeWithRollback(prefs: SharedPreferences, values: Map<String, Int>): Boolean {
            val before = prefs.all
            val ok = prefs.edit().apply { values.forEach { (k, v) -> putInt(k, v) } }.commit()
            if (!ok) {
                try {
                    prefs.edit().apply {
                        values.keys.forEach { k ->
                            when (val old = before[k]) {
                                null -> remove(k)
                                is Int -> putInt(k, old)
                                is Long -> putLong(k, old)
                                is Float -> putFloat(k, old)
                                is Boolean -> putBoolean(k, old)
                                is String -> putString(k, old)
                                is Set<*> -> putStringSet(k, old.filterIsInstance<String>().toSet())
                                else -> remove(k)
                            }
                        }
                    }.commit()
                } catch (_: Exception) {
                    // wycofanie best effort; wynik nadal false
                }
            }
            return ok
        }
    }
}
