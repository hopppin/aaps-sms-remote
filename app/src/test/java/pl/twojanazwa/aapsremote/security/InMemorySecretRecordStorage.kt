package pl.twojanazwa.aapsremote.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Wspólna atrapa magazynu klucz-wartość dla testów spec 006. */
class InMemorySecretRecordStorage(var record: String? = null) : SecretRecordStorage {
    var readCount = 0
        private set
    var writeCount = 0
        private set
    var clearCount = 0
        private set

    /** Wynik zwracany przez [write]; przy false rekord nie jest zmieniany (chyba że [writeChangesValueButReturnsFalse]). */
    var writeResult = true
    var clearResult = true

    /** Tryb „zapis zmienia wartość, ale zwraca false”. */
    var writeChangesValueButReturnsFalse = false

    var readError: Exception? = null
    var writeError: Exception? = null
    var clearError: Exception? = null

    /** Akcja wywoływana na początku [write] (np. anulowanie korutyny). */
    var onWrite: (() -> Unit)? = null

    /** Numery wywołań [read] (1 = pierwsze od początku życia atrapy), przy których [read] rzuca [readErrorOnCall]. */
    var readErrorOnCalls: Set<Int> = emptySet()
    var readErrorOnCall: Exception = IllegalStateException("test")

    /** Tryb „pierwsze N zapisów zmienia wartość i zwraca false, kolejne działają normalnie”. */
    var failFirstWritesWithChange = 0

    /** Tryb „pierwsze N zapisów zmienia wartość i rzuca RuntimeException” (wyjątek po zmianie stanu). */
    var failFirstWritesWithChangeAndThrow = 0

    /** Opcjonalny licznik równoczesnych wywołań i rejestr nazw wątków (SECRET-13). */
    var probe: ConcurrencyProbe? = null

    /** Gdy ustawiony, [write] blokuje wątek do zwolnienia zatrzasku przez test (maks. 10 s). */
    var writeGate: CountDownLatch? = null

    /** Odliczany, gdy [write] zaczyna działać (po [onWrite]). */
    val writeEntered = CountDownLatch(1)

    /** Rejestr wartości przekazanych do [write] (kolejno). */
    val writes = mutableListOf<String>()

    override fun read(): String? = tracked { readImpl() }

    override fun write(record: String): Boolean = tracked { writeImpl(record) }

    override fun clear(): Boolean = tracked { clearImpl() }

    private fun <T> tracked(block: () -> T): T = probe?.track(block) ?: block()

    private fun readImpl(): String? {
        readCount++
        readError?.let { throw it }
        if (readCount in readErrorOnCalls) throw readErrorOnCall
        return record
    }

    private fun writeImpl(record: String): Boolean {
        writeCount++
        writes += record
        onWrite?.invoke()
        writeEntered.countDown()
        writeGate?.await(10, TimeUnit.SECONDS)
        writeError?.let { throw it }
        if (failFirstWritesWithChangeAndThrow > 0) {
            failFirstWritesWithChangeAndThrow--
            this.record = record
            throw RuntimeException("test: zapis zmienił wartość i rzucił")
        }
        if (failFirstWritesWithChange > 0) {
            failFirstWritesWithChange--
            this.record = record
            return false
        }
        if (writeResult || writeChangesValueButReturnsFalse) this.record = record
        return writeResult
    }

    private fun clearImpl(): Boolean {
        clearCount++
        clearError?.let { throw it }
        if (clearResult) record = null
        return clearResult
    }
}
