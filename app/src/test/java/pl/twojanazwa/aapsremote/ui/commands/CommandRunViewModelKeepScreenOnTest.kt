package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunViewModelKeepScreenOnTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    @Test
    fun `CMDUI-22 znacznik ekran wlaczony false true false a stan koncowy to wynik`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { seen += it.keepScreenOn }
        }
        runCurrent()
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()
        job.cancel()

        assertEquals(listOf(false, true, false), seen.fold(listOf<Boolean>()) { acc, v ->
            if (acc.lastOrNull() == v) acc else acc + v
        })
        assertTrue(viewModel.uiState.value.view is RunView.Finished)
    }

    @Test
    fun `CMDUI-23 znacznik ekran chroniony false true true false i zgodny z widokiem`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
        val seen = mutableListOf<Boolean>()
        fun record() {
            val s = viewModel.uiState.value
            assertEquals(s.view != null, s.secureWindow)
            seen += s.secureWindow
        }
        runCurrent()
        record()
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        record()
        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()
        record()
        viewModel.onResultDismissed()
        runCurrent()
        record()

        assertEquals(listOf(false, true, true, false), seen)
        assertNull(viewModel.uiState.value.view)
        assertFalse(viewModel.uiState.value.interceptsBack)
    }
}
