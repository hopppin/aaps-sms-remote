package pl.twojanazwa.aapsremote.ui.commands

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles
import pl.twojanazwa.aapsremote.ui.settings.number.FakeAapsNumberSettingsStore
import pl.twojanazwa.aapsremote.ui.settings.secret.FakeTotpSecretStore

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelLimitsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val session = SessionDoubles()
    private val numbers = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    private val secrets = FakeTotpSecretStore().also { it.setState(SecretState.PRESENT) }
    private val limits = FakeCommandLimitsStore()
    private val launcher = mockk<CommandLauncher>(relaxed = true)

    private fun viewModel() = CommandsViewModel(
        launcher, session.coordinator, CurrentRun(), numbers, secrets, FakeSmsPermissions(), limits,
    )

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    private fun CommandsViewModel.type(text: String) {
        onCarbsInputChanged(text)
        settle()
        onCarbsNextClicked()
        settle()
    }

    @Test
    fun `LIM-05 stany Loading, LoadFailed i Loaded daja CARBS niedostepne, niedostepne z banerem, dostepne`() =
        runTest(main.dispatcher) {
            limits.setState(CommandLimitsState.Loading)
            val vm = viewModel()
            vm.uiState.test {
                var state = awaitItem()
                assertFalse(state.canStartCarbs)
                assertNull(state.limits.bannerRes())
                assertTrue(state.canSendNoCode)

                limits.setState(CommandLimitsState.LoadFailed)
                state = awaitItem()
                assertFalse(state.canStartCarbs)
                assertEquals(R.string.commands_banner_limits_load_failed, state.limits.bannerRes())
                assertTrue(state.canSendNoCode)

                limits.setCarbsMax(50)
                state = awaitItem()
                assertTrue(state.canStartCarbs)
                assertNull(state.limits.bannerRes())
                assertTrue(state.canSendNoCode)
            }
        }

    @Test
    fun `LIM-06 limit 30 i tekst 35 daje blad powyzej maksimum z argumentem 30 i zamkniete podsumowanie`() =
        runTest(main.dispatcher) {
            limits.setCarbsMax(30)
            val vm = viewModel()
            settle()
            vm.type("35")
            val state = vm.uiState.value
            assertEquals(CarbsAmountError.ABOVE_MAX, state.carbsError)
            assertEquals(listOf(30), state.carbsError?.textArgs(checkNotNull(state.loadedLimits)))
            assertEquals(listOf(1, 30), CarbsAmountError.EMPTY.textArgs(checkNotNull(state.loadedLimits)))
            assertNull(state.summaryGrams)
            assertNull(state.confirmingLargeGrams)
        }

    @Test
    fun `LIM-07 limit 30 ma prog 24, 24 otwiera podsumowanie, 25 okno Na pewno`() = runTest(main.dispatcher) {
        limits.setCarbsMax(30)
        val vm = viewModel()
        settle()
        assertEquals(24, vm.uiState.value.carbsExtraConfirmationAboveGrams)
        vm.type("24")
        assertEquals(24, vm.uiState.value.summaryGrams)
        vm.onSummaryDismissed()
        vm.type("25")
        settle()
        assertEquals(25, vm.uiState.value.confirmingLargeGrams)
        assertNull(vm.uiState.value.summaryGrams)
    }

    @Test
    fun `LIM-08 szybkie wartosci z limitow, przy limicie 10 lista pusta`() = runTest(main.dispatcher) {
        limits.setCarbsMax(30)
        val vm = viewModel()
        settle()
        assertEquals(listOf(10, 20), vm.uiState.value.quickValues)
        limits.setCarbsMax(10)
        settle()
        assertEquals(emptyList<Int>(), vm.uiState.value.quickValues)
        limits.setState(CommandLimitsState.LoadFailed)
        settle()
        assertEquals(emptyList<Int>(), vm.uiState.value.quickValues)
    }

    @Test
    fun `LIM-09 extraConfirmed true tylko po Tak w oknie Na pewno`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        vm.type("41")
        vm.onLargeAmountConfirmed()
        vm.onPinChanged("4321")
        settle()
        vm.onSendClicked()
        settle()
        coVerify { launcher.launch(any<Command>(), any(), true) }

        vm.onQuickValueClicked(20)
        vm.onPinChanged("4321")
        settle()
        vm.onSendClicked()
        settle()
        coVerify { launcher.launch(any<Command>(), any(), false) }
    }

    @Test
    fun `LIM-09 limit zmniejszony po otwarciu podsumowania daje komunikat AMOUNT_ABOVE_LIMIT bez startu`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            settle()
            vm.type("40")
            vm.onPinChanged("4321")
            limits.setCarbsMax(30)
            settle()
            vm.onSendClicked()
            settle()
            assertEquals(CommandMessage.AMOUNT_ABOVE_LIMIT, vm.uiState.value.message)
            assertNull(vm.uiState.value.summaryGrams)
            coVerify(exactly = 0) { launcher.launch(any<Command>(), any(), any()) }
        }

    @Test
    fun `LIM-09 odrzucenie z launchera daje komunikat z tabeli`() = runTest(main.dispatcher) {
        coEvery { launcher.launch(any<Command>(), any(), any()) } returns
            LaunchResult.Rejected(LaunchRejection.EXTRA_CONFIRMATION_REQUIRED)
        val vm = viewModel()
        settle()
        vm.type("20")
        vm.onPinChanged("4321")
        settle()
        vm.onSendClicked()
        settle()
        assertEquals(CommandMessage.EXTRA_CONFIRMATION_REQUIRED, vm.uiState.value.message)
        assertFalse(CommandMessage.EXTRA_CONFIRMATION_REQUIRED.opensSettings)
        assertFalse(CommandMessage.AMOUNT_ABOVE_LIMIT.opensSettings)
        assertTrue(CommandMessage.LIMITS_UNAVAILABLE.opensSettings)
    }
}
