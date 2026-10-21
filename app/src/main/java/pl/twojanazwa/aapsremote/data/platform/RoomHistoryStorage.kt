package pl.twojanazwa.aapsremote.data.platform

import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.twojanazwa.aapsremote.data.HistoryRow
import pl.twojanazwa.aapsremote.data.HistoryStorage

/** Port [HistoryStorage] nad Room. Cienka delegacja, bez decyzji (DATA-10 – DATA-12). */
class RoomHistoryStorage @Inject constructor(
    private val database: AapsRemoteDatabase,
    private val dao: HistoryDao,
) : HistoryStorage {
    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }

    override suspend fun insert(row: HistoryRow): Long = dao.insert(HistoryEntity.fromRow(row))

    override suspend fun count(): Int = dao.count()

    override suspend fun deleteOldest(count: Int) = dao.deleteOldest(count)

    override fun observeNewestFirst(): Flow<List<HistoryRow>> =
        dao.observeNewestFirst().map { list -> list.map { it.toRow() } }

    override suspend fun deleteAll() = dao.deleteAll()

    override fun toString(): String = "RoomHistoryStorage(***)"
}
