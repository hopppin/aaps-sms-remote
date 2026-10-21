package pl.twojanazwa.aapsremote.data.platform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.twojanazwa.aapsremote.data.SettingsStorage
import javax.inject.Inject

/**
 * Numer AAPS w pliku SharedPreferences `settings` (DATA-01). Zapis i usunięcie przez `commit()`,
 * żeby znać wynik. Bez logowania (DATA-17). Plik jest wykluczony z kopii zapasowej (DATA-16).
 */
class SharedPreferencesSettingsStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStorage {

    override fun readAapsNumber(): String? = prefs().getString(KEY_AAPS_NUMBER, null)

    override fun writeAapsNumber(value: String): Boolean =
        prefs().edit().putString(KEY_AAPS_NUMBER, value).commit()

    override fun removeAapsNumber(): Boolean =
        prefs().edit().remove(KEY_AAPS_NUMBER).commit()

    private fun prefs() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val FILE_NAME = "settings"
        const val KEY_AAPS_NUMBER = "aaps_number"
    }
}
