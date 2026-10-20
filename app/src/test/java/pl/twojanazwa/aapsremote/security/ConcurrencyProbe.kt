package pl.twojanazwa.aapsremote.security

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Licznik równocześnie trwających wywołań portów i rejestr nazw wątków (testy SECRET-13). */
class ConcurrencyProbe {
    private val active = AtomicInteger()
    private val maxActive = AtomicInteger()
    private val names = ConcurrentLinkedQueue<String>()

    val max: Int get() = maxActive.get()
    val threadNames: List<String> get() = names.toList()

    fun <T> track(block: () -> T): T {
        val now = active.incrementAndGet()
        maxActive.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        names.add(Thread.currentThread().name)
        try {
            return block()
        } finally {
            active.decrementAndGet()
        }
    }
}
