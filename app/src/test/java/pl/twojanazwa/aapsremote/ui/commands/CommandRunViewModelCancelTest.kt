package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.session.requestCodeAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunViewModelCancelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun CommandTestEnv.viewModel() = CommandRunViewModel(currentRun, coordinator)

    private suspend fun CommandTestEnv.startCarbs1(): CommandRunViewModel {
        secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val viewModel = viewModel()
        launcher().launch(CommandTestFixtures.carbs(1), CommandTestFixtures.pin())
        return viewModel
    }

    private fun CommandRunViewModel.finished() = (uiState.value.view as RunView.Finished).result

    @Test
    fun `CMDUI-18 CARBS 1 w oczekiwaniu na prosbe o kod anuluje bez okna i nie wysyla kodu`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            val viewModel = env.startCarbs1()
            runCurrent()
            assertEquals(CancelMode.CANCEL, (viewModel.uiState.value.view as RunView.Active).cancelMode)

            viewModel.onCancelClicked()
            runCurrent()

            assertFalse(viewModel.uiState.value.confirmingStopWaiting)
            assertEquals(ResultKind.CANCELLED, viewModel.finished().kind)
            deliverAt(env.incoming, 60_000, AapsSamples.carbsCodeRequest)
            runCurrent()
            assertTrue(env.sender.sentCodeCopies.isEmpty())
        }

    @Test
    fun `CMDUI-18 po kodzie okno Przestac czekac Czekaj dalej a potwierdzenie daje nieznany`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            val viewModel = env.startCarbs1()
            runCurrent()
            requestCodeAt(env.incoming)
            runCurrent()

            viewModel.onCancelClicked()
            assertTrue(viewModel.uiState.value.confirmingStopWaiting)
            assertTrue(viewModel.uiState.value.view is RunView.Active)

            viewModel.onStopWaitingDismissed()
            assertFalse(viewModel.uiState.value.confirmingStopWaiting)
            assertTrue(viewModel.uiState.value.view is RunView.Active)

            viewModel.onCancelClicked()
            viewModel.onStopWaitingConfirmed()
            runCurrent()

            assertEquals(ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE, viewModel.finished().message)
            assertFalse(viewModel.uiState.value.confirmingStopWaiting)
        }

    @Test
    fun `CMDUI-18 sukces przy otwartym oknie zamyka okno a pozniejsze potwierdzenie nie zmienia wyniku`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            val viewModel = env.startCarbs1()
            runCurrent()
            requestCodeAt(env.incoming)
            runCurrent()
            viewModel.onCancelClicked()
            assertTrue(viewModel.uiState.value.confirmingStopWaiting)

            deliverAt(env.incoming, 120_000, AapsSamples.carbsDone)
            runCurrent()
            assertFalse(viewModel.uiState.value.confirmingStopWaiting)

            viewModel.onStopWaitingConfirmed()
            runCurrent()
            assertEquals(ResultMessage.CARBS_CONFIRMED, viewModel.finished().message)
        }

    @Test
    fun `CMDUI-18 BG w oczekiwaniu na odpowiedz po anulowaniu daje Cancelled`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = env.viewModel()
        env.launcher().launch(Command.Bg, null)
        runCurrent()

        viewModel.onCancelClicked()
        runCurrent()

        assertEquals(CommandId.BG, viewModel.finished().commandId)
        assertEquals(ResultKind.CANCELLED, viewModel.finished().kind)
    }

    @Test
    fun `CMDUI-18 zniknięcie przebiegu zamyka okna`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = env.viewModel()
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        viewModel.onBack()
        assertTrue(viewModel.uiState.value.confirmingExit)

        env.currentRun.clear()
        runCurrent()

        assertFalse(viewModel.uiState.value.confirmingExit)
        assertFalse(viewModel.uiState.value.confirmingStopWaiting)
    }
}
