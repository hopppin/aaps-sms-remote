package pl.twojanazwa.aapsremote.ui.settings.limits

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitField
import pl.twojanazwa.aapsremote.commands.LimitValueError
import pl.twojanazwa.aapsremote.commands.LimitsValidation
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.data.SaveLimitsResult
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles

class CommandLimitsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private class Store(initial: CommandLimitsState) : CommandLimitsStore {
        private val mutable = MutableStateFlow(initial)
        override val state: StateFlow<CommandLimitsState> = mutable
        val saved = mutableListOf<CommandLimits>()
        var gate: CompletableDeferred<Unit>? = null
        var result = SaveLimitsResult.Saved

        fun set(value: CommandLimitsState) {
            mutable.value = value
        }

        override suspend fun save(limits: CommandLimits): SaveLimitsResult {
            saved += limits
            gate?.await()
            return result
        }
    }

    private fun limits(carbs: Int, bolus: Int, interval: Int) =
        (CommandLimits.of(carbs, bolus, interval) as LimitsValidation.Valid).limits

    private val session = SessionDoubles()

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    private fun viewModel(store: Store) = CommandLimitsViewModel(store, session.coordinator)

    private fun CommandLimitsViewModel.type(carbs: String, bolus: String, interval: String) {
        onCarbsChanged(carbs)
        onBolusChanged(bolus)
        onIntervalChanged(interval)
    }

    @Test
    fun `LIM-10 stan Loaded wypelnia pola tekstami`() = runTest(main.dispatcher) {
        val vm = viewModel(Store(CommandLimitsState.Loaded(limits(30, 250, 20))))
        settle()
        val s = vm.uiState.value
        assertEquals(Triple("30", "2,50", "20"), Triple(s.carbsMax, s.bolusMax, s.interval))
        assertTrue(s.canSave)
    }

    @Test
    fun `LIM-10 bledne teksty daja bledy pol i brak zapisu`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loaded(CommandLimits.DEFAULT))
        val vm = viewModel(store)
        settle()
        vm.type("5", "2,52", "15")
        vm.onSaveClicked()
        settle()
        assertEquals(
            mapOf(LimitField.CARBS_MAX to LimitValueError.BELOW_MIN, LimitField.BOLUS_MAX to LimitValueError.NOT_STEP),
            vm.uiState.value.errors,
        )
        assertTrue(store.saved.isEmpty())
        vm.onCarbsChanged("10")
        settle()
        assertEquals(setOf(LimitField.BOLUS_MAX), vm.uiState.value.errors.keys)
    }

    @Test
    fun `LIM-10 poprawne teksty daja jeden zapis i komunikat`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loaded(CommandLimits.DEFAULT))
        val vm = viewModel(store)
        settle()
        vm.type("60", "3", "0")
        vm.onSaveClicked()
        settle()
        assertEquals(listOf(limits(60, 300, 0)), store.saved)
        val s = vm.uiState.value
        assertEquals(LimitsMessage.SAVED, s.message)
        assertEquals(Triple("60", "3,00", "0"), Triple(s.carbsMax, s.bolusMax, s.interval))
        vm.onMessageShown()
        settle()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `LIM-10 niepowodzenie zapisu daje komunikat i zostawia pola`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loaded(CommandLimits.DEFAULT)).also { it.result = SaveLimitsResult.Failed }
        val vm = viewModel(store)
        settle()
        vm.type("60", "3", "0")
        vm.onSaveClicked()
        settle()
        assertEquals(LimitsMessage.SAVE_FAILED, vm.uiState.value.message)
        assertEquals("60", vm.uiState.value.carbsMax)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `LIM-10 Loading blokuje zapis a LoadFailed daje wartosci domyslne`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loading)
        val vm = viewModel(store)
        settle()
        assertFalse(vm.uiState.value.canSave)
        vm.onSaveClicked()
        assertTrue(store.saved.isEmpty())
        store.set(CommandLimitsState.LoadFailed)
        settle()
        val s = vm.uiState.value
        assertEquals(LimitsLoad.LOAD_FAILED, s.load)
        assertEquals(Triple("50", "5,00", "15"), Triple(s.carbsMax, s.bolusMax, s.interval))
        assertTrue(s.canSave)
    }

    @Test
    fun `LIM-10 kolejne emisje stanu nie nadpisuja edycji`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loaded(CommandLimits.DEFAULT))
        val vm = viewModel(store)
        settle()
        vm.onCarbsChanged("77")
        store.set(CommandLimitsState.Loaded(limits(20, 100, 5)))
        settle()
        assertEquals("77", vm.uiState.value.carbsMax)
    }

    @Test
    fun `LIM-11 polecenie w toku blokuje zapis i daje komunikat`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loaded(CommandLimits.DEFAULT))
        val vm = viewModel(store)
        settle()
        session.startCommand()
        vm.onSaveClicked()
        settle()
        assertTrue(store.saved.isEmpty())
        assertEquals(LimitsMessage.COMMAND_ACTIVE, vm.uiState.value.message)
        assertTrue(vm.uiState.value.commandActive)
        assertFalse(vm.uiState.value.canSave)
        vm.onCarbsChanged("12") // pola edytowalne
        settle()
        assertEquals("12", vm.uiState.value.carbsMax)
        session.finishCommand()
        settle()
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `LIM-11 dwa dotkniecia w trakcie zapisu daja jeden zapis`() = runTest(main.dispatcher) {
        val store = Store(CommandLimitsState.Loaded(CommandLimits.DEFAULT)).also { it.gate = CompletableDeferred() }
        val vm = viewModel(store)
        settle()
        vm.onSaveClicked()
        vm.onSaveClicked()
        settle()
        assertEquals(1, store.saved.size)
        assertFalse(vm.uiState.value.canSave)
        store.gate!!.complete(Unit)
        settle()
        assertEquals(1, store.saved.size)
        assertTrue(vm.uiState.value.canSave)
    }
}
