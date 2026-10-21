package pl.twojanazwa.aapsremote.ui.settings.number

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.data.SaveNumberResult
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles

class AapsNumberViewModelEditTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val store = FakeAapsNumberSettingsStore(AapsNumberState.NotConfigured)
    private val session = SessionDoubles()

    private fun viewModel() = AapsNumberViewModel(store, session.coordinator)

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `UI-05 zmiana przy Configured otwiera edycje z biezacym numerem`() = runTest(main.dispatcher) {
        store.setConfigured("+447700900999")
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        settle()
        val state = vm.uiState.value
        assertTrue(state.editing)
        assertEquals("+447700900999", state.input)
    }

    @Test
    fun `UI-05 zapis poprawnego numeru zamyka edycje czyści pole i daje SAVED`() = runTest(main.dispatcher) {
        store.saveResults += SaveNumberResult.Saved
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        vm.onInputChanged("+44 7700 900123")
        vm.onSaveClicked()
        settle()
        val state = vm.uiState.value
        assertEquals(listOf("+44 7700 900123"), store.saveCalls)
        assertFalse(state.editing)
        assertEquals("", state.input)
        assertFalse(state.busy)
        assertEquals(NumberMessage.SAVED, state.message)
    }

    @Test
    fun `UI-05 wynik Invalid zostawia edycje i tekst a zmiana tekstu czysci blad`() = runTest(main.dispatcher) {
        store.saveResults += SaveNumberResult.Invalid
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        vm.onInputChanged("70090012")
        vm.onSaveClicked()
        settle()
        var state = vm.uiState.value
        assertTrue(state.editing)
        assertEquals("70090012", state.input)
        assertEquals(NumberInputError.INVALID, state.inputError)
        vm.onInputChanged("700900123")
        settle()
        state = vm.uiState.value
        assertNull(state.inputError)
        assertEquals("700900123", state.input)
    }

    @Test
    fun `UI-05 pusty tekst trafia do magazynu a blad pola to INVALID`() = runTest(main.dispatcher) {
        store.saveResults += SaveNumberResult.Invalid
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        settle()
        assertTrue(vm.uiState.value.canSave)
        vm.onSaveClicked()
        settle()
        assertEquals(listOf(""), store.saveCalls)
        assertEquals(NumberInputError.INVALID, vm.uiState.value.inputError)
    }

    @Test
    fun `UI-05 wynik Failed zostawia edycje i tekst z komunikatem SAVE_FAILED`() = runTest(main.dispatcher) {
        store.saveResults += SaveNumberResult.Failed
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        vm.onInputChanged("+44 7700 900123")
        vm.onSaveClicked()
        settle()
        val state = vm.uiState.value
        assertTrue(state.editing)
        assertEquals("+44 7700 900123", state.input)
        assertEquals(NumberMessage.SAVE_FAILED, state.message)
        assertFalse(state.busy)
    }

    @Test
    fun `UI-05 anulowanie zamyka edycje czysci tekst i nie wola magazynu`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onEditClicked()
        vm.onInputChanged("+44 7700 900123")
        vm.onEditCancelled()
        settle()
        val state = vm.uiState.value
        assertFalse(state.editing)
        assertEquals("", state.input)
        assertTrue(store.saveCalls.isEmpty())
        assertEquals(0, store.clearCalls)
    }
}
