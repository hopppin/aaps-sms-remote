package pl.twojanazwa.aapsremote.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class HistoryRepositoryClearTest {

    private val storage = InMemoryHistoryStorage()
    private val repository = HistoryRepository(storage)

    @Test
    fun `DATA-12 czyszczenie daje Cleared i kolektor dostaje pusta liste`() = runTest {
        storage.seed(3)
        repository.entries.test {
            val first = awaitItem() as HistorySnapshot.Entries
            assertEquals(3, first.entries.size)
            assertEquals(ClearHistoryResult.Cleared, repository.clearAll())
            assertEquals(HistorySnapshot.Entries(emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DATA-12 zapis po czyszczeniu dostaje identyfikator wiekszy niz 3`() = runTest {
        storage.seed(3)
        repository.clearAll()
        assertEquals(RecordResult.Recorded, repository.record(HistoryFixtures.bgCompleted()))
        assertEquals(1, storage.current.size)
        assertTrue(storage.current.single().id > 3)
    }

    @Test
    fun `DATA-12 blad czyszczenia daje Failed bez wyjatku`() = runTest {
        storage.seed(3)
        storage.deleteAllFailure = RuntimeException("x")
        assertEquals(ClearHistoryResult.Failed, repository.clearAll())
        assertEquals(3, storage.current.size)
    }

    @Test
    fun `DATA-12 CommandHistory ma dokladnie strumien zapis i czyszczenie`() {
        val names = CommandHistory::class.java.methods
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()
        assertEquals(setOf("getEntries", "record", "clearAll"), names)
    }
}
