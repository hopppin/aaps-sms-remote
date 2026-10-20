package pl.twojanazwa.aapsremote.security.platform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.twojanazwa.aapsremote.security.SecretRecordStorage
import javax.inject.Inject

/**
 * Jeden rekord w pliku SharedPreferences (SECRET-16). Zapis i usunięcie przez `commit()`,
 * żeby znać wynik i mieć trwałość przed zakończeniem zamiany (SECRET-05). Bez logowania (SECRET-15).
 */
class SharedPreferencesSecretRecordStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecretRecordStorage {

    override fun read(): String? = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getString(KEY, null)

    override fun write(record: String): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, record)
            .commit()

    override fun clear(): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()

    companion object {
        const val FILE_NAME = "totp_secret_store"
        const val KEY = "sealed_secret"
    }
}
