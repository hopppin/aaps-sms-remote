package pl.twojanazwa.aapsremote.security

/** Fakty o Androidzie potrzebne do klasyfikacji błędów (SECRET-07). Implementacja w warstwie platformy. */
interface KeystoreEnvironment {
    /** `KeyguardManager.isDeviceLocked`. Może rzucić. */
    fun isDeviceLocked(): Boolean

    /** `error is KeyPermanentlyInvalidatedException`. */
    fun isPermanentlyInvalidated(error: Throwable): Boolean

    /** `error is UserNotAuthenticatedException`. */
    fun isUserNotAuthenticated(error: Throwable): Boolean
}
