package pl.twojanazwa.aapsremote.security

class TestKeyInvalidatedException(message: String = "test", cause: Throwable? = null) : Exception(message, cause)

class TestUserNotAuthenticatedException(message: String = "test", cause: Throwable? = null) : Exception(message, cause)

/** Wspólna atrapa środowiska Keystore dla testów spec 006. */
class FakeKeystoreEnvironment(
    var locked: Boolean = false,
    /** Gdy ustawiony, `isDeviceLocked` go rzuca. */
    var lockedCheckError: Throwable? = null,
    /** Gdy ustawiony, oba predykaty go rzucają. */
    var predicateError: Throwable? = null,
) : KeystoreEnvironment {

    override fun isDeviceLocked(): Boolean {
        lockedCheckError?.let { throw it }
        return locked
    }

    override fun isPermanentlyInvalidated(error: Throwable): Boolean {
        predicateError?.let { throw it }
        return error is TestKeyInvalidatedException
    }

    override fun isUserNotAuthenticated(error: Throwable): Boolean {
        predicateError?.let { throw it }
        return error is TestUserNotAuthenticatedException
    }
}
