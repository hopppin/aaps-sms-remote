package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.flow.Flow
import pl.twojanazwa.aapsremote.session.SessionResult

/**
 * DATA-10 – DATA-12. Dokładnie te trzy składowe (DATA-12).
 * Operacje nie rzucają poza CancellationException (DATA-15).
 */
interface CommandHistory {
    val entries: Flow<HistorySnapshot>
    suspend fun record(result: SessionResult): RecordResult
    suspend fun clearAll(): ClearHistoryResult
}
