package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelReplaceTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

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
    fun `UI-08 UNKNOWN wklej i zastap otwiera pole`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.UNKNOWN)
        f.viewModel.onPasteClicked()
        f.viewModel.onReplaceConfirmed()
        f.idle()
        val s = f.viewModel.uiState.value
        assertNull(s.confirmingReplace)
        assertEquals(SecretInputMode.PASTE, s.mode)
    }

    @Test
    fun `UI-08 UNKNOWN skan pyta o zastapienie a anuluj zamyka okno bez skanera i zapisu`() =
        runTest(main.dispatcher) {
            val f = fixture(SecretState.UNKNOWN)
            f.camera.granted = true
            f.viewModel.onScanClicked()
            f.idle()
            var s = f.viewModel.uiState.value
            assertEquals(PendingSecretInput.SCAN, s.confirmingReplace)
            assertEquals(SecretInputMode.NONE, s.mode)

            f.viewModel.onReplaceDismissed()
            f.idle()
            s = f.viewModel.uiState.value
            assertNull(s.confirmingReplace)
            assertEquals(SecretInputMode.NONE, s.mode)
            assertEquals(0, f.store.saveCount)
        }

    @Test
    fun `UI-08 PRESENT skan i zastap otwiera skaner`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.PRESENT)
        f.camera.granted = true
        f.viewModel.onScanClicked()
        f.viewModel.onReplaceConfirmed()
        f.idle()
        val s = f.viewModel.uiState.value
        assertNull(s.confirmingReplace)
        assertEquals(SecretInputMode.SCANNER, s.mode)
    }

    @Test
    fun `UI-08 ABSENT i INVALIDATED skan otwiera skaner bez pytania`() = runTest(main.dispatcher) {
        listOf(SecretState.ABSENT, SecretState.INVALIDATED).forEach { state ->
            val f = fixture(state)
            f.camera.granted = true
            f.viewModel.onScanClicked()
            f.idle()
            val s = f.viewModel.uiState.value
            assertNull(s.confirmingReplace)
            assertEquals(SecretInputMode.SCANNER, s.mode)
        }
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

    @Test
    fun `UI-16 zastap przy trwajacej operacji zamyka pytanie i nie otwiera pola ani skanera`() =
        runTest(main.dispatcher) {
            for (scan in listOf(false, true)) {
                val f = fixture(SecretState.PRESENT)
                f.camera.granted = true
                if (scan) f.viewModel.onScanClicked() else f.viewModel.onPasteClicked()
                f.idle()
                assertEquals(
                    if (scan) PendingSecretInput.SCAN else PendingSecretInput.PASTE,
                    f.viewModel.uiState.value.confirmingReplace,
                )
                // Operacja w toku (busy) przy otwartym pytaniu; sesja polecenia nie bierze udzialu,
                // wiec okno zamyka wylacznie galaz !canPaste/!canScan w onReplaceConfirmed.
                f.store.gate = CompletableDeferred()
                f.viewModel.onDeleteClicked()
                f.viewModel.onDeleteConfirmed()
                f.idle()
                assertEquals(true, f.viewModel.uiState.value.busy)

                f.viewModel.onReplaceConfirmed()
                f.idle()
                val s = f.viewModel.uiState.value
                assertNull(s.confirmingReplace)
                assertEquals(SecretInputMode.NONE, s.mode)

                f.store.gate!!.complete(Unit)
                f.idle()
            }
        }
}
