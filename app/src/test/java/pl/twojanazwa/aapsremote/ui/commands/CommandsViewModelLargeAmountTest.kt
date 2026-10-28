package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles
import pl.twojanazwa.aapsremote.ui.settings.number.FakeAapsNumberSettingsStore
import pl.twojanazwa.aapsremote.ui.settings.secret.FakeTotpSecretStore

class CommandsViewModelLargeAmountTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val session = SessionDoubles()
    private val numbers = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    private val secrets = FakeTotpSecretStore().also { it.setState(SecretState.PRESENT) }
    private val launcher = mockk<CommandLauncher>(relaxed = true)

    private fun viewModel() = CommandsViewModel(
        launcher, session.coordinator, CurrentRun(), numbers, secrets, FakeSmsPermissions(),
    )

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `CMDUI-09 ilosc 41 wymaga potwierdzenia a tak otwiera podsumowanie`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onCarbsInputChanged("41")
        settle()
        vm.onCarbsNextClicked()
        settle()
        assertEquals(41, vm.uiState.value.confirmingLargeGrams)
        assertNull(vm.uiState.value.summaryGrams)
        vm.onLargeAmountConfirmed()
        settle()
        assertNull(vm.uiState.value.confirmingLargeGrams)
        assertEquals(41, vm.uiState.value.summaryGrams)
    }

    @Test
    fun `CMDUI-09 nie zamyka okno i nie otwiera podsumowania a 40 nie wymaga pytania`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            settle()
            vm.onCarbsInputChanged("50")
            settle()
            vm.onCarbsNextClicked()
            settle()
            vm.onLargeAmountDismissed()
            settle()
            assertNull(vm.uiState.value.confirmingLargeGrams)
            assertNull(vm.uiState.value.summaryGrams)
            assertEquals("50", vm.uiState.value.carbsInput)
            coVerify(exactly = 0) { launcher.launch(any(), any()) }
            vm.onCarbsInputChanged("40")
            settle()
            vm.onCarbsNextClicked()
            settle()
            assertNull(vm.uiState.value.confirmingLargeGrams)
            assertEquals(40, vm.uiState.value.summaryGrams)
        }
}
