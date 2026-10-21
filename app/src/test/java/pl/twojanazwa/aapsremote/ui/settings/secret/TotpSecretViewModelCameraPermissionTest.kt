package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import pl.twojanazwa.aapsremote.ui.permissions.PermissionDenial

class TotpSecretViewModelCameraPermissionTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private fun fixture(): SecretFixture =
        SecretFixture(main.dispatcher, SecretState.ABSENT).also { it.idle() }

    @Test
    fun `UI-10 brak CAMERA daje prosbe o uprawnienie a launched ja zdejmuje`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onScanClicked()
        f.idle()
        var s = f.viewModel.uiState.value
        assertTrue(s.cameraPermissionRequest)
        assertEquals(SecretInputMode.NONE, s.mode)

        f.viewModel.onCameraPermissionRequestLaunched()
        f.idle()
        assertFalse(f.viewModel.uiState.value.cameraPermissionRequest)
    }

    @Test
    fun `UI-10 przyznanie w wyniku prosby otwiera skaner`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onScanClicked()
        f.camera.granted = true
        f.viewModel.onCameraPermissionResult(shouldShowRationale = false)
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals(SecretInputMode.SCANNER, s.mode)
        assertEquals(PermissionDenial.NONE, s.cameraDenial)
    }

    @Test
    fun `UI-10 odmowa z rationale to DENIED a bez rationale PERMANENTLY_DENIED`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onScanClicked()
        f.viewModel.onCameraPermissionResult(shouldShowRationale = true)
        f.idle()
        var s = f.viewModel.uiState.value
        assertEquals(PermissionDenial.DENIED, s.cameraDenial)
        assertEquals(SecretInputMode.NONE, s.mode)

        f.viewModel.onCameraPermissionResult(shouldShowRationale = false)
        f.idle()
        s = f.viewModel.uiState.value
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, s.cameraDenial)
        assertEquals(SecretInputMode.NONE, s.mode)
    }

    @Test
    fun `UI-10 onResume z przyznanym CAMERA zdejmuje odmowe`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onScanClicked()
        f.viewModel.onCameraPermissionResult(shouldShowRationale = false)
        f.idle()
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, f.viewModel.uiState.value.cameraDenial)

        f.viewModel.onResume()
        f.idle()
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, f.viewModel.uiState.value.cameraDenial)

        f.camera.granted = true
        f.viewModel.onResume()
        f.idle()
        assertEquals(PermissionDenial.NONE, f.viewModel.uiState.value.cameraDenial)
    }

    @Test
    fun `UI-10 odmowa trwala nie blokuje wklejania`() = runTest(main.dispatcher) {
        val f = fixture()
        f.viewModel.onScanClicked()
        f.viewModel.onCameraPermissionResult(shouldShowRationale = false)
        f.viewModel.onPasteClicked()
        f.idle()
        assertEquals(SecretInputMode.PASTE, f.viewModel.uiState.value.mode)
    }
}
