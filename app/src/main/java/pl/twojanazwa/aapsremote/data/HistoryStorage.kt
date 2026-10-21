package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.flow.Flow

/** Port platformy dla historii. Funkcje suspend są bezpieczne dla wątku głównego (Room). Mogą rzucać. */
interface HistoryStorage {
    /** Jedna transakcja bazy; wyjątek z [block] = wycofanie i ten sam wyjątek na zewnątrz. */
    suspend fun <R> inTransaction(block: suspend () -> R): R

    /** Zwraca nadany identyfikator. */
    suspend fun insert(row: HistoryRow): Long

    suspend fun count(): Int

    /** Usuwa [count] wierszy o najmniejszych identyfikatorach. */
    suspend fun deleteOldest(count: Int)

    /** Wszystkie wiersze, malejący identyfikator; nowa lista po każdej zmianie tabeli. */
    fun observeNewestFirst(): Flow<List<HistoryRow>>

    suspend fun deleteAll()
}
