package pl.twojanazwa.aapsremote.data

/** Wspólna atrapa magazynu ustawień (zad. 1a, rozszerzana w 1b). */
class InMemorySettingsStorage(var value: String? = null) : SettingsStorage {
    var readCount = 0
    var writeCount = 0
    var removeCount = 0
    var lastWritten: String? = null

    var readThrows: Throwable? = null
    var writeThrows: Throwable? = null
    var removeThrows: Throwable? = null
    var writeResult = true
    var removeResult = true

    /** Wołane po udanym zapisie/usunięciu, przed zwróceniem wyniku (np. anulowanie korutyny). */
    var afterWrite: () -> Unit = {}
    var afterRemove: () -> Unit = {}

    override fun readAapsNumber(): String? {
        readCount++
        readThrows?.let { throw it }
        return value
    }

    override fun writeAapsNumber(value: String): Boolean {
        writeCount++
        writeThrows?.let { throw it }
        if (!writeResult) return false
        this.value = value
        lastWritten = value
        afterWrite()
        return true
    }

    override fun removeAapsNumber(): Boolean {
        removeCount++
        removeThrows?.let { throw it }
        if (!removeResult) return false
        value = null
        afterRemove()
        return true
    }
}
