package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelLockTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun fixture(state: SecretState = SecretState.PRESENT): SecretFixture =
        SecretFixture(main.dispatcher, state).also {
            it.camera.granted = true
            it.idle()
        }

    @Test
    fun `UI-15 polecenie w toku blokuje akcje sekretu`() = runTest(main.dispatcher) {
        val f = fixture()
        f.session.startCommand()
        f.idle()
        val s = f.viewModel.uiState.value
        assertTrue(s.commandActive)
        assertFalse(s.canScan || s.canPaste || s.canDelete)
    }

    @Test
    fun `UI-15 start polecenia zamyka okno usuniecia`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onDeleteClicked()
        f.idle()
        f.session.startCommand()
        f.idle()
        assertFalse(f.viewModel.uiState.value.confirmingDelete)
    }

    @Test
    fun `UI-15 start polecenia zamyka okno zastapienia`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onPasteClicked()
        f.idle()
        assertEquals(PendingSecretInput.PASTE, f.viewModel.uiState.value.confirmingReplace)
        f.session.startCommand()
        f.idle()
        assertEquals(null, f.viewModel.uiState.value.confirmingReplace)
    }

    @Test
    fun `UI-15 start polecenia zamyka pole wklejania i czysci tekst`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.ABSENT)
        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.idle()
        f.session.startCommand()
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals("", s.pasteText)
    }

    @Test
    fun `UI-15 start polecenia zamyka skaner i prosbe o kamere`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.ABSENT)
        f.viewModel.onScanClicked()
        f.idle()
        assertEquals(SecretInputMode.SCANNER, f.viewModel.uiState.value.mode)
        f.session.startCommand()
        f.idle()
        assertEquals(SecretInputMode.NONE, f.viewModel.uiState.value.mode)

        f.session.finishCommand()
        f.camera.granted = false
        f.idle()
        f.viewModel.onScanClicked()
        f.idle()
        assertTrue(f.viewModel.uiState.value.cameraPermissionRequest)
        f.session.startCommand()
        f.idle()
        assertFalse(f.viewModel.uiState.value.cameraPermissionRequest)
    }

    @Test
    fun `UI-15 wyscig wejscie ze skanera przy aktywnym poleceniu daje COMMAND_ACTIVE bez save`() =
        runTest(main.dispatcher) {
            val f = fixture(SecretState.ABSENT)
            f.viewModel.onScanClicked()
            f.idle()
            f.session.startCommand() // bez idle: kolektor jeszcze nie zadziałał
            f.viewModel.onQrScanned(URI_RFC)
            f.idle()
            assertEquals(0, f.store.saveCount)
            assertEquals(SecretMessage.COMMAND_ACTIVE, f.viewModel.uiState.value.message)
        }

    @Test
    fun `UI-15 wyscig zapisz w polu przy aktywnym poleceniu daje COMMAND_ACTIVE bez save`() =
        runTest(main.dispatcher) {
            val f = fixture(SecretState.ABSENT)
            f.viewModel.onPasteClicked()
            f.viewModel.onPasteTextChanged(URI_RFC)
            f.idle()
            f.session.startCommand()
            f.viewModel.onPasteSubmitted()
            f.idle()
            assertEquals(0, f.store.saveCount)
            assertEquals(SecretMessage.COMMAND_ACTIVE, f.viewModel.uiState.value.message)
            assertEquals("", f.viewModel.uiState.value.pasteText)
        }

    @Test
    fun `UI-15 wyscig potwierdzenie usuniecia przy aktywnym poleceniu bez delete`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onDeleteClicked()
        f.idle()
        f.session.startCommand()
        f.viewModel.onDeleteConfirmed()
        f.idle()
        assertEquals(0, f.store.deleteCount)
        assertEquals(SecretMessage.COMMAND_ACTIVE, f.viewModel.uiState.value.message)
    }

    @Test
    fun `UI-15 po zakonczeniu polecenia akcje wracaja wedlug UI-07`() = runTest(main.dispatcher) {
        val f = fixture()
        f.session.startCommand()
        f.idle()
        f.session.finishCommand()
        f.idle()
        val s = f.viewModel.uiState.value
        assertFalse(s.commandActive)
        assertTrue(s.canScan && s.canPaste && s.canDelete)
    }

    @Test
    fun `UI-16 zawieszony zapis daje jedno save busy i ignoruje skan oraz usuniecie`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.PRESENT)
        f.viewModel.onDeleteClicked()
        f.idle()
        f.viewModel.onPasteClicked()
        f.viewModel.onReplaceConfirmed()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.store.gate = CompletableDeferred()
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertTrue(f.viewModel.uiState.value.busy)

        f.viewModel.onScanClicked()
        f.viewModel.onQrScanned(URI_RFC)
        f.viewModel.onDeleteConfirmed()
        f.idle()
        assertEquals(1, f.store.saveCount)
        assertEquals(0, f.store.deleteCount)
        assertEquals(SecretInputMode.NONE, f.viewModel.uiState.value.mode)

        f.store.gate!!.complete(Unit)
        f.idle()
        assertEquals(1, f.store.saveCount)
        assertFalse(f.viewModel.uiState.value.busy)
    }
}
