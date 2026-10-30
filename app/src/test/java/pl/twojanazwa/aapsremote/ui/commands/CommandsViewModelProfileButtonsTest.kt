package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelProfileButtonsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun TestScope.setup(): Pair<CommandTestEnv, CommandsViewModel> {
        val env = CommandTestEnv(this)
        val vm = CommandsViewModel(
            env.launcher(), env.coordinator, env.currentRun, env.numbers, env.secrets, env.permissions, env.limits,
        )
        main.dispatcher.scheduler.runCurrent()
        return env to vm
    }

    private fun settle() = main.dispatcher.scheduler.runCurrent()

    @Test
    fun `PROF-13 onProfileStatusClicked wysyla PROFILE STATUS bez czytania sekretu`() = runTest(main.dispatcher) {
        val (env, vm) = setup()
        vm.onProfileStatusClicked()
        settle()
        assertEquals(listOf("PROFILE STATUS"), env.sender.sentCommands)
        assertEquals(Command.ProfileStatus, checkNotNull(env.coordinator.activeSession.value).command)
        assertEquals(0, env.secrets.loadCount)
        env.coordinator.activeSession.value?.cancel()
        settle()
    }

    @Test
    fun `PROF-13 zrodla ui commands nie zawieraja przycisku PROFILE LIST`() {
        val dir = java.io.File("src/main/java/pl/twojanazwa/aapsremote/ui/commands")
        val sources = dir.listFiles { f -> f.extension == "kt" }!!.joinToString(" ") { it.readText() }
        assertFalse("onProfileListClicked" in sources)
        assertFalse("startNoCode(Command.ProfileList)" in sources)
        for (removed in listOf("ProfileSelection", "ProfileChoice", "onProfileChosen", "profileItems")) {
            assertFalse(removed in sources, removed)
        }
        assertFalse("commands_profile_list" in java.io.File("src/main/res/values/strings.xml").readText())
    }

    @Test
    fun `PROF-13 sekret ABSENT i limity LoadFailed nie blokuja przyciskow profilu`() = runTest(main.dispatcher) {
        val (env, vm) = setup()
        env.secrets.setState(SecretState.ABSENT)
        env.limits.setState(CommandLimitsState.LoadFailed)
        settle()
        val state = vm.uiState.value
        assertEquals(SecretReadiness.ABSENT, state.secret)
        assertTrue(state.canSendNoCode)
        assertFalse(state.canStartCarbs)
    }

    @Test
    fun `PROF-13 przycisk profilu jest nieaktywny gdy trwa polecenie`() = runTest(main.dispatcher) {
        val (env, vm) = setup()
        vm.onBgClicked()
        settle()
        assertFalse(vm.uiState.value.canSendNoCode)
        vm.onProfileSwitchClicked()
        settle()
        assertEquals(listOf("BG"), env.sender.sentCommands)
        assertNull(vm.uiState.value.profile)
        env.coordinator.activeSession.value?.cancel()
        settle()
    }
}
