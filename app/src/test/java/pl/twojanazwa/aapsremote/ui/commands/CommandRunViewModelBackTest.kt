package pl.twojanazwa.aapsremote.ui.commands

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunViewModelBackTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun CommandTestEnv.viewModel() = CommandRunViewModel(currentRun, coordinator)

    @Test
    fun `CMDUI-24 wynik na ekranie i Wstecz zamyka wynik`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = env.viewModel()
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()
        assertTrue(viewModel.uiState.value.view is RunView.Finished)

        viewModel.onBack()
        runCurrent()

        assertNull(viewModel.uiState.value.view)
        assertNull(env.currentRun.state.value)
    }

    @Test
    fun `CMDUI-24 przebieg BG Wstecz otwiera okno Zostan zamyka a Wyjdz zada zamkniecia bez cancel`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            val viewModel = env.viewModel()
            env.launcher().launch(Command.Bg, null)
            runCurrent()

            viewModel.onBack()
            assertTrue(viewModel.uiState.value.confirmingExit)
            assertNotNull(env.coordinator.activeSession.value)

            viewModel.onExitDismissed()
            assertFalse(viewModel.uiState.value.confirmingExit)

            viewModel.onBack()
            viewModel.onExitConfirmed()
            runCurrent()
            assertTrue(viewModel.uiState.value.exitRequested)
            assertFalse(viewModel.uiState.value.confirmingExit)
            assertNotNull(env.coordinator.activeSession.value)
            assertNotNull(env.currentRun.state.value)

            viewModel.onExitHandled()
            assertFalse(viewModel.uiState.value.exitRequested)
        }

    @Test
    fun `CMDUI-24 po Wyjdz i zwolnieniu ViewModelu nowy pokazuje wynik BG`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val first = env.viewModel()
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        first.onBack()
        first.onExitConfirmed()
        first.viewModelScope.cancel() // jak ViewModelStore.clear()
        runCurrent()

        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()
        val second = env.viewModel()
        runCurrent()

        val result = (second.uiState.value.view as RunView.Finished).result
        assertEquals(CommandId.BG, result.commandId)
        assertEquals(ResultKind.SUCCESS, result.kind)

        second.onResultDismissed()
        runCurrent()
        assertNull(second.uiState.value.view)
        assertNull(env.currentRun.state.value)
    }

    @Test
    fun `CMDUI-24 bez uruchomienia interceptsBack jest false`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = env.viewModel()
        runCurrent()
        assertFalse(viewModel.uiState.value.interceptsBack)
        viewModel.onBack()
        assertFalse(viewModel.uiState.value.confirmingExit)
    }
}
