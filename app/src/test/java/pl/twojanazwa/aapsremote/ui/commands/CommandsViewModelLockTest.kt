package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles
import pl.twojanazwa.aapsremote.ui.settings.number.FakeAapsNumberSettingsStore
import pl.twojanazwa.aapsremote.ui.settings.secret.FakeTotpSecretStore

class CommandsViewModelLockTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val launcher = mockk<CommandLauncher>(relaxed = true)
    private val session = SessionDoubles()
    private val numbers = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }

    private fun viewModel() =
        CommandsViewModel(launcher, session.coordinator, numbers, FakeTotpSecretStore(), FakeSmsPermissions())

    @Test
    fun `CMDUI-05 polecenie w toku nie woluje launchera i daje komunikat COMMAND_ACTIVE`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            main.dispatcher.scheduler.advanceUntilIdle()
            session.startCommand()
            vm.onBgClicked()
            vm.onPumpClicked()
            main.dispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 0) { launcher.launch(any(), any()) }
            assertEquals(CommandMessage.COMMAND_ACTIVE, vm.uiState.value.message)
        }

    @Test
    fun `CMDUI-05 brak gotowosci bez polecenia w toku nie woluje launchera ani nie zmienia panelu`() =
        runTest(main.dispatcher) {
            numbers.setState(AapsNumberState.NotConfigured)
            val vm = viewModel()
            main.dispatcher.scheduler.advanceUntilIdle()
            vm.onBgClicked()
            main.dispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 0) { launcher.launch(any(), any()) }
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun `CMDUI-05 podwojne dotkniecie BG i PUMP wola launcher dokladnie raz`() = runTest(main.dispatcher) {
        val vm = viewModel()
        main.dispatcher.scheduler.advanceUntilIdle()
        vm.onBgClicked()
        vm.onBgClicked()
        vm.onPumpClicked()
        main.dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { launcher.launch(any(), any()) }
    }
}
