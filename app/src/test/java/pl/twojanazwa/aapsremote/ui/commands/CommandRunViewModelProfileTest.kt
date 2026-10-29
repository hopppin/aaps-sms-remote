package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.SecretState
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
    fun `PROF-14 onProfileChosen zamyka wynik i zapisuje wybor w ProfileSelection`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val selection = ProfileSelection()
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator, selection, env.secrets)
        finishedList(env)
        val view = viewModel.uiState.value.view as RunView.Finished
        assertEquals(
            listOf(ProfileChoice(1, "LocalProfile1"), ProfileChoice(2, "LocalProfile2")),
            view.result.profileItems,
        )

        viewModel.onProfileChosen(2)
        runCurrent()

        assertNull(env.currentRun.state.value)
        assertNull(viewModel.uiState.value.view)
        assertEquals(ProfileChoice(2, "LocalProfile2"), selection.choice.value)
    }

    @Test
    fun `PROF-14 onProfileChosen z nieistniejacym numerem nic nie robi`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val selection = ProfileSelection()
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator, selection, env.secrets)
        finishedList(env)

        viewModel.onProfileChosen(3)
        runCurrent()

        assertNull(selection.choice.value)
        assertNotNull(env.currentRun.state.value)
    }

    @Test
    fun `PROF-14 sekret ABSENT lub INVALIDATED blokuje wybor pozycji`() = runTest(main.dispatcher) {
        for (secret in listOf(SecretState.ABSENT, SecretState.INVALIDATED)) {
            val env = CommandTestEnv(this)
            env.secrets.setState(secret)
            val selection = ProfileSelection()
            val viewModel = CommandRunViewModel(env.currentRun, env.coordinator, selection, env.secrets)
            finishedList(env)
            assertEquals(secret.toReadiness(), viewModel.uiState.value.secret)

            viewModel.onProfileChosen(1)
            runCurrent()

            assertNull(selection.choice.value, "sekret $secret")
            assertNotNull(env.currentRun.state.value)
        }
    }

    @Test
    fun `PROF-14 RunUiState z wynikiem listy nie ujawnia nazw w toString`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator, ProfileSelection(), env.secrets)
        finishedList(env)
        assertFalse(viewModel.uiState.value.toString().contains("LocalProfile"))
        assertTrue(viewModel.uiState.value.view is RunView.Finished)
    }
}
