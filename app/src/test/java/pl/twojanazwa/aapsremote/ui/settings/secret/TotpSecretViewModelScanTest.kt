package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelScanTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private fun openScanner(): SecretFixture {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.camera.granted = true
        f.idle()
        f.viewModel.onScanClicked()
        f.idle()
        assertEquals(SecretInputMode.SCANNER, f.viewModel.uiState.value.mode)
        return f
    }

    @Test
    fun `UI-09 dwa razy to samo wejscie ze skanera daje jedno save i zamyka skaner`() = runTest(main.dispatcher) {
        val f = openScanner()
        f.store.gate = CompletableDeferred()
        f.viewModel.onQrScanned(URI_RFC)
        f.viewModel.onQrScanned(URI_RFC)
        f.idle()
        assertEquals(SecretInputMode.NONE, f.viewModel.uiState.value.mode)
        f.store.gate!!.complete(Unit)
        f.idle()
        assertEquals(1, f.store.saveCount)
        assertEquals(SecretMessage.SAVED, f.viewModel.uiState.value.message)
    }

    @Test
    fun `UI-09 zamkniety skaner ignoruje wejscie`() = runTest(main.dispatcher) {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.idle()
        f.viewModel.onQrScanned(URI_RFC)
        f.idle()
        assertEquals(0, f.store.saveCount)
        assertEquals(null, f.viewModel.uiState.value.message)
    }

    @Test
    fun `UI-09 blad aparatu zamyka skaner z komunikatem CAMERA_UNAVAILABLE`() = runTest(main.dispatcher) {
        val f = openScanner()
        f.viewModel.onCameraFailed()
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals(SecretMessage.CAMERA_UNAVAILABLE, s.message)
    }

    @Test
    fun `UI-09 anuluj zamyka skaner bez zapisu`() = runTest(main.dispatcher) {
        val f = openScanner()
        f.viewModel.onScannerClosed()
        f.idle()
        assertEquals(SecretInputMode.NONE, f.viewModel.uiState.value.mode)
        assertEquals(0, f.store.saveCount)
    }
}
