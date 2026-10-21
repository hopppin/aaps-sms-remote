package pl.twojanazwa.aapsremote.ui.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PermissionDenialClassifierTest {
    private val sendSms = "android.permission.SEND_SMS"
    private val receiveSms = "android.permission.RECEIVE_SMS"

    @Test
    fun `UI-18 pusta lista brakujacych daje NONE`() {
        assertEquals(PermissionDenial.NONE, PermissionDenialClassifier.classify(emptyList()) { true })
    }

    @Test
    fun `UI-18 brak obu uprawnien i rationale tylko dla RECEIVE_SMS daje DENIED`() {
        val result = PermissionDenialClassifier.classify(listOf(sendSms, receiveSms)) { it == receiveSms }
        assertEquals(PermissionDenial.DENIED, result)
    }

    @Test
    fun `UI-18 brak tylko RECEIVE_SMS z rationale daje DENIED`() {
        assertEquals(PermissionDenial.DENIED, PermissionDenialClassifier.classify(listOf(receiveSms)) { true })
    }

    @Test
    fun `UI-18 oba brakujace bez rationale daja PERMANENTLY_DENIED`() {
        val result = PermissionDenialClassifier.classify(listOf(sendSms, receiveSms)) { false }
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, result)
    }

    @Test
    fun `UI-10 CAMERA z rationale daje DENIED`() {
        val result = PermissionDenialClassifier.classify(listOf(CameraPermission.CAMERA)) { true }
        assertEquals(PermissionDenial.DENIED, result)
    }

    @Test
    fun `UI-10 CAMERA bez rationale daje PERMANENTLY_DENIED`() {
        val result = PermissionDenialClassifier.classify(listOf(CameraPermission.CAMERA)) { false }
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, result)
    }
}
