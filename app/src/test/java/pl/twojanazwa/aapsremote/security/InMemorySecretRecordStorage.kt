package pl.twojanazwa.aapsremote.security

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

    /** Rejestr wartości przekazanych do [write] (kolejno). */
    val writes = mutableListOf<String>()

    override fun read(): String? {
        readCount++
        readError?.let { throw it }
        if (readCount in readErrorOnCalls) throw readErrorOnCall
        return record
    }

    override fun write(record: String): Boolean {
        writeCount++
        writes += record
        onWrite?.invoke()
        writeError?.let { throw it }
        if (failFirstWritesWithChange > 0) {
            failFirstWritesWithChange--
            this.record = record
            return false
        }
        if (writeResult || writeChangesValueButReturnsFalse) this.record = record
        return writeResult
    }

    override fun clear(): Boolean {
        clearCount++
        clearError?.let { throw it }
        if (clearResult) record = null
        return clearResult
    }
}
