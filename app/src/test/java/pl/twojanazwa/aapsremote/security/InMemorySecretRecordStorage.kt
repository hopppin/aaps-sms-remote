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

    override fun read(): String? {
        readCount++
        readError?.let { throw it }
        return record
    }

    override fun write(record: String): Boolean {
        writeCount++
        writeError?.let { throw it }
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
