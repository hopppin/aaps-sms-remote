package pl.twojanazwa.aapsremote.ui.settings.secret

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelStateTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun fixture(initial: SecretState = SecretState.UNKNOWN) = SecretFixture(main.dispatcher, initial)

    @Test
    fun `UI-07 odswiezenie stanu dokladnie raz bez save load i delete`() = runTest(main.dispatcher) {
        val f = fixture()
        f.idle()
        assertEquals(1, f.store.refreshCount)
        assertEquals(0, f.store.saveCount)
        assertEquals(0, f.store.loadCount)
        assertEquals(0, f.store.deleteCount)
    }

    @Test
    fun `UI-07 stan magazynu odwzorowany na wyswietlanie i akcje`() = runTest(main.dispatcher) {
        val f = fixture()
        f.idle()
        f.viewModel.uiState.test {
            var s = awaitItem()
            assertEquals(SecretDisplay.CHECKING, s.display)
            assertTrue(s.canScan && s.canPaste && s.canDelete)

            f.store.setState(SecretState.ABSENT)
            f.idle()
            s = awaitItem()
            assertEquals(SecretDisplay.ABSENT, s.display)
            assertTrue(s.canScan && s.canPaste)
            assertFalse(s.canDelete)

            f.store.setState(SecretState.PRESENT)
            f.idle()
            s = awaitItem()
            assertEquals(SecretDisplay.PRESENT, s.display)
            assertTrue(s.canScan && s.canPaste && s.canDelete)

            f.store.setState(SecretState.INVALIDATED)
            f.idle()
            s = awaitItem()
            assertEquals(SecretDisplay.INVALIDATED, s.display)
            assertTrue(s.canScan && s.canPaste && s.canDelete)

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, f.store.loadCount)
    }

    @Test
    fun `UI-07 aktywne polecenie blokuje akcje`() = runTest(main.dispatcher) {
        val f = fixture(SecretState.PRESENT)
        f.idle()
        f.session.startCommand()
        f.idle()
        val s = f.viewModel.uiState.value
        assertTrue(s.commandActive)
        assertFalse(s.canScan || s.canPaste || s.canDelete)
    }

    @Test
    fun `UI-21 toString stanu nie zawiera tekstu pola`() {
        val state = TotpSecretUiState(mode = SecretInputMode.PASTE, pasteText = "TAJNE-WEJSCIE")
        assertFalse(state.toString().contains("TAJNE-WEJSCIE"))
        assertFalse(state.toString().contains("pasteText"))
    }
}
