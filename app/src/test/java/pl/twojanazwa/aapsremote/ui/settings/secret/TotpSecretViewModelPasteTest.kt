package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SaveResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import java.io.File

class TotpSecretViewModelPasteTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    /** Magazyn ABSENT, otwarte okno wklejania. */
    private fun openPaste(): SecretFixture {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.idle()
        f.viewModel.onPasteClicked()
        f.idle()
        return f
    }

    @Test
    fun `UI-11 zapisz poprawny URI zapisuje raz i czysci pole`() = runTest(main.dispatcher) {
        val f = openPaste()
        f.store.saveResults.add(SaveResult.Saved)
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.idle()
        assertTrue(f.viewModel.uiState.value.canSubmitPaste)

        f.viewModel.onPasteSubmitted()
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals(1, f.store.saveCount)
        assertEquals("", s.pasteText)
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals(SecretMessage.SAVED, s.message)
        assertFalse(s.busy)
        assertNotNull(f.store.savedSecret)
    }

    @Test
    fun `UI-11 pole czyszczone przed zakonczeniem zapisu`() = runTest(main.dispatcher) {
        val f = openPaste()
        f.store.gate = CompletableDeferred()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.viewModel.onPasteSubmitted()
        f.idle()
        val s = f.viewModel.uiState.value
        assertTrue(s.busy)
        assertEquals("", s.pasteText)
        assertFalse(s.toString().contains("GEZDGNBV"))
        f.store.gate!!.complete(Unit)
        f.idle()
        assertFalse(f.viewModel.uiState.value.busy)
    }

    @Test
    fun `UI-16 w trakcie zapisu kolejne wklejanie nie woła save`() = runTest(main.dispatcher) {
        val f = openPaste()
        f.store.gate = CompletableDeferred()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertTrue(f.viewModel.uiState.value.busy)

        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertEquals(1, f.store.saveCount)
        assertEquals(SecretInputMode.NONE, f.viewModel.uiState.value.mode)
        f.store.gate!!.complete(Unit)
        f.idle()
        assertEquals(1, f.store.saveCount)
    }

    @Test
    fun `UI-11 hotp daje NOT_TOTP_QR bez zapisu i czysci pole`() = runTest(main.dispatcher) {
        val f = openPaste()
        f.viewModel.onPasteTextChanged("otpauth://hotp/AAPS?secret=GEZDGNBV")
        f.viewModel.onPasteSubmitted()
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals("", s.pasteText)
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals(0, f.store.saveCount)
        assertEquals(SecretMessage.NOT_TOTP_QR, s.message)
    }

    @Test
    fun `UI-11 anuluj czysci pole zamyka okno i nie zapisuje`() = runTest(main.dispatcher) {
        val f = openPaste()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.viewModel.onPasteDismissed()
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals("", s.pasteText)
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals(0, f.store.saveCount)
    }

    @Test
    fun `UI-11 same spacje nie pozwalaja zapisac`() = runTest(main.dispatcher) {
        val f = openPaste()
        f.viewModel.onPasteTextChanged("   ")
        f.idle()
        assertFalse(f.viewModel.uiState.value.canSubmitPaste)
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertEquals(0, f.store.saveCount)
        assertEquals(SecretInputMode.PASTE, f.viewModel.uiState.value.mode)
    }

    @Test
    fun `UI-11 konstruktor bez SavedStateHandle a zrodla bez rememberSaveable`() {
        val params = TotpSecretViewModel::class.java.constructors.single().parameterTypes.map { it.simpleName }
        assertFalse(params.contains("SavedStateHandle"), "Parametry: $params")

        val dir = File("src/main/java/pl/twojanazwa/aapsremote/ui/settings/secret")
        assertTrue(dir.isDirectory, "Brak katalogu ${dir.path}")
        val text = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(text.contains("rememberSaveable"))
        assertFalse(text.contains("SavedStateHandle"))
    }
}
