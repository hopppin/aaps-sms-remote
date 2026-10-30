package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunViewModelProfileTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private suspend fun TestScope.finishedList(env: CommandTestEnv) {
        env.launcher().launch(Command.ProfileList, null)
        runCurrent()
        deliverAt(env.incoming, 1_000, AapsSamples.profileList)
        runCurrent()
    }

    @Test
    fun `PROF-16 RunUiState z wynikiem listy nie ujawnia nazw w toString`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
        finishedList(env)
        assertFalse(viewModel.uiState.value.toString().contains("LocalProfile"))
        assertTrue(viewModel.uiState.value.view is RunView.Finished)
    }
}
