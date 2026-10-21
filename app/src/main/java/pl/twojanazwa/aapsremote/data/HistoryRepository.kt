package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import pl.twojanazwa.aapsremote.session.SessionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DATA-10 – DATA-12. Retencja liczona tu, w logice czystej; magazyn tylko liczy i usuwa.
 * Bez ponawiania zapisu i bez usuwania pojedynczego wpisu.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val storage: HistoryStorage,
) : CommandHistory {

    override val entries: Flow<HistorySnapshot> =
        flow { emitAll(storage.observeNewestFirst()) } // błąd przy tworzeniu strumienia też trafia do catch
            .map<List<HistoryRow>, HistorySnapshot> { rows ->
                HistorySnapshot.Entries(rows.map(HistoryCodec::toEntry))
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(HistorySnapshot.Unavailable)
            }

    override suspend fun record(result: SessionResult): RecordResult = try {
        val row = HistoryCodec.toRow(result)
        storage.inTransaction {
            storage.insert(row)
            val excess = storage.count() - HistoryLimits.MAX_ENTRIES
            if (excess > 0) storage.deleteOldest(excess)
        }
        RecordResult.Recorded
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RecordResult.Failed
    }

    override suspend fun clearAll(): ClearHistoryResult = try {
        storage.deleteAll()
        ClearHistoryResult.Cleared
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ClearHistoryResult.Failed
    }
}
