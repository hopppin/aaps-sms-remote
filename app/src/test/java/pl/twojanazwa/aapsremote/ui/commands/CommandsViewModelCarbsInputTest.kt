package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles
import pl.twojanazwa.aapsremote.ui.settings.number.FakeAapsNumberSettingsStore
import pl.twojanazwa.aapsremote.ui.settings.secret.FakeTotpSecretStore
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions

class CommandsViewModelCarbsInputTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val session = SessionDoubles()
    private val numbers = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    private val secrets = FakeTotpSecretStore().also { it.setState(SecretState.PRESENT) }
    private val launcher = mockk<CommandLauncher>(relaxed = true)

    private fun viewModel() = CommandsViewModel(
        launcher, session.coordinator, numbers, secrets, FakeSmsPermissions(),
    )

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `CMDUI-07 parse waliduje ilosc bez zmiany tekstu`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        assertTrue(vm.uiState.value.canStartCarbs)
        val cases = listOf(
            "" to CarbsAmountError.EMPTY,
            "2.5" to CarbsAmountError.INVALID_CHARACTERS,
            "0" to CarbsAmountError.BELOW_MIN,
            "51" to CarbsAmountError.ABOVE_MAX,
        )
        cases.forEach { (text, expected) ->
            vm.onCarbsInputChanged(text)
            settle()
            vm.onCarbsNextClicked()
            settle()
            assertEquals(expected, vm.uiState.value.carbsError)
            assertEquals(text, vm.uiState.value.carbsInput)
            assertNull(vm.uiState.value.summaryGrams)
        }
    }

    @Test
    fun `CMDUI-07 zmiana usuwa blad a spacja nie jest przycinana`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.onCarbsInputChanged("51")
        settle()
        vm.onCarbsNextClicked()
        settle()
        vm.onCarbsInputChanged("5")
        settle()
        assertNull(vm.uiState.value.carbsError)
        vm.onCarbsInputChanged(" 20")
        settle()
        vm.onCarbsNextClicked()
        settle()
        assertEquals(CarbsAmountError.INVALID_CHARACTERS, vm.uiState.value.carbsError)
        vm.onCarbsInputChanged("020")
        settle()
        vm.onCarbsNextClicked()
        settle()
        assertEquals(20, vm.uiState.value.summaryGrams)
    }

    @Test
    fun `CMDUI-08 szybkie wartosci otwieraja podsumowanie bez wysylania`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        assertEquals(listOf(10, 20, 30), vm.uiState.value.quickValues)
        vm.onCarbsInputChanged("7")
        settle()
        vm.onQuickValueClicked(20)
        settle()
        val state = vm.uiState.value
        assertEquals("20", state.carbsInput)
        assertEquals(20, state.summaryGrams)
        assertNull(state.confirmingLargeGrams)
        coVerify(exactly = 0) { launcher.launch(any(), any()) }
    }
}
