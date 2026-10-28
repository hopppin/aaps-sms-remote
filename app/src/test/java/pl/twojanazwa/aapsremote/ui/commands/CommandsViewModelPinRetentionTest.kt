package pl.twojanazwa.aapsremote.ui.commands

import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.AapsPin
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.session.CodeCredentials
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelPinRetentionTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    private fun CommandsViewModel.state(): CommandsUiState {
        settle()
        return uiState.value
    }

    private fun TestScope.env() = CommandTestEnv(this).also { it.secrets.setState(SecretState.PRESENT) }

    private fun CommandTestEnv.viewModel(launcher: CommandLauncher = launcher()) =
        CommandsViewModel(launcher, coordinator, currentRun, numbers, secrets, permissions).also { settle() }

    private fun CommandsViewModel.openSummary(grams: String = "20") {
        onCarbsInputChanged(grams)
        onCarbsNextClicked()
        settle()
    }

    @Test
    fun `CMDUI-11 Wyslij przy zawieszonym load czysci pole PIN przed zwolnieniem`() = runTest(main.dispatcher) {
        val env = env()
        env.secrets.loadGate = CompletableDeferred()
        val vm = env.viewModel()
        vm.openSummary()
        vm.onPinChanged("4321")
        vm.onSendClicked()
        assertEquals("", vm.state().pinText)
        settle()
        assertEquals(1, env.secrets.loadCount)
        assertEquals("", vm.state().pinText)
        env.secrets.loadGate!!.complete(Unit)
        settle()
    }

    @Test
    fun `CMDUI-11 PIN wpisany w trakcie uruchamiania nie zostaje w stanie`() = runTest(main.dispatcher) {
        val env = env()
        env.secrets.loadGate = CompletableDeferred()
        val vm = env.viewModel()
        vm.openSummary()
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        vm.onPinChanged("9999")
        assertEquals("", vm.state().pinText)
        env.secrets.loadGate!!.complete(Unit)
        settle()
        assertEquals("", vm.state().pinText)
    }

    @Test
    fun `CMDUI-11 anulowanie viewModelScope po Wyslij czysci PIN i nie startuje sesji`() = runTest(main.dispatcher) {
        val env = env()
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val real = env.launcher()
        val pins = mutableListOf<AapsPin?>()
        val launcher = mockk<CommandLauncher>()
        coEvery { launcher.launch(any(), any()) } coAnswers {
            pins.add(secondArg())
            real.launch(firstArg(), secondArg())
        }
        val vm = env.viewModel(launcher)
        vm.openSummary()
        vm.onPinChanged("4321")
        vm.onSendClicked()
        vm.viewModelScope.cancel() // jak ViewModelStore.clear(), zanim ruszy dyspozytor
        settle()
        assertEquals(1, pins.size)
        assertTrue(pins.single()!!.isCleared)
        assertEquals("", vm.state().pinText)
        assertNull(env.coordinator.activeSession.value)
    }

    @Test
    fun `CMDUI-11 onStop z PIN-em zamyka podsumowanie i nie wola launchera`() = runTest(main.dispatcher) {
        val launcher = mockk<CommandLauncher>(relaxed = true)
        val vm = env().viewModel(launcher)
        vm.openSummary()
        vm.onPinChanged("4321")
        vm.onStop()
        val state = vm.state()
        assertEquals("", state.pinText)
        assertNull(state.summaryGrams)
        assertEquals("20", state.carbsInput)
        coVerify(exactly = 0) { launcher.launch(any(), any()) }
    }

    @Test
    fun `CMDUI-11 onStop zamyka okno Na pewno i zostawia tekst ilosci`() = runTest(main.dispatcher) {
        val launcher = mockk<CommandLauncher>(relaxed = true)
        val vm = env().viewModel(launcher)
        vm.openSummary("41")
        assertEquals(41, vm.state().confirmingLargeGrams)
        vm.onStop()
        assertNull(vm.state().confirmingLargeGrams)
        assertEquals("41", vm.state().carbsInput)
        coVerify(exactly = 0) { launcher.launch(any(), any()) }
    }

    @Test
    fun `CMDUI-11 konstruktor bez SavedStateHandle a pola bez typow z sekretami`() {
        val forbidden = setOf(
            AapsPin::class.java, CharArray::class.java, TotpSecret::class.java,
            LoadResult::class.java, CodeCredentials::class.java,
        )
        assertFalse(
            CommandsViewModel::class.java.declaredConstructors.single().parameterTypes
                .any { it.name.endsWith("SavedStateHandle") },
        )
        for (type in listOf(CommandsViewModel::class.java, CommandsUiState::class.java)) {
            val clash = type.declaredFields.filter { f -> forbidden.any { it.isAssignableFrom(f.type) } }
            assertTrue(clash.isEmpty())
        }
    }

    @Test
    fun `CMDUI-15 po udanym starcie CARBS 20 ViewModel i stan nie trzymaja sekretu`() = runTest(main.dispatcher) {
        val env = env()
        val secret = SessionTestFixtures.secret()
        env.secrets.loadResults.add(LoadResult.Loaded(secret))
        val vm = env.viewModel()
        vm.openSummary()
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        assertTrue(vm.state().runHandoff)
        for (holder in listOf<Any>(vm, vm.state())) {
            for (field in holder.javaClass.declaredFields) {
                field.isAccessible = true
                assertNotSame(secret, field.get(holder))
            }
        }
        env.coordinator.activeSession.value?.cancel()
        settle()
    }
}
