package pl.twojanazwa.aapsremote.ui.commands

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandsSourcesTest {
    private val commandsDirectory = File("src/main/java/pl/twojanazwa/aapsremote/ui/commands")
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `CMDUI-01 zrodla nie zawieraja zaslepki ani jej tekstu`() {
        assertFalse(File(commandsDirectory, "CommandsPlaceholderScreen.kt").exists())
        assertFalse("placeholder_commands_text" in strings)
    }

    @Test
    fun `CMDUI-01 ekran polecen nie zapisuje pola przez stan Compose`() {
        val sources = commandsDirectory.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse("rememberSaveable" in sources)
        assertFalse("SavedStateHandle" in sources)
    }

    @Test
    fun `CMDUI-10 CMDUI-23 podsumowanie maskuje PIN, a ekran chroni okno znacznikiem secureWindow`() {
        val summary = withoutComments(File(commandsDirectory, "CommandSummary.kt").readText())
        assertTrue("PasswordVisualTransformation()" in summary)
        assertTrue("KeyboardType.NumberPassword" in summary)
        assertTrue("autoCorrectEnabled = false" in summary)
    }

    @Test
    fun `CMDUI-23 SecureWindowEffect jest tylko w AppScaffold i obejmuje stan obu ViewModeli`() {
        val scaffold = withoutComments(File("src/main/java/pl/twojanazwa/aapsremote/ui/navigation/AppScaffold.kt").readText())
        assertTrue("SecureWindowEffect(commandsState.secureWindow || runState.secureWindow)" in scaffold)
        for (name in listOf("CommandsScreen.kt", "RunScreen.kt", "ResultCard.kt")) {
            assertFalse("SecureWindowEffect(" in withoutComments(File(commandsDirectory, name).readText()), name)
        }
    }

    @Test
    fun `CMDUI-09 okno Na pewno ma securePolicy SecureOn`() {
        val dialogs = withoutComments(File(commandsDirectory, "CommandDialogs.kt").readText())
        val large = dialogs.substringAfter("fun LargeAmountDialog").substringBefore("@Composable")
        assertTrue("properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)" in large)
    }

    @Test
    fun `BOL-14 okno Na pewno bolusa ma securePolicy SecureOn`() {
        val dialogs = withoutComments(File(commandsDirectory, "CommandDialogs.kt").readText())
        val bolus = dialogs.substringAfter("fun BolusLargeDialog").substringBefore("@Composable")
        assertTrue("properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)" in bolus)
    }

    @Test
    fun `BOL-13 sekcja Bolus nie ma szybkich wartosci i uzywa klawiatury dziesietnej`() {
        val panel = withoutComments(File(commandsDirectory, "CommandsPanel.kt").readText())
        val section = panel.substringAfter("commands_bolus_title").substringBefore("@Composable")
        assertTrue("KeyboardType.Decimal" in section)
        assertFalse("QuickValue" in section)
    }

    @Test
    fun `CMDUI-17 RunScreen ustawia stateDescription z odwzorowania statusu kroku`() {
        val run = withoutComments(File(commandsDirectory, "RunScreen.kt").readText())
        assertTrue("stateDescription = statusDescription" in run)
        assertTrue("stringResource(step.status.descriptionRes())" in run)
    }

    @Test
    fun `CMDUI-11 CommandsScreen woła onStop przy ON_STOP i przy opuszczeniu kompozycji bez runViewModel`() {
        val screen = withoutComments(File(commandsDirectory, "CommandsScreen.kt").readText())
        assertTrue("LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commandsViewModel.onStop() }" in screen)
        assertTrue("DisposableEffect(Unit) { onDispose { commandsViewModel.onStop() } }" in screen)
        val onDispose = screen.substringAfter("onDispose {").substringBefore("}")
        assertFalse("runViewModel" in onDispose)
    }

    @Test
    fun `CMDUI-01 sekcje panelu w kolejnosci Bolus Profil CARBS BG PUMP po banerach`() {
        val panel = withoutComments(File(commandsDirectory, "CommandsPanel.kt").readText())
        val keys = listOf("commands_bolus_title", "commands_profile_title", "commands_carbs_title", "commands_bg", "commands_pump")
            .map { panel.indexOf("R.string.$it") }
        assertTrue(keys.all { it >= 0 })
        assertEquals(keys.sorted(), keys)
        assertTrue(panel.lastIndexOf("bannerRes()") < keys.first())
    }

    @Test
    fun `CMDUI-28 podsumowanie ustawia fokus pola PIN i pokazuje klawiature bez stanu zapisywanego`() {
        val summary = withoutComments(File(commandsDirectory, "CommandSummary.kt").readText())
        assertTrue("remember { FocusRequester() }" in summary)
        assertTrue(".focusRequester(" in summary)
        assertTrue("LaunchedEffect(windowFocused)" in summary)
        assertTrue("LocalWindowInfo.current.isWindowFocused" in summary)
        assertTrue("requestFocus()" in summary)
        assertTrue("LocalSoftwareKeyboardController.current" in summary)
        assertTrue("keyboard?.show()" in summary)
        assertFalse("rememberSaveable" in summary)
        assertFalse("SavedStateHandle" in summary)
    }

    private fun withoutComments(source: String) =
        source.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("//.*"), "")
}
