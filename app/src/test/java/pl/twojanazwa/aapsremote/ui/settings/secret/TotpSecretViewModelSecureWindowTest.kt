package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelSecureWindowTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    @Test
    fun `UI-23 secureWindow dla skanera i okna wklejania`() = runTest(main.dispatcher) {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.camera.granted = true
        f.idle()
        val seen = mutableListOf<Boolean>()
        fun record() {
            f.idle()
            seen += f.viewModel.uiState.value.secureWindow
        }

        f.viewModel.onScanClicked()
        record()
        f.viewModel.onScannerClosed()
        record()
        f.viewModel.onPasteClicked()
        record()
        f.viewModel.onPasteDismissed()
        record()
        assertEquals(listOf(true, false, true, false), seen)
    }
}
