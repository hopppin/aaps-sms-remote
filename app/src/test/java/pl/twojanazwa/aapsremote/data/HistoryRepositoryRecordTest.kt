package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryRepositoryRecordTest {

    private val storage = InMemoryHistoryStorage()
    private val repository = HistoryRepository(storage)
    private val result = HistoryFixtures.bgCompleted()

    private fun ids() = storage.current.map { it.id }

    @Test
    fun `DATA-10 199 wpisow plus zapis daje 200 bez usuwania`() = runTest {
        storage.seed(199)
        assertEquals(RecordResult.Recorded, repository.record(result))
        assertEquals(200, storage.current.size)
        assertEquals((1L..200L).toList(), ids())
        assertEquals(false, storage.calls.any { it.startsWith("deleteOldest") })
    }

    @Test
    fun `DATA-10 200 wpisow plus zapis usuwa najstarszy`() = runTest {
        storage.seed(200)
        assertEquals(RecordResult.Recorded, repository.record(result))
        assertEquals((2L..201L).toList(), ids())
    }

    @Test
    fun `DATA-10 205 wpisow plus zapis usuwa 6 najstarszych`() = runTest {
        storage.seed(205)
        assertEquals(RecordResult.Recorded, repository.record(result))
        assertEquals(200, storage.current.size)
        assertEquals((7L..206L).toList(), ids())
    }

    @Test
    fun `DATA-10 blad usuwania daje Failed bez wyjatku i wycofuje zapis bez ponawiania`() = runTest {
        storage.seed(200)
        storage.deleteOldestFailure = RuntimeException("x")
        assertEquals(RecordResult.Failed, repository.record(result))
        assertEquals((1L..200L).toList(), ids())
        assertEquals(1, storage.transactionCalls)
    }

    @Test
    fun `DATA-10 wstawienie liczenie i usuwanie sa w jednej transakcji`() = runTest {
        storage.seed(200)
        repository.record(result)
        assertEquals(1, storage.transactionCalls)
        assertEquals(
            listOf("inTransaction:false", "insert:true", "count:true", "deleteOldest:true"),
            storage.calls,
        )
    }

    @Test
    fun `DATA-10 blad wstawienia daje Failed`() = runTest {
        storage.insertFailure = IllegalStateException("x")
        assertEquals(RecordResult.Failed, repository.record(result))
        assertEquals(0, storage.current.size)
        assertEquals(1, storage.transactionCalls)
    }

    @Test
    fun `DATA-10 zapisany wiersz ma kody z kodeka i nadany identyfikator`() = runTest {
        repository.record(result)
        val expected = HistoryCodec.toRow(result).copy(id = 1)
        assertEquals(listOf(expected), storage.current)
    }
}
