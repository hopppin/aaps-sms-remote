package pl.twojanazwa.aapsremote.ui.commands

import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.SessionDoubles
import pl.twojanazwa.aapsremote.ui.settings.number.FakeAapsNumberSettingsStore
import pl.twojanazwa.aapsremote.ui.settings.secret.FakeTotpSecretStore

class CommandsViewModelReadinessTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val numbers = FakeAapsNumberSettingsStore().also { it.setConfigured("+447700900123") }
    private val secrets = FakeTotpSecretStore()
    private val permissions = FakeSmsPermissions()
    private val session = SessionDoubles()

    private fun viewModel() =
        CommandsViewModel(mockk(relaxed = true), session.coordinator, CurrentRun(), numbers, secrets, permissions, FakeCommandLimitsStore())

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `CMDUI-01 stan spoczynku ma zamkniete podsumowanie i okno oraz pusty tekst`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        val state = vm.uiState.value
        assertNull(state.summaryGrams)
        assertNull(state.confirmingLargeGrams)
        assertEquals("", state.carbsInput)
        assertFalse(state.launching)
        assertNull(state.message)
    }

    @Test
    fun `CMDUI-02 wczytywanie numeru blokuje wszystkie przyciski`() = runTest(main.dispatcher) {
        numbers.setState(AapsNumberState.Loading)
        val vm = viewModel()
        settle()
        val state = vm.uiState.value
        assertEquals(NumberReadiness.LOADING, state.number)
        assertFalse(state.canSendNoCode)
        assertFalse(state.canStartCarbs)
    }

    @Test
    fun `CMDUI-02 sekwencja stanow numeru daje brak, blad wczytania i numer gotowy`() = runTest(main.dispatcher) {
        numbers.setState(AapsNumberState.NotConfigured)
        val vm = viewModel()
        settle()
        vm.uiState.test {
            assertEquals(NumberReadiness.NOT_CONFIGURED, awaitItem().number)
            numbers.setState(AapsNumberState.LoadFailed)
            settle()
            val failed = awaitItem()
            assertEquals(NumberReadiness.LOAD_FAILED, failed.number)
            assertFalse(failed.canSendNoCode)
            numbers.setConfigured("+447700900123")
            settle()
            val ready = awaitItem()
            assertEquals(NumberReadiness.CONFIGURED, ready.number)
            assertTrue(ready.canSendNoCode)
            assertTrue(ready.canStartCarbs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CMDUI-02 toString stanu z numerem nie zawiera numeru`() = runTest(main.dispatcher) {
        val vm = viewModel()
        settle()
        val text = vm.uiState.value.toString()
        assertFalse(text.contains("900123"))
        assertFalse(text.contains("7700"))
    }

    @Test
    fun `CMDUI-03 brak uprawnienia odbioru blokuje przyciski a po zmianie i odswiezeniu BG jest dostepne`() =
        runTest(main.dispatcher) {
            permissions.send = false
            permissions.receive = false
            val vm = viewModel()
            settle()
            assertFalse(vm.uiState.value.smsGranted)
            assertFalse(vm.uiState.value.canSendNoCode)
            permissions.send = true
            vm.refreshPermissions()
            settle()
            assertFalse(vm.uiState.value.smsGranted)
            permissions.receive = true
            vm.refreshPermissions()
            settle()
            assertTrue(vm.uiState.value.smsGranted)
            assertTrue(vm.uiState.value.canSendNoCode)
        }

    @Test
    fun `CMDUI-03 odczyt uprawnien po utworzeniu i dwoch odswiezeniach daje po trzy wywolania takze gdy pierwszy odczyt jest false`() =
        runTest(main.dispatcher) {
            val counting = CountingPermissions()
            val vm = CommandsViewModel(mockk(relaxed = true), session.coordinator, CurrentRun(), numbers, secrets, counting, FakeCommandLimitsStore())
            vm.refreshPermissions()
            vm.refreshPermissions()
            settle()
            assertEquals(3, counting.sendCalls)
            assertEquals(3, counting.receiveCalls)
        }

    @Test
    fun `CMDUI-04 utworzenie odswieza stan sekretu dokladnie raz i nie laduje sekretu`() = runTest(main.dispatcher) {
        viewModel()
        settle()
        assertEquals(1, secrets.refreshCount)
        assertEquals(0, secrets.loadCount)
    }

    @Test
    fun `CMDUI-04 stan sekretu steruje tylko CARBS a BG i PUMP sa dostepne zawsze`() = runTest(main.dispatcher) {
        secrets.setState(SecretState.ABSENT)
        val vm = viewModel()
        settle()
        vm.uiState.test {
            val absent = awaitItem()
            assertEquals(SecretReadiness.ABSENT, absent.secret)
            assertFalse(absent.canStartCarbs)
            assertTrue(absent.canSendNoCode)
            secrets.setState(SecretState.INVALIDATED)
            settle()
            val invalidated = awaitItem()
            assertFalse(invalidated.canStartCarbs)
            assertTrue(invalidated.canSendNoCode)
            secrets.setState(SecretState.PRESENT)
            settle()
            val present = awaitItem()
            assertTrue(present.canStartCarbs)
            assertTrue(present.canSendNoCode)
            secrets.setState(SecretState.UNKNOWN)
            settle()
            val unknown = awaitItem()
            assertEquals(SecretReadiness.UNKNOWN, unknown.secret)
            assertTrue(unknown.canStartCarbs)
            assertTrue(unknown.canSendNoCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CMDUI-05 polecenie w toku blokuje przyciski a po zakonczeniu wracaja`() = runTest(main.dispatcher) {
        session.startCommand()
        val vm = viewModel()
        settle()
        val busy = vm.uiState.value
        assertTrue(busy.commandActive)
        assertFalse(busy.canSendNoCode)
        assertFalse(busy.canStartCarbs)
        session.finishCommand()
        settle()
        val idle = vm.uiState.value
        assertFalse(idle.commandActive)
        assertTrue(idle.canSendNoCode)
        assertTrue(idle.canStartCarbs)
    }

    @Test
    fun `CMDUI-05 uruchamianie blokuje przyciski`() {
        val state = CommandsUiState(
            number = NumberReadiness.CONFIGURED,
            smsGranted = true,
            launching = true,
        )
        assertFalse(state.canSendNoCode)
        assertFalse(state.canStartCarbs)
    }

    private class CountingPermissions : pl.twojanazwa.aapsremote.sms.SmsPermissions {
        var sendCalls = 0
        var receiveCalls = 0

        override fun canSend(): Boolean {
            sendCalls++
            return false
        }

        override fun canReceive(): Boolean {
            receiveCalls++
            return true
        }
    }
}
