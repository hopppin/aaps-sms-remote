package pl.twojanazwa.aapsremote.security

import kotlin.coroutines.cancellation.CancellationException

/** SECRET-02: StrongBox, a jedna próba TEE tylko przy „StrongBox niedostępny”. */
object KeyGenerationPolicy {
    /**
     * `attempt(true)`; gdy w łańcuchu przyczyn wyjątku jest „StrongBox niedostępny” – dokładnie jedno
     * `attempt(false)`. Inne wyjątki oraz wyjątek drugiej próby są propagowane.
     */
    fun <K> generate(
        attempt: (strongBox: Boolean) -> K,
        isStrongBoxUnavailable: (Throwable) -> Boolean,
    ): K = try {
        attempt(true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (causeChain(e).any { isStrongBoxUnavailable(it) }) attempt(false) else throw e
    }
}
