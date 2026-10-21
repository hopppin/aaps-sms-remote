package pl.twojanazwa.aapsremote.ui.history

import app.cash.turbine.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import kotlin.time.Duration.Companion.minutes

class HistoryViewModelStateTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private val history = ControllableCommandHistory()

    private fun entry(id: Long, startedAt: Long? = T0) =
        historyEntry(HistoryOutcome.Cancelled, HistoryCommand.BG, id = id, startedAt = startedAt)

    private fun snapshot(vararg ids: Long) = HistorySnapshot.Entries(ids.map { entry(it) })

    private fun viewModel() = HistoryViewModel(history)

    private fun HistoryViewModel.rows(): List<Long> =
        (uiState.value.content as HistoryContent.Entries).items.map { it.id }

    @Test
    fun `HISTUI-01 strumien bez emisji daje stan wczytywania`() = runTest(main.dispatcher) {
        history.enqueueStream()
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HistoryContent.Loading, vm.uiState.value.content)
    }

    @Test
    fun `HISTUI-01 kolejne emisje daja stany wczytywanie pusto lista lista`() = runTest(main.dispatcher) {
        val stream = history.enqueueStream()
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(HistoryContent.Loading, awaitItem().content)
            stream.emit(snapshot())
            main.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(HistoryContent.Empty, awaitItem().content)
            stream.emit(snapshot(1))
            main.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, (awaitItem().content as HistoryContent.Entries).items.size)
            stream.emit(snapshot(2, 1))
            main.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(2, (awaitItem().content as HistoryContent.Entries).items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `HISTUI-02 kolejnosc wierszy bez ponownego sortowania`() = runTest(main.dispatcher) {
        history.enqueue(
            HistorySnapshot.Entries(
                listOf(entry(3, T0), entry(2, T0 + 60_000), entry(1, T0 + 120_000)),
            ),
        )
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), vm.rows())
    }

    @Test
    fun `HISTUI-02 dwiescie wpisow daje dwiescie wierszy`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(*LongArray(200) { 200L - it }))
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(200, vm.rows().size)
    }

    @Test
    fun `HISTUI-02 nowy wpis pojawia sie jako pierwszy wiersz`() = runTest(main.dispatcher) {
        val stream = history.enqueue(snapshot(1))
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1L), vm.rows())
        stream.emit(snapshot(2, 1))
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(2L, 1L), vm.rows())
    }

    @Test
    fun `HISTUI-08 pusta historia wylacza czyszczenie`() = runTest(main.dispatcher) {
        history.enqueue(snapshot())
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HistoryContent.Empty, vm.uiState.value.content)
        assertFalse(vm.uiState.value.canClear)
    }

    @Test
    fun `HISTUI-09 blad odczytu zbierany raz a ponowienie daje wczytywanie i liste`() = runTest(main.dispatcher) {
        history.enqueue(HistorySnapshot.Unavailable)
        history.enqueue(snapshot(1))
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HistoryContent.Unavailable, vm.uiState.value.content)
        main.dispatcher.scheduler.advanceTimeBy(10.minutes)
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, history.collections)
        vm.uiState.test {
            assertEquals(HistoryContent.Unavailable, awaitItem().content)
            vm.onRetry()
            main.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(HistoryContent.Loading, awaitItem().content)
            assertEquals(1, (awaitItem().content as HistoryContent.Entries).items.size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, history.collections)
    }

    @Test
    fun `HISTUI-09 ponowienie na liscie nic nie zmienia`() = runTest(main.dispatcher) {
        history.enqueue(snapshot(1))
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        val before = vm.uiState.value
        vm.onRetry()
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(before, vm.uiState.value)
        assertEquals(1, history.collections)
    }

    @Test
    fun `HISTUI-09 klikniecie wpisu w bledzie odczytu nic nie zmienia`() = runTest(main.dispatcher) {
        history.enqueue(HistorySnapshot.Unavailable)
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        val before = vm.uiState.value
        vm.onEntryClicked(1)
        main.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(before, vm.uiState.value)
        assertTrue(vm.uiState.value.details == null)
    }
}
