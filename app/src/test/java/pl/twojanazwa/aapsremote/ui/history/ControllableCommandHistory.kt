package pl.twojanazwa.aapsremote.ui.history

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import pl.twojanazwa.aapsremote.data.ClearHistoryResult
import pl.twojanazwa.aapsremote.data.CommandHistory
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import pl.twojanazwa.aapsremote.data.RecordResult
import pl.twojanazwa.aapsremote.session.SessionResult

/**
 * Atrapa [CommandHistory]: kolejka strumieni na kolejne zbierania (licznik [collections]),
 * `clearAll()` z kolejką wyników, bramką [clearGate] i licznikiem [clearCount].
 */
class ControllableCommandHistory : CommandHistory {
    private val streams = ArrayDeque<MutableSharedFlow<HistorySnapshot>>()
    private val clearResults = ArrayDeque<ClearHistoryResult>()

    var collections = 0
        private set
    var clearCount = 0
        private set

    /** Jeśli ustawiona, `clearAll()` czeka na jej ukończenie. */
    var clearGate: CompletableDeferred<Unit>? = null

    /** Dodaje strumień dla kolejnego zbierania; replay = 1, więc emisję można wykonać przed zbieraniem. */
    fun enqueueStream(): MutableSharedFlow<HistorySnapshot> =
        MutableSharedFlow<HistorySnapshot>(replay = 1).also { streams.addLast(it) }

    /** Strumień z jedną emisją na kolejne zbieranie. */
    fun enqueue(snapshot: HistorySnapshot): MutableSharedFlow<HistorySnapshot> =
        enqueueStream().also { it.tryEmit(snapshot) }

    fun enqueueClearResult(result: ClearHistoryResult) {
        clearResults.addLast(result)
    }

    override val entries: Flow<HistorySnapshot> = flow {
        collections++
        emitAll(streams.removeFirst())
    }

    override suspend fun record(result: SessionResult): RecordResult =
        throw UnsupportedOperationException("niepotrzebne w testach UI historii")

    override suspend fun clearAll(): ClearHistoryResult {
        clearCount++
        clearGate?.await()
        return clearResults.removeFirstOrNull() ?: ClearHistoryResult.Cleared
    }
}
