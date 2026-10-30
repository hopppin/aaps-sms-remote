package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import pl.twojanazwa.aapsremote.commands.ProfileSwitchError
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CommandsViewModelProfileTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    private class Setup(val env: CommandTestEnv, val selection: ProfileSelection, val vm: CommandsViewModel)

    private fun TestScope.setup(): Setup {
        val env = CommandTestEnv(this).also { it.secrets.setState(SecretState.PRESENT) }
        val selection = ProfileSelection()
        val vm = CommandsViewModel(
            env.launcher(), env.coordinator, env.currentRun, env.numbers, env.secrets,
            env.permissions, env.limits, selection,
        )
        settle()
        return Setup(env, selection, vm)
    }

    private fun Setup.state(): CommandsUiState {
        settle()
        return vm.uiState.value
    }

    private fun Setup.choose(number: Int = 1, name: String = "LocalProfile1") {
        selection.select(ProfileChoice(number, name))
        settle()
    }

    private fun Setup.next(text: String) {
        vm.onProfilePercentChanged(text)
        vm.onProfileNextClicked()
        settle()
    }

    @Test
    fun `PROF-14 wybor z ProfileSelection otwiera formularz z tekstem 100 i jest czyszczony`() = runTest(main.dispatcher) {
        val s = setup()
        s.choose(2, "LocalProfile2")
        val form = checkNotNull(s.state().profile)
        assertEquals(ProfileChoice(2, "LocalProfile2"), form.choice)
        assertEquals("100", form.percentText)
        assertNull(form.error)
        assertNull(s.selection.choice.value)
    }

    @Test
    fun `PROF-15 Dalej i Wyslij z PIN-em 4321 wysyla zmiane 1 115 i czysci PIN`() = runTest(main.dispatcher) {
        val s = setup()
        s.env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        s.choose(1, "LocalProfile1")
        s.next("115")
        assertNull(s.state().profile)
        val summary = checkNotNull(s.state().summary).command as Command.ProfileSwitch
        assertEquals(Pair(1, 115), Pair(summary.number, summary.percent))
        s.vm.onPinChanged("4321")
        s.vm.onSendClicked()
        settle()
        main.dispatcher.scheduler.advanceTimeBy(1_000)
        main.dispatcher.scheduler.runCurrent()
        assertEquals("", s.vm.uiState.value.pinText)
        assertEquals(listOf("PROFILE 1 115"), s.env.sender.sentCommands)
        val launched = s.env.currentRun.state.value!!.command as Command.ProfileSwitch
        assertEquals(Pair(1, 115), Pair(launched.number, launched.percent))
        s.env.coordinator.activeSession.value?.cancel()
        settle()
    }

    @Test
    fun `PROF-15 wysylka zmiany profilu dziala, gdy limity sa LoadFailed lub niewczytane`() = runTest(main.dispatcher) {
        for (limits in listOf(CommandLimitsState.LoadFailed, CommandLimitsState.Loading)) {
            val s = setup()
            s.env.limits.setState(limits)
            s.env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            s.choose(1, "LocalProfile1")
            s.next("115")
            checkNotNull(s.state().summary)
            s.vm.onPinChanged("4321")
            s.vm.onSendClicked()
            settle()
            main.dispatcher.scheduler.advanceTimeBy(1_000)
            main.dispatcher.scheduler.runCurrent()
            assertEquals(listOf("PROFILE 1 115"), s.env.sender.sentCommands, limits.toString())
            assertNotNull(s.env.currentRun.state.value, limits.toString())
            s.env.coordinator.activeSession.value?.cancel()
            settle()
        }
    }

    @Test
    fun `PROF-15 bledne teksty procentu daja bledy z tabeli, bez podsumowania i bez SMS-a`() = runTest(main.dispatcher) {
        val s = setup()
        s.choose()
        val expected = listOf(
            "" to ProfileSwitchError.EMPTY,
            "11a" to ProfileSwitchError.INVALID_CHARACTERS,
            "49" to ProfileSwitchError.BELOW_MIN,
            "201" to ProfileSwitchError.ABOVE_MAX,
        )
        for ((text, error) in expected) {
            s.next(text)
            assertEquals(error, s.state().profile?.error, text)
            assertNull(s.state().summary)
        }
        assertEquals(0, s.env.sender.sendCount)
        s.vm.onProfilePercentChanged("90")
        assertNull(s.state().profile?.error)
    }

    @Test
    fun `PROF-15 onStop z podsumowaniem i PIN-em czysci PIN, zamyka formularz i podsumowanie, wyboru brak`() =
        runTest(main.dispatcher) {
            val s = setup()
            s.choose()
            s.next("90")
            s.vm.onPinChanged("4321")
            s.vm.onStop()
            val state = s.state()
            assertEquals("", state.pinText)
            assertNull(state.summary)
            assertNull(state.profile)
            assertNull(s.selection.choice.value)
        }

    @Test
    fun `PROF-15 Anuluj Wstecz i onStop zamykaja formularz i podsumowanie`() = runTest(main.dispatcher) {
        val s = setup()
        s.choose()
        assertTrue(s.state().interceptsBack)
        s.vm.onProfileFormCancelled()
        assertNull(s.state().profile)
        s.choose()
        s.vm.onBack()
        assertNull(s.state().profile)
        s.choose()
        s.vm.onStop()
        assertNull(s.state().profile)
        assertFalse(s.state().interceptsBack)
        s.choose()
        s.next("90")
        s.vm.onBack()
        assertNull(s.state().summary)
        assertNull(s.state().profile)
    }

    @Test
    fun `PROF-15 otwarty formularz wlacza znacznik ekran chroniony`() = runTest(main.dispatcher) {
        val s = setup()
        assertFalse(s.state().secureWindow)
        s.choose()
        assertTrue(s.state().secureWindow)
        s.vm.onProfileFormCancelled()
        assertFalse(s.state().secureWindow)
    }

    @Test
    fun `PROF-15 Dalej jest zablokowane przy braku sekretu`() = runTest(main.dispatcher) {
        val s = setup()
        s.choose()
        s.env.secrets.setState(SecretState.ABSENT)
        s.next("90")
        assertNull(s.state().summary)
        assertFalse(s.state().canStartProfile)
    }

    @Test
    fun `PROF-18 toString stanu z formularzem 115 i PIN-em 4321 nie zawiera nazwy, procentu ani PIN-u`() =
        runTest(main.dispatcher) {
            val s = setup()
            s.choose(2, "LocalProfile2")
            s.vm.onProfilePercentChanged("115")
            val formState = s.state()
            for (secret in listOf("LocalProfile", "115", "4321")) {
                assertFalse(secret in formState.toString(), secret)
                assertFalse(secret in checkNotNull(formState.profile).toString(), secret)
            }
            s.next("115")
            s.vm.onPinChanged("4321")
            val summaryText = s.state().toString()
            for (secret in listOf("LocalProfile", "115", "4321")) assertFalse(secret in summaryText, secret)
        }

    @Test
    fun `PROF-15 teksty formularza i podsumowania sa zgodne ze specyfikacja`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        for (
            expected in listOf(
                "Profil: %1\$d. %2\$s", "Procent (%)","Podaj procent (%1\$d–%2\$d).", "Tylko cyfry.",
                "Najmniejszy procent to %1\$d.", "Największy procent to %1\$d.",
                "Zmienić profil na %1\$s (%2\$d%%)?",
                "Aplikacja wyśle %1\$s, a kod z PIN-em odeśle dopiero, gdy AAPS poprosi o kod dla %2\$s %3\$d%%.",
            )
        ) {
            assertTrue(expected in strings, expected)
        }
        val form = File("src/main/java/pl/twojanazwa/aapsremote/ui/commands/ProfileForm.kt").readText()
        assertTrue("KeyboardType.Number" in form)
    }
}
