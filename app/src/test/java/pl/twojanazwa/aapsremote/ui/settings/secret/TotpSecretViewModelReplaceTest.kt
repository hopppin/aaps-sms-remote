package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelReplaceTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private fun fixture(initial: SecretState): SecretFixture =
        SecretFixture(main.dispatcher, initial).also { it.idle() }

    @Test
    fun `UI-08 PRESENT wklej pyta o zastapienie a zastap otwiera pole`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.PRESENT)
        f.viewModel.onPasteClicked()
        f.idle()
        var s = f.viewModel.uiState.value
        assertEquals(PendingSecretInput.PASTE, s.confirmingReplace)
        assertEquals(SecretInputMode.NONE, s.mode)

        f.viewModel.onReplaceConfirmed()
        f.idle()
        s = f.viewModel.uiState.value
        assertNull(s.confirmingReplace)
        assertEquals(SecretInputMode.PASTE, s.mode)
        assertEquals(0, f.store.saveCount)
    }

    @Test
    fun `UI-08 UNKNOWN wklej i anuluj zamyka okno bez pola i bez zapisu`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.UNKNOWN)
        f.viewModel.onPasteClicked()
        f.idle()
        assertEquals(PendingSecretInput.PASTE, f.viewModel.uiState.value.confirmingReplace)

        f.viewModel.onReplaceDismissed()
        f.idle()
        val s = f.viewModel.uiState.value
        assertNull(s.confirmingReplace)
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals(0, f.store.saveCount)
    }

    @Test
    fun `UI-08 ABSENT wklej otwiera pole bez pytania`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.ABSENT)
        f.viewModel.onPasteClicked()
        f.idle()
        val s = f.viewModel.uiState.value
        assertNull(s.confirmingReplace)
        assertEquals(SecretInputMode.PASTE, s.mode)
    }

    @Test
    fun `UI-08 INVALIDATED wklej otwiera pole bez pytania`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.INVALIDATED)
        f.viewModel.onPasteClicked()
        f.idle()
        val s = f.viewModel.uiState.value
        assertNull(s.confirmingReplace)
        assertEquals(SecretInputMode.PASTE, s.mode)
    }
}
