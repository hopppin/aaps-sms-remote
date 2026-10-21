package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.DeleteResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelDeleteTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private fun fixture(): SecretFixture =
        SecretFixture(main.dispatcher, SecretState.PRESENT).also { it.idle() }

    @Test
    fun `UI-13 usun otwiera okno bez delete a potwierdzenie robi jedno delete z DELETED`() =
        runTest(main.dispatcher) {
            val f = fixture()
            f.viewModel.onDeleteClicked()
            f.idle()
            assertTrue(f.viewModel.uiState.value.confirmingDelete)
            assertEquals(0, f.store.deleteCount)

            f.viewModel.onDeleteConfirmed()
            f.idle()
            val s = f.viewModel.uiState.value
            assertEquals(1, f.store.deleteCount)
            assertFalse(s.confirmingDelete)
            assertFalse(s.busy)
            assertEquals(SecretMessage.DELETED, s.message)
        }

    @Test
    fun `UI-13 niepowodzenie usuniecia daje DELETE_FAILED`() = runTest(main.dispatcher) {
        val f = fixture()
        f.store.deleteResults.add(DeleteResult.Failed)
        f.viewModel.onDeleteClicked()
        f.viewModel.onDeleteConfirmed()
        f.idle()
        assertEquals(SecretMessage.DELETE_FAILED, f.viewModel.uiState.value.message)
    }

    @Test
    fun `UI-13 anulowanie zamyka okno bez delete`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onDeleteClicked()
        f.viewModel.onDeleteDismissed()
        f.idle()
        assertFalse(f.viewModel.uiState.value.confirmingDelete)
        assertEquals(0, f.store.deleteCount)
    }
}
