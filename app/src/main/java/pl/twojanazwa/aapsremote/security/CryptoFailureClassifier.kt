package pl.twojanazwa.aapsremote.security

import javax.crypto.AEADBadTagException
import kotlin.coroutines.cancellation.CancellationException

/**
 * SECRET-07. Blokada telefonu jest sprawdzana przed typem wyjątku; każda niepewność daje wynik
 * przejściowy (bez kasowania sekretu). `CancellationException` jest zawsze przepuszczany (SECRET-17).
 */
object CryptoFailureClassifier {
    const val MAX_CHAIN: Int = 8

    fun classify(error: Throwable, environment: KeystoreEnvironment): CryptoFailure {
        lockedFailure(environment)?.let { return it }
        val chain = causeChain(error, MAX_CHAIN)
        return when {
            chain.any { matches { environment.isUserNotAuthenticated(it) } } -> CryptoFailure.LOCKED
            chain.any { matches { environment.isPermanentlyInvalidated(it) } } -> CryptoFailure.KEY_INVALIDATED
            chain.any { it is AEADBadTagException } -> CryptoFailure.CORRUPTED
            else -> CryptoFailure.UNAVAILABLE
        }
    }

    fun classifyMissingKey(environment: KeystoreEnvironment): CryptoFailure =
        lockedFailure(environment) ?: CryptoFailure.KEY_INVALIDATED

    /** Reguły 1 i 2: `UNAVAILABLE`, gdy sprawdzenie blokady rzuca; `LOCKED`, gdy zablokowany; inaczej null. */
    private fun lockedFailure(environment: KeystoreEnvironment): CryptoFailure? {
        val locked = try {
            environment.isDeviceLocked()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CryptoFailure.UNAVAILABLE
        }
        return if (locked) CryptoFailure.LOCKED else null
    }

    /** Wyjątek predykatu = „nie pasuje” (poza CancellationException). */
    private inline fun matches(predicate: () -> Boolean): Boolean = try {
        predicate()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
