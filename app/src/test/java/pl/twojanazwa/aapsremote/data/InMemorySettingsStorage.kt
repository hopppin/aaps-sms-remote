package pl.twojanazwa.aapsremote.data

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Wspólna atrapa magazynu ustawień (zad. 1a, rozszerzona w 1b). */
class InMemorySettingsStorage(@Volatile var value: String? = null) : SettingsStorage {
    @Volatile var readCount = 0
    @Volatile var writeCount = 0
    @Volatile var removeCount = 0
    @Volatile var lastWritten: String? = null

    var readThrows: Throwable? = null
    var writeThrows: Throwable? = null
    var removeThrows: Throwable? = null
    var writeResult = true
    var removeResult = true

    /** Wołane po udanym zapisie/usunięciu, przed zwróceniem wyniku (np. anulowanie korutyny). */
    var afterWrite: () -> Unit = {}
    var afterRemove: () -> Unit = {}

    /** Gdy ustawione, zapis czeka na zwolnienie blokady (po zasygnalizowaniu [writeEntered]). */
    @Volatile var writeGate: CountDownLatch? = null
    val writeEntered = CountDownLatch(1)

    /** Nazwy wątków każdego wywołania. */
    val threadNames = CopyOnWriteArrayList<String>()

    private val inFlight = AtomicInteger(0)
    private val maxInFlightValue = AtomicInteger(0)

    /** Maksymalna liczba równocześnie trwających wywołań. */
    val maxInFlight: Int get() = maxInFlightValue.get()

    private inline fun <T> tracked(block: () -> T): T {
        threadNames.add(Thread.currentThread().name)
        val now = inFlight.incrementAndGet()
        maxInFlightValue.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try {
            return block()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    override fun readAapsNumber(): String? = tracked {
        readCount++
        readThrows?.let { throw it }
        value
    }

    override fun writeAapsNumber(value: String): Boolean = tracked {
        writeCount++
        writeThrows?.let { throw it }
        writeEntered.countDown()
        writeGate?.await()
        if (!writeResult) return@tracked false
        this.value = value
        lastWritten = value
        afterWrite()
        true
    }

    override fun removeAapsNumber(): Boolean = tracked {
        removeCount++
        removeThrows?.let { throw it }
        if (!removeResult) return@tracked false
        value = null
        afterRemove()
        true
    }
}
