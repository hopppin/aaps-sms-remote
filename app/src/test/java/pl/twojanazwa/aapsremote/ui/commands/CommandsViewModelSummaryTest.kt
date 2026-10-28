package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.parser.AapsSamples
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelSummaryTest {
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
    fun `CMDUI-10 podsumowanie dla 20 g ma pusty PIN a Wyslij jest dostepne dopiero po wpisaniu cyfr`() =
        runTest(main.dispatcher) {
            val vm = env().viewModel()
            vm.openSummary()
            assertEquals(20, vm.state().summaryGrams)
            assertEquals("", vm.state().pinText)
            assertFalse(vm.state().canSubmitPin)
            val cases = listOf("43" to "43", "43a" to "43", "4321" to "4321")
            for ((input, expected) in cases) {
                vm.onPinChanged(input)
                assertEquals(expected, vm.state().pinText)
                assertTrue(vm.state().canSubmitPin)
            }
            vm.onPinChanged("123456789012")
            vm.onPinChanged("1234567890123")
            assertEquals("123456789012", vm.state().pinText)
        }

    @Test
    fun `CMDUI-10 Anuluj zamyka podsumowanie czysci PIN i nie wola launchera`() = runTest(main.dispatcher) {
        val launcher = mockk<CommandLauncher>(relaxed = true)
        val vm = env().viewModel(launcher)
        vm.openSummary()
        vm.onPinChanged("4321")
        vm.onSummaryDismissed()
        assertNull(vm.state().summaryGrams)
        assertEquals("", vm.state().pinText)
        assertEquals("20", vm.state().carbsInput)
        coVerify(exactly = 0) { launcher.launch(any(), any()) }
    }

    @Test
    fun `CMDUI-12 brak sekretu daje komunikat z Ustawieniami zamyka podsumowanie i zachowuje ilosc`() =
        runTest(main.dispatcher) {
            val vm = env().viewModel()
            vm.openSummary()
            vm.onPinChanged("4321")
            vm.onSendClicked()
            settle()
            val state = vm.state()
            assertEquals(CommandMessage.SECRET_ABSENT, state.message)
            assertTrue(state.message!!.opensSettings)
            assertNull(state.summaryGrams)
            assertEquals("20", state.carbsInput)
        }

    @Test
    fun `CMDUI-05 Wyslij przy zawieszonym load ustawia launching i blokuje BG PUMP CARBS`() =
        runTest(main.dispatcher) {
            val env = env()
            env.secrets.loadGate = CompletableDeferred()
            val vm = env.viewModel()
            vm.openSummary()
            vm.onPinChanged("4321")
            vm.onSendClicked()
            settle()
            val state = vm.state()
            assertTrue(state.launching)
            assertFalse(state.canSendNoCode)
            assertFalse(state.canStartCarbs)
            env.secrets.loadGate!!.complete(Unit)
            settle()
        }

    @Test
    fun `CMDUI-23 znacznik ochrony ekranu to true false true true false bez falszywej emisji po Wyslij`() =
        runTest(main.dispatcher) {
            val env = env()
            env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            val vm = env.viewModel()
            val flags = mutableListOf<Boolean>()
            val job = launch(UnconfinedTestDispatcher(main.dispatcher.scheduler)) {
                vm.uiState.collect { flags.add(it.secureWindow) }
            }
            vm.openSummary()
            vm.onSummaryDismissed()
            settle()
            vm.openSummary()
            vm.onPinChanged("4321")
            val beforeSend = flags.size
            vm.onSendClicked()
            settle()
            assertTrue(vm.state().runHandoff)
            assertTrue(flags.drop(beforeSend).all { it })
            env.coordinator.activeSession.value?.cancel()
            settle()
            assertTrue(flags.last())
            env.currentRun.clear()
            settle()
            assertFalse(flags.last())
            val compressed = flags.fold(emptyList<Boolean>()) { acc, v -> if (acc.lastOrNull() == v) acc else acc + v }
            assertEquals(listOf(false, true, false, true, false), compressed)
            job.cancel()
        }

    @Test
    fun `CMDUI-23 odrzucenie startu nie wlacza przekazania i znacznik gasnie po zamknieciu podsumowania`() =
        runTest(main.dispatcher) {
            val vm = env().viewModel()
            vm.openSummary()
            vm.onPinChanged("4321")
            vm.onSendClicked()
            settle()
            assertFalse(vm.state().runHandoff)
            assertFalse(vm.state().secureWindow)
        }

    @Test
    fun `CMDUI-23 przekazanie trwa bez CommandRunViewModel dopoki CurrentRun nie jest pusty`() =
        runTest(main.dispatcher) {
            val env = env()
            env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            val vm = env.viewModel()
            vm.openSummary()
            vm.onPinChanged("4321")
            vm.onSendClicked()
            settle()
            assertTrue(vm.state().secureWindow)
            env.coordinator.activeSession.value?.cancel()
            settle()
            assertTrue(vm.state().secureWindow)
            env.currentRun.clear()
            settle()
            assertFalse(vm.state().secureWindow)
        }

    @Test
    fun `CMDUI-23 BG tez wlacza przekazanie ochrony`() = runTest(main.dispatcher) {
        val env = env()
        val vm = env.viewModel()
        vm.onBgClicked()
        settle()
        assertTrue(vm.state().runHandoff)
        env.coordinator.activeSession.value?.cancel()
        settle()
    }

    @Test
    fun `CMDUI-24 Wstecz w podsumowaniu zamyka je i czysci PIN a panel bez okien nie przechwytuje Wstecz`() =
        runTest(main.dispatcher) {
            val vm = env().viewModel()
            assertFalse(vm.state().interceptsBack)
            vm.openSummary()
            vm.onPinChanged("4321")
            assertTrue(vm.state().interceptsBack)
            vm.onBack()
            assertNull(vm.state().summaryGrams)
            assertEquals("", vm.state().pinText)
            assertFalse(vm.state().interceptsBack)
        }

    @Test
    fun `CMDUI-23 onStop w trakcie load Loaded utrzymuje znacznik przez launching i runHandoff`() =
        runTest(main.dispatcher) {
            val env = env()
            env.secrets.loadGate = CompletableDeferred()
            env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            val vm = env.viewModel()
            vm.openSummary("1")
            vm.onPinChanged("4321")
            val flags = mutableListOf<Boolean>()
            val job = launch(UnconfinedTestDispatcher(main.dispatcher.scheduler)) {
                vm.uiState.collect { flags.add(it.secureWindow) }
            }
            vm.onSendClicked()
            settle()
            vm.onStop()
            settle()
            assertNull(vm.state().summaryGrams)
            assertTrue(vm.state().launching)
            assertTrue(vm.state().secureWindow)
            env.secrets.loadGate!!.complete(Unit)
            settle()
            assertTrue(vm.state().runHandoff)
            assertTrue(flags.all { it })
            job.cancel()
            env.coordinator.activeSession.value?.cancel()
            settle()
        }

    @Test
    fun `CMDUI-23 onStop w trakcie load Absent utrzymuje znacznik do zwolnienia i gasi po odmowie`() =
        runTest(main.dispatcher) {
            val env = env()
            env.secrets.loadGate = CompletableDeferred()
            val vm = env.viewModel()
            vm.openSummary("1")
            vm.onPinChanged("4321")
            vm.onSendClicked()
            settle()
            vm.onStop()
            settle()
            assertTrue(vm.state().secureWindow)
            env.secrets.loadGate!!.complete(Unit)
            settle()
            assertFalse(vm.state().secureWindow)
        }

    @Test
    fun `CMDUI-23 onStop w trakcie BG utrzymuje ochrone obu ViewModeli do zamkniecia wyniku`() =
        runTest(main.dispatcher) {
            val env = env()
            val vm = env.viewModel()
            val run = CommandRunViewModel(env.currentRun, env.coordinator)
            fun secure() = vm.uiState.value.secureWindow || run.uiState.value.secureWindow
            vm.onBgClicked()
            settle()
            assertTrue(secure())
            vm.onStop()
            settle()
            assertTrue(secure())
            deliverAt(env.incoming, 1_000, AapsSamples.bg)
            settle()
            assertTrue(run.uiState.value.view is RunView.Finished)
            assertTrue(secure())
            run.onResultDismissed()
            settle()
            assertFalse(secure())
        }

    @Test
    fun `CMDUI-11 onStop w trakcie CARBS 1 nie rusza przebiegu sesji ani ochrony`() = runTest(main.dispatcher) {
        val env = env()
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val vm = env.viewModel()
        val run = CommandRunViewModel(env.currentRun, env.coordinator)
        fun secure() = vm.uiState.value.secureWindow || run.uiState.value.secureWindow
        vm.openSummary("1")
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        val published = env.currentRun.state.value
        val session = env.coordinator.activeSession.value
        val before = run.uiState.value.view as RunView.Active
        assertTrue(published != null && session != null)
        vm.onStop()
        settle()
        assertSame(published, env.currentRun.state.value)
        assertSame(session, env.coordinator.activeSession.value)
        val after = run.uiState.value.view as RunView.Active
        assertEquals(1, after.commandGrams)
        assertEquals(before.commandId, after.commandId)
        assertTrue(secure())
        env.coordinator.activeSession.value?.cancel()
        settle()
    }
}
