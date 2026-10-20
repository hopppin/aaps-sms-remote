package pl.twojanazwa.aapsremote.security

import java.util.IdentityHashMap

/**
 * Wyjątek i kolejne `cause`, najwyżej [maxSize] elementów; przerwane przy powtórzeniu obiektu
 * (porównanie tożsamości).
 */
internal fun causeChain(error: Throwable, maxSize: Int = CryptoFailureClassifier.MAX_CHAIN): List<Throwable> {
    val seen = IdentityHashMap<Throwable, Unit>()
    val result = ArrayList<Throwable>()
    var current: Throwable? = error
    while (current != null && result.size < maxSize && seen.put(current, Unit) == null) {
        result.add(current)
        current = current.cause
    }
    return result
}
