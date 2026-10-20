package pl.twojanazwa.aapsremote.security.platform

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.twojanazwa.aapsremote.security.KeystoreEnvironment
import javax.inject.Inject

/** Fakty o Androidzie dla klasyfikacji błędów (SECRET-07). Bez decyzji i bez logowania (SECRET-15). */
class AndroidKeystoreEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) : KeystoreEnvironment {

    override fun isDeviceLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked
            ?: throw IllegalStateException("KeyguardManager unavailable")

    override fun isPermanentlyInvalidated(error: Throwable): Boolean =
        error is KeyPermanentlyInvalidatedException

    override fun isUserNotAuthenticated(error: Throwable): Boolean =
        error is UserNotAuthenticatedException
}
