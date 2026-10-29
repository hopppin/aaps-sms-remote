package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.SendResult
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.session.requestCodeAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunViewModelObserveTest {
    /** Instancyjne (nie statyczne): świeży scheduler na każdy test, bo testy przesuwają czas. */
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun CommandTestEnv.viewModel() = CommandRunViewModel(currentRun, coordinator, ProfileSelection(), secrets)

    private fun CommandRunViewModel.active(): RunView.Active = uiState.value.view as RunView.Active

    private fun CommandRunViewModel.finished(): RunView.Finished = uiState.value.view as RunView.Finished

    @Test
    fun `CMDUI-16 BG z launchera daje przebieg a po bg txt wynik`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = env.viewModel()
        runCurrent()
        assertNull(viewModel.uiState.value.view)

        env.launcher().launch(Command.Bg, null)
        runCurrent()

        val active = viewModel.active()
        assertEquals(CommandId.BG, active.commandId)
        assertEquals(
            listOf(StepStatus.DONE, StepStatus.CURRENT),
            active.steps.map { it.status },
        )
        assertTrue(viewModel.uiState.value.commandActive)

        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()

        val result = viewModel.finished().result
        assertEquals(CommandId.BG, result.commandId)
        assertEquals(ResultKind.SUCCESS, result.kind)
        assertTrue(!viewModel.uiState.value.commandActive)
    }

    @Test
    fun `CMDUI-16 CARBS 20 uruchomione przed utworzeniem ViewModelu daje przebieg z krokiem prosby o kod`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            env.launcher().launch(CommandTestFixtures.carbs(20), CommandTestFixtures.pin())
            runCurrent()

            val viewModel = env.viewModel()
            runCurrent()

            val active = viewModel.active()
            assertEquals(CommandId.CARBS, active.commandId)
            assertEquals(20, active.commandGrams)
            val current = active.steps.single { it.status == StepStatus.CURRENT }
            assertEquals(ProgressStepId.AWAIT_CODE_REQUEST, current.id)
            env.coordinator.activeSession.value?.cancel()
            runCurrent()
        }

    @Test
    fun `CMDUI-16 BG zakonczone przed utworzeniem ViewModelu daje wynik Completed`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()

        val viewModel = env.viewModel()
        runCurrent()

        val result = viewModel.finished().result
        assertEquals(CommandId.BG, result.commandId)
        assertEquals(ResultMessage.BG_REPLY, result.message)
    }

    @Test
    fun `CMDUI-16 natychmiastowe SEND_FAILED pokazuje wynik a po zamknieciu CurrentRun jest pusty`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            env.sender.commandResult = SendResult.Failed
            val viewModel = env.viewModel()
            runCurrent()

            env.launcher().launch(Command.Bg, null)
            runCurrent()

            val result = viewModel.finished().result
            assertEquals(ResultKind.FAILURE, result.kind)
            assertEquals(ResultMessage.SEND_FAILED, result.message)

            viewModel.onResultDismissed()
            runCurrent()
            assertNull(env.currentRun.state.value)
            assertNull(viewModel.uiState.value.view)
        }

    @Test
    fun `CMDUI-20 dwa carbs-sukces w oczekiwaniu na prosbe o kod daja liczbe nieoczekiwanych 2`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            val viewModel = env.viewModel()
            env.launcher().launch(CommandTestFixtures.carbs(1), CommandTestFixtures.pin())
            runCurrent()

            deliverAt(env.incoming, 10_000, AapsSamples.carbsDone)
            deliverAt(env.incoming, 20_000, AapsSamples.carbsDone)
            runCurrent()

            assertEquals(2, viewModel.active().unexpectedCount)
            env.coordinator.activeSession.value?.cancel()
            runCurrent()
        }

    @Test
    fun `CMDUI-16 requestCodeAt prowadzi CARBS do kroku wysylania kodu bez utraty przebiegu`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            val viewModel = env.viewModel()
            env.launcher().launch(CommandTestFixtures.carbs(1), CommandTestFixtures.pin())
            runCurrent()

            requestCodeAt(env.incoming)
            runCurrent()

            val current = viewModel.active().steps.single { it.status == StepStatus.CURRENT }
            assertEquals(ProgressStepId.AWAIT_CONFIRMATION, current.id)
            env.coordinator.activeSession.value?.cancel()
            runCurrent()
        }
}
