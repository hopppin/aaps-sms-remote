package pl.twojanazwa.aapsremote.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelNoCodeTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun TestEnvScope.settle() {
        main.dispatcher.scheduler.runCurrent()
    }

    private class TestEnvScope(val env: CommandTestEnv, val vm: CommandsViewModel)

    private fun kotlinx.coroutines.test.TestScope.setup(): TestEnvScope {
        val env = CommandTestEnv(this)
        val vm = CommandsViewModel(env.launcher(), env.coordinator, env.currentRun, env.numbers, env.secrets, env.permissions, env.limits)
        return TestEnvScope(env, vm).also { it.settle() }
    }

    @Test
    fun `CMDUI-06 onBgClicked wysyla jeden SMS BG ustawia sesje Bg i nie czyta sekretu`() =
        runTest(main.dispatcher) {
            val t = setup()
            t.vm.onBgClicked()
            t.settle()
            val session = checkNotNull(t.env.coordinator.activeSession.value)
            assertEquals(listOf("BG"), t.env.sender.sentCommands)
            assertEquals(Command.Bg, session.command)
            assertEquals(0, t.env.secrets.loadCount)
            assertFalse(t.vm.uiState.value.launching)
            session.cancel()
            t.settle()
        }

    @Test
    fun `CMDUI-06 onPumpClicked wysyla jeden SMS PUMP`() = runTest(main.dispatcher) {
        val t = setup()
        t.vm.onPumpClicked()
        t.settle()
        val session = checkNotNull(t.env.coordinator.activeSession.value)
        assertEquals(listOf("PUMP"), t.env.sender.sentCommands)
        assertEquals(Command.Pump, session.command)
        assertEquals(0, t.env.secrets.loadCount)
        session.cancel()
        t.settle()
    }

    @Test
    fun `CMDUI-05 zawieszony SmsSender i podwojne dotkniecie daja dokladnie jeden SMS BG`() =
        runTest(main.dispatcher) {
            val t = setup()
            t.env.sender.suspendForever = true
            t.vm.onBgClicked()
            t.vm.onBgClicked()
            t.vm.onPumpClicked()
            t.settle()
            assertEquals(listOf("BG"), t.env.sender.sentCommands)
            t.env.coordinator.activeSession.value?.cancel()
            t.settle()
        }

    @Test
    fun `CMDUI-12 odrzucenie przez launcher ustawia komunikat powodu i zwalnia launching`() =
        runTest(main.dispatcher) {
            val t = setup()
            // Panel ma jeszcze zbuforowane uprawnienia (bez refreshPermissions), launcher czyta aktualne.
            val readyBefore = t.vm.uiState.value.canSendNoCode
            assertTrue(readyBefore)
            t.env.permissions.send = false
            t.vm.onBgClicked()
            t.settle()
            val state = t.vm.uiState.value
            assertEquals(CommandMessage.SMS_PERMISSIONS_MISSING, state.message)
            assertFalse(state.launching)
            assertEquals(readyBefore, state.canSendNoCode)
            assertEquals(0, t.env.sender.sendCount)
        }

    @Test
    fun `CMDUI-13 sesja BG trwa po wyczyszczeniu ViewModelu i konczy sie Completed`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CommandsViewModel(env.launcher(), env.coordinator, env.currentRun, env.numbers, env.secrets, env.permissions, env.limits) as T
            },
        )[CommandsViewModel::class.java]
        main.dispatcher.scheduler.runCurrent()
        vm.onBgClicked()
        main.dispatcher.scheduler.runCurrent()
        val session = checkNotNull(env.coordinator.activeSession.value)
        store.clear()
        deliverAt(env.incoming, 5_000, AapsSamples.bg)
        main.dispatcher.scheduler.runCurrent()
        val state = session.state.value
        assertEquals(SessionPhase.FINISHED, state.phase)
        assertTrue(state.result?.outcome is SessionOutcome.Completed)
    }
}
