package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.BolusAmountError
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitsValidation
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.commands.CommandTestFixtures.T0

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelBolusTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    private fun TestScope.env() = CommandTestEnv(this).also { it.secrets.setState(SecretState.PRESENT) }

    private fun CommandTestEnv.viewModel(launcher: CommandLauncher = launcher()) =
        CommandsViewModel(launcher, coordinator, currentRun, numbers, secrets, permissions, limits, ProfileSelection()).also { settle() }

    private fun CommandsViewModel.state(): CommandsUiState {
        settle()
        return uiState.value
    }

    private fun CommandsViewModel.next(text: String) {
        onBolusInputChanged(text)
        onBolusNextClicked()
        settle()
    }

    private fun summaryCentiUnits(state: CommandsUiState) = (state.summary?.command as? Command.Bolus)?.centiUnits

    @Test
    fun `BOL-13 bledne teksty daja bledy z tabeli, bez podsumowania i bez SMS-a`() = runTest(main.dispatcher) {
        val env = env()
        val vm = env.viewModel()
        val expected = listOf(
            "" to BolusAmountError.EMPTY,
            "1,5j" to BolusAmountError.INVALID_CHARACTERS,
            "1,234" to BolusAmountError.TOO_MANY_DECIMALS,
            "0" to BolusAmountError.BELOW_MIN,
            "6" to BolusAmountError.ABOVE_MAX,
            "1,23" to BolusAmountError.NOT_STEP,
        )
        for ((text, error) in expected) {
            vm.next(text)
            assertEquals(error, vm.state().bolusError, text)
            assertNull(vm.state().summary)
            assertNull(vm.state().confirming)
            assertEquals(text, vm.state().bolusInput)
        }
        assertEquals(0, env.sender.sendCount)
        assertEquals(listOf("5,00"), BolusAmountError.ABOVE_MAX.textArgs(vm.state().loadedLimits!!))
        vm.onBolusInputChanged("1")
        assertNull(vm.state().bolusError)
    }

    @Test
    fun `BOL-13 tekst 0,1 otwiera podsumowanie dla 10 bez okna Na pewno`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("0,1")
        assertEquals(10, summaryCentiUnits(vm.state()))
        assertNull(vm.state().confirming)
        assertFalse(vm.state().extraConfirmed)
    }

    @Test
    fun `BOL-13 brak sekretu albo limity LoadFailed blokuja Dalej bolusa, BG zostaje dostepny`() =
        runTest(main.dispatcher) {
            val absent = env().also { it.secrets.setState(SecretState.ABSENT) }.viewModel()
            absent.next("1")
            assertFalse(absent.state().canStartBolus)
            assertTrue(absent.state().canSendNoCode)
            assertNull(absent.state().summary)

            val failed = env().also { it.limits.setState(CommandLimitsState.LoadFailed) }.viewModel()
            failed.next("1")
            assertFalse(failed.state().canStartBolus)
            assertTrue(failed.state().canSendNoCode)
            assertNull(failed.state().summary)
        }

    @Test
    fun `BOL-14 3 j przy limicie 500 otwiera okno, Tak otwiera podsumowanie dla 300 z extraConfirmed`() =
        runTest(main.dispatcher) {
            val vm = env().viewModel()
            vm.next("3")
            assertEquals(300, vm.state().confirmingLargeCentiUnits)
            assertNull(vm.state().summary)
            assertTrue(vm.state().secureWindow)
            assertEquals("2,50", formatHalfLimit(vm.state().loadedLimits!!.bolusMaxCentiUnits))
            vm.onBolusLargeConfirmed()
            assertNull(vm.state().confirming)
            assertEquals(300, summaryCentiUnits(vm.state()))
            assertTrue(vm.state().extraConfirmed)
        }

    @Test
    fun `BOL-14 Nie zamyka okno bez podsumowania, tekst zostaje, brak SMS-a`() = runTest(main.dispatcher) {
        val env = env()
        val vm = env.viewModel()
        vm.next("3")
        vm.onBolusLargeDismissed()
        assertNull(vm.state().confirming)
        assertNull(vm.state().summary)
        assertEquals("3", vm.state().bolusInput)
        assertFalse(vm.state().extraConfirmed)
        assertEquals(0, env.sender.sendCount)
    }

    @Test
    fun `BOL-14 2,5 j nie otwiera okna, podsumowanie dla 250`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("2,5")
        assertNull(vm.state().confirming)
        assertEquals(250, summaryCentiUnits(vm.state()))
    }

    @Test
    fun `BOL-14 onStop zamyka okno Na pewno`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("3")
        vm.onStop()
        assertNull(vm.state().confirming)
        assertFalse(vm.state().secureWindow)
        assertEquals("3", vm.state().bolusInput)
    }

    @Test
    fun `BOL-14 okno bolusa nie jest potwierdzane metodami CARBS i odwrotnie`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("3")
        vm.onLargeAmountConfirmed()
        vm.onLargeAmountDismissed()
        assertEquals(300, vm.state().confirmingLargeCentiUnits)
        assertNull(vm.state().summary)
    }

    @Test
    fun `BOL-15 Wyslij wysyla BOLUS 0,1 i kod 0504714321, wynik BolusConfirmed 10`() = runTest(main.dispatcher) {
        val env = env()
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val vm = env.viewModel()
        vm.next("0,1")
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        assertEquals("", vm.state().pinText)
        assertEquals("", vm.state().bolusInput)
        deliverAt(env.incoming, 60_000, AapsSamples.bolusCodeRequest)
        deliverAt(env.incoming, 120_000, "Bolus 0,10 U podany prawidlowo")
        settle()
        assertEquals(listOf("BOLUS 0.1"), env.sender.sentCommands)
        assertEquals(listOf("0504714321"), env.sender.sentCodeCopies)
        val outcome = env.currentRun.state.value!!.state.value.result!!.outcome
        assertTrue(outcome is SessionOutcome.BolusConfirmed && outcome.centiUnits == 10)
    }

    @Test
    fun `BOL-15 onStop czysci PIN i zamyka podsumowanie, tekst ilosci zostaje`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("0,1")
        vm.onPinChanged("4321")
        vm.onStop()
        assertEquals("", vm.state().pinText)
        assertNull(vm.state().summary)
        assertEquals("0,1", vm.state().bolusInput)
    }

    @Test
    fun `BOL-15 tylko Wyslij podsumowania wola launch dla bolusa`() = runTest(main.dispatcher) {
        val launcher = mockk<CommandLauncher>(relaxed = true)
        val vm = env().viewModel(launcher)
        vm.next("3")
        vm.onBolusLargeConfirmed()
        vm.onPinChanged("4321")
        settle()
        coVerify(exactly = 0) { launcher.launch(any(), any(), any()) }
        vm.onSendClicked()
        settle()
        coVerify(exactly = 1) { launcher.launch(match { it is Command.Bolus }, any(), true) }
    }

    @Test
    fun `BOL-15 zrodlo ViewModelu ma launch tylko w startCommand, a startCommand wolane tylko ze startNoCode i Wyslij`() {
        val source = java.io.File("src/main/java/pl/twojanazwa/aapsremote/ui/commands/CommandsViewModel.kt").readText()
        val launches = Regex("""launcher\.launch\(""").findAll(source).toList()
        assertEquals(1, launches.size)
        assertEquals("startCommand", source.substring(0, launches[0].range.first).substringAfterLast("fun ").substringBefore("("))
        val callers = Regex("""(?<!fun )startCommand\(""").findAll(source).map { m ->
            source.substring(0, m.range.first).substringAfterLast("fun ").substringBefore("(")
        }.toList()
        assertEquals(setOf("startNoCode", "onSendClicked"), callers.toSet())
    }

    @Test
    fun `BOL-14 BOL-15 extraConfirmed false bez okna i true po Tak`() = runTest(main.dispatcher) {
        val launcher = mockk<CommandLauncher>(relaxed = true)
        val vm = env().viewModel(launcher)
        vm.next("0,1")
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        coVerify(exactly = 1) { launcher.launch(match { it is Command.Bolus }, any(), false) }
        coVerify(exactly = 0) { launcher.launch(any(), any(), true) }
        vm.next("3")
        vm.onBolusLargeConfirmed()
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        coVerify(exactly = 1) { launcher.launch(match { it is Command.Bolus }, any(), true) }
    }

    @Test
    fun `BOL-13 LIM-09 limit zmniejszony po otwarciu podsumowania daje AMOUNT_ABOVE_LIMIT bez launch`() =
        runTest(main.dispatcher) {
            val launcher = mockk<CommandLauncher>(relaxed = true)
            val env = env()
            val vm = env.viewModel(launcher)
            vm.next("1")
            vm.onPinChanged("4321")
            env.limits.setState(CommandLimitsState.Loaded((CommandLimits.of(30, 50, 15) as LimitsValidation.Valid).limits))
            settle()
            vm.onSendClicked()
            val state = vm.state()
            assertEquals(CommandMessage.AMOUNT_ABOVE_LIMIT, state.message)
            assertEquals("", state.pinText)
            assertNull(state.summary)
            coVerify(exactly = 0) { launcher.launch(any(), any(), any()) }
        }

    @Test
    fun `BOL-13 LIM-09 limity LoadFailed po otwarciu podsumowania daja LIMITS_UNAVAILABLE bez launch`() =
        runTest(main.dispatcher) {
            val launcher = mockk<CommandLauncher>(relaxed = true)
            val env = env()
            val vm = env.viewModel(launcher)
            vm.next("1")
            vm.onPinChanged("4321")
            env.limits.setState(CommandLimitsState.LoadFailed)
            settle()
            vm.onSendClicked()
            val state = vm.state()
            assertEquals(CommandMessage.LIMITS_UNAVAILABLE, state.message)
            assertEquals("", state.pinText)
            assertNull(state.summary)
            coVerify(exactly = 0) { launcher.launch(any(), any(), any()) }
        }

    @Test
    fun `BOL-14 utrata limitow zamyka okno Na pewno razem z ochrona okna i Wstecz`() = runTest(main.dispatcher) {
        val env = env()
        val vm = env.viewModel()
        vm.next("3")
        assertTrue(vm.state().secureWindow)
        env.limits.setState(CommandLimitsState.LoadFailed)
        val state = vm.state()
        assertNull(state.confirming)
        assertFalse(state.secureWindow)
        assertFalse(state.interceptsBack)
    }

    @Test
    fun `BOL-14 onBack przy oknie bolusa zamyka okno bez podsumowania`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("3")
        assertTrue(vm.state().interceptsBack)
        vm.onBack()
        assertNull(vm.state().confirming)
        assertNull(vm.state().summary)
        assertEquals("3", vm.state().bolusInput)
    }

    @Test
    fun `BOL-15 podsumowanie bolusa ma secureWindow i przechwytuje Wstecz`() = runTest(main.dispatcher) {
        val vm = env().viewModel()
        vm.next("0,1")
        assertTrue(vm.state().secureWindow)
        assertTrue(vm.state().interceptsBack)
        vm.onBack()
        assertFalse(vm.state().secureWindow)
    }

    @Test
    fun `BOL-15 odrzucenie przez odstep daje komunikat z N i M`() = runTest(main.dispatcher) {
        val env = env()
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        env.history.entries = flowOf(
            HistorySnapshot.Entries(listOf(bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 600_000))),
        )
        val vm = env.viewModel()
        vm.next("0,1")
        vm.onPinChanged("4321")
        vm.onSendClicked()
        settle()
        val state = vm.state()
        assertEquals(CommandMessage.BOLUS_INTERVAL, state.message)
        assertEquals(listOf(15, 5), state.messageArgs)
        assertNull(state.summary)
        assertEquals(0, env.sender.sendCount)
        vm.onMessageShown()
        assertEquals(emptyList<Int>(), vm.state().messageArgs)
    }

    @Test
    fun `BOL-14 polowa nieparzystego limitu bez zaokraglania`() {
        assertEquals("2,50", formatHalfLimit(500))
        assertEquals("0,025", formatHalfLimit(5))
        assertEquals("5,00", formatHalfLimit(1000))
    }
}
