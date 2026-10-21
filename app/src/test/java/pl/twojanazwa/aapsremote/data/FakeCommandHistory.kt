package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.twojanazwa.aapsremote.session.SessionResult

/** Atrapa CommandHistory: zapamietuje wyniki przekazane do record. */
class FakeCommandHistory : CommandHistory {
    val recorded = mutableListOf<SessionResult>()

    /** Kolejne wywolania record: gdy true, zwraca Failed. */
    var returnFailed = false

    /** Gdy ustawione, record rzuca ten wyjatek (po zapamietaniu wyniku). */
    var throwOnRecord: Throwable? = null

    override val entries: Flow<HistorySnapshot> = flowOf(HistorySnapshot.Entries(emptyList()))

    override suspend fun record(result: SessionResult): RecordResult {
        recorded.add(result)
        throwOnRecord?.let { throw it }
        return if (returnFailed) RecordResult.Failed else RecordResult.Recorded
    }

    override suspend fun clearAll(): ClearHistoryResult = ClearHistoryResult.Cleared
}
