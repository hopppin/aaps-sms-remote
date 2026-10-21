package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Wspolna atrapa magazynu historii. nextId nigdy nie maleje (jak AUTOINCREMENT),
 * inTransaction przywraca kopie listy przy wyjatku, kazde wywolanie notuje glebokosc transakcji.
 */
class InMemoryHistoryStorage : HistoryStorage {
    private val rows = MutableStateFlow<List<HistoryRow>>(emptyList())
    var nextId: Long = 1
        private set
    private var depth = 0

    /** Liczba wywolan inTransaction. */
    var transactionCalls = 0
        private set

    /** Notatki "metoda:w_transakcji" w kolejnosci wywolan. */
    val calls = mutableListOf<String>()

    var insertFailure: Exception? = null
    var countFailure: Exception? = null
    var deleteOldestFailure: Exception? = null
    var deleteAllFailure: Exception? = null

    /** Rzucany synchronicznie przy wywolaniu observeNewestFirst(). */
    var observeCreationFailure: Exception? = null

    /** Rzucany przy pierwszym odbiorze ze strumienia. */
    var observeStreamFailure: Exception? = null

    val current: List<HistoryRow> get() = rows.value

    private fun note(name: String) {
        calls += "$name:${depth > 0}"
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        transactionCalls++
        note("inTransaction")
        val snapshot = rows.value
        depth++
        try {
            return block()
        } catch (e: Throwable) {
            // wycofanie listy; nextId zostaje (AUTOINCREMENT nie cofa sie po wycofaniu)
            rows.value = snapshot
            throw e
        } finally {
            depth--
        }
    }

    override suspend fun insert(row: HistoryRow): Long {
        note("insert")
        insertFailure?.let { throw it }
        val id = nextId++
        rows.value = rows.value + row.copy(id = id)
        return id
    }

    override suspend fun count(): Int {
        note("count")
        countFailure?.let { throw it }
        return rows.value.size
    }

    override suspend fun deleteOldest(count: Int) {
        note("deleteOldest")
        deleteOldestFailure?.let { throw it }
        val ids = rows.value.sortedBy { it.id }.take(count).map { it.id }.toSet()
        rows.value = rows.value.filter { it.id !in ids }
    }

    override fun observeNewestFirst(): Flow<List<HistoryRow>> {
        observeCreationFailure?.let { throw it }
        return rows.map { list ->
            observeStreamFailure?.let { throw it }
            list.sortedByDescending { it.id }
        }
    }

    override suspend fun deleteAll() {
        note("deleteAll")
        deleteAllFailure?.let { throw it }
        rows.value = emptyList()
    }

    /** Wstawia [count] wierszy bez notowania wywolan; zwraca nadane identyfikatory. */
    fun seed(count: Int, template: HistoryRow = HistoryCodec.toRow(HistoryFixtures.bgCompleted())): List<Long> {
        val ids = (1..count).map { nextId++ }
        rows.value = rows.value + ids.map { template.copy(id = it) }
        return ids
    }
}
