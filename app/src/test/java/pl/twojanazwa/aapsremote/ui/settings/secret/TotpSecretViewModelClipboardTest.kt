package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelClipboardTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    /** Magazyn ABSENT, otwarte okno wklejania z tekstem [pasted] i schowkiem [clip]. */
    private fun openPaste(pasted: String, clip: String?): SecretFixture {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.camera.granted = true
        f.idle()
        f.clipboard.text = clip
        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(pasted)
        f.idle()
        return f
    }

    @Test
    fun `UI-24 zapisz czysci schowek z wklejonym URI dokladnie raz`() = runTest(main.dispatcher) {
        val f = openPaste(URI_RFC, URI_RFC)
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertEquals(1, f.clipboard.clearCount)
        assertEquals(1, f.store.saveCount)
    }

    @Test
    fun `UI-24 anuluj czysci schowek z wklejonym URI dokladnie raz`() = runTest(main.dispatcher) {
        val f = openPaste(URI_RFC, URI_RFC)
        f.viewModel.onPasteDismissed()
        f.idle()
        assertEquals(1, f.clipboard.clearCount)
        assertEquals(0, f.store.saveCount)
    }

    @Test
    fun `UI-24 onStop z otwartym oknem czysci schowek dokladnie raz`() = runTest(main.dispatcher) {
        val f = openPaste(URI_RFC, URI_RFC)
        f.viewModel.onStop()
        f.idle()
        assertEquals(1, f.clipboard.clearCount)
    }

    @Test
    fun `UI-24 inny tekst w schowku nie jest czyszczony`() = runTest(main.dispatcher) {
        val f = openPaste(URI_RFC, "otpauth://totp/INNE?secret=GEZDGNBV")
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertEquals(0, f.clipboard.clearCount)
    }

    @Test
    fun `UI-24 tekst ze spacja na poczatku nie jest czyszczony przy anuluj`() = runTest(main.dispatcher) {
        val f = openPaste(URI_RFC, " $URI_RFC")
        f.viewModel.onPasteDismissed()
        f.idle()
        assertEquals(0, f.clipboard.clearCount)
    }

    @Test
    fun `UI-24 hotp w schowku jest czyszczony mimo bledu parsera`() = runTest(main.dispatcher) {
        val hotp = "otpauth://hotp/AAPS?secret=GEZDGNBV"
        val f = openPaste(hotp, hotp)
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertEquals(1, f.clipboard.clearCount)
        assertEquals(SecretMessage.NOT_TOTP_QR, f.viewModel.uiState.value.message)
    }

    @Test
    fun `UI-24 schowek null albo wyjatek odczytu nie czyszczy i nie rzuca`() = runTest(main.dispatcher) {
        val empty = openPaste(URI_RFC, null)
        empty.viewModel.onPasteSubmitted()
        empty.idle()
        assertEquals(0, empty.clipboard.clearCount)

        val throwing = openPaste(URI_RFC, URI_RFC)
        throwing.clipboard.throwOnRead = true
        throwing.viewModel.onPasteSubmitted()
        throwing.idle()
        assertEquals(0, throwing.clipboard.clearCount)
        assertEquals(SecretMessage.SAVED, throwing.viewModel.uiState.value.message)
    }

    @Test
    fun `UI-24 onStop bez okna wklejania nie czyta ani nie czysci schowka`() = runTest(main.dispatcher) {
        val closed = SecretFixture(main.dispatcher, SecretState.ABSENT)
        closed.clipboard.text = URI_RFC
        closed.idle()
        closed.viewModel.onStop()
        closed.idle()
        assertEquals(0, closed.clipboard.readCount)
        assertEquals(0, closed.clipboard.clearCount)

        val scanner = SecretFixture(main.dispatcher, SecretState.ABSENT)
        scanner.camera.granted = true
        scanner.clipboard.text = URI_RFC
        scanner.idle()
        scanner.viewModel.onScanClicked()
        scanner.idle()
        assertEquals(SecretInputMode.SCANNER, scanner.viewModel.uiState.value.mode)
        scanner.viewModel.onStop()
        scanner.idle()
        assertEquals(0, scanner.clipboard.readCount)
        assertEquals(0, scanner.clipboard.clearCount)
    }

    @Test
    fun `UI-24 puste pole i anuluj nie czyta schowka`() = runTest(main.dispatcher) {
        val f = openPaste("", URI_RFC)
        f.viewModel.onPasteDismissed()
        f.idle()
        assertEquals(0, f.clipboard.readCount)
    }

    @Test
    fun `UI-24 tresc schowka nie trafia do stanu`() = runTest(main.dispatcher) {
        val f = openPaste(URI_RFC, "TAJNA-TRESC-SCHOWKA")
        f.viewModel.onPasteDismissed()
        f.idle()
        val s = f.viewModel.uiState.value
        assertFalse(s.toString().contains("TAJNA-TRESC-SCHOWKA"))
        assertEquals("", s.pasteText)
    }
}
