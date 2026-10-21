package pl.twojanazwa.aapsremote.data

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HistoryFailureTest {

    private val storage = InMemoryHistoryStorage()
    private val repository = HistoryRepository(storage)

    @Test
    fun `DATA-15 wyjatki magazynu historii daja Failed Failed i Unavailable bez rzucania`() = runTest {
        val boom = RuntimeException("x")
        storage.insertFailure = boom
        storage.countFailure = boom
        storage.deleteOldestFailure = boom
        storage.deleteAllFailure = boom
        storage.observeCreationFailure = boom

        val recorded = repository.record(HistoryFixtures.bgCompleted())
        val cleared = repository.clearAll()
        assertEquals(RecordResult.Failed, recorded)
        assertEquals(ClearHistoryResult.Failed, cleared)
        repository.entries.test {
            assertEquals(HistorySnapshot.Unavailable, awaitItem())
            awaitComplete()
        }
        // wyniki nie przenosza wyjatku ani komunikatu
        assertEquals("Failed", recorded.toString())
        assertEquals("Failed", cleared.toString())
    }

    @Test
    fun `DATA-15 wyjatek przy liczeniu i usuwaniu najstarszych tez daje Failed`() = runTest {
        storage.countFailure = IllegalStateException("x")
        assertEquals(RecordResult.Failed, repository.record(HistoryFixtures.bgCompleted()))
        assertEquals(0, storage.current.size)
    }

    @Test
    fun `DATA-15 CancellationException przy wstawianiu jest przepuszczany przez zapis`() = runTest {
        storage.insertFailure = CancellationException("x")
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.record(HistoryFixtures.bgCompleted()) }
        }
        assertEquals(0, storage.current.size)
    }

    @Test
    fun `DATA-15 CancellationException przy czyszczeniu jest przepuszczany`() = runTest {
        storage.deleteAllFailure = CancellationException("x")
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.clearAll() }
        }
    }
}
