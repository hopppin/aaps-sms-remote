package pl.twojanazwa.aapsremote.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.HistoryFixtures.T0
import pl.twojanazwa.aapsremote.session.SessionResult

class HistoryRepositoryEntriesTest {

    private val storage = InMemoryHistoryStorage()
    private val repository = HistoryRepository(storage)

    private fun ids(snapshot: HistorySnapshot): List<Long> =
        (snapshot as HistorySnapshot.Entries).entries.map { it.id }

    private fun started(result: SessionResult, at: Long?) = result.copy(startedAtEpochMillis = at)

    @Test
    fun `DATA-11 kolektor dostaje stan poczatkowy i nowy stan po kazdym zapisie`() = runTest {
        repository.entries.test {
            assertEquals(HistorySnapshot.Entries(emptyList()), awaitItem())
            repository.record(HistoryFixtures.bgCompleted())
            assertEquals(listOf(1L), ids(awaitItem()))
            repository.record(HistoryFixtures.carbsConfirmed())
            assertEquals(listOf(2L, 1L), ids(awaitItem()))
            repository.record(HistoryFixtures.wrongCode())
            val last = awaitItem() as HistorySnapshot.Entries
            assertEquals(listOf(3L, 2L, 1L), last.entries.map { it.id })
            assertEquals(
                listOf(HistoryCommand.BG, HistoryCommand.CARBS, HistoryCommand.BG),
                last.entries.map { it.command }.reversed(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DATA-11 kolejnosc nie zalezy od czasu rozpoczecia`() = runTest {
        repository.record(started(HistoryFixtures.bgCompleted(), T0 + 120000)) // A
        repository.record(started(HistoryFixtures.bgCompleted(), T0)) // B
        repository.entries.test {
            val entries = (awaitItem() as HistorySnapshot.Entries).entries
            assertEquals(listOf(2L, 1L), entries.map { it.id })
            assertEquals(listOf(T0, T0 + 120000), entries.map { it.startedAtEpochMillis })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DATA-11 kolejnosc nie zalezy od czasow null`() = runTest {
        repository.record(HistoryFixtures.bgCompleted()) // A
        repository.record(HistoryFixtures.cancelledNoTimes()) // B, wszystkie czasy null
        repository.entries.test {
            assertEquals(listOf(2L, 1L), ids(awaitItem()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DATA-11 blad strumienia przy emisji daje Unavailable i koniec bez wyjatku`() = runTest {
        storage.seed(1)
        storage.observeStreamFailure = RuntimeException("x")
        repository.entries.test {
            assertEquals(HistorySnapshot.Unavailable, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `DATA-11 blad przy tworzeniu strumienia daje Unavailable i koniec bez wyjatku`() = runTest {
        storage.observeCreationFailure = RuntimeException("x")
        repository.entries.test {
            assertEquals(HistorySnapshot.Unavailable, awaitItem())
            awaitComplete()
        }
    }
}
