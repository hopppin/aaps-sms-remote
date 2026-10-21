package pl.twojanazwa.aapsremote.ui.permissions

import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.sms.FakeSmsPermissions
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class SmsPermissionsViewModelTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private fun vm(fake: FakeSmsPermissions) = SmsPermissionsViewModel(fake)

    @Test
    fun `UI-17 brak odbioru SMS przy utworzeniu pozwala prosic o zgode`() {
        val state = vm(FakeSmsPermissions(send = true, receive = false)).uiState.value
        assertTrue(state.canSend)
        assertFalse(state.canReceive)
        assertTrue(state.canRequest)
    }

    @Test
    fun `UI-17 oba przyznane to allGranted bez mozliwosci prosby`() {
        val state = vm(FakeSmsPermissions(true, true)).uiState.value
        assertTrue(state.allGranted)
        assertFalse(state.canRequest)
    }

    @Test
    fun `UI-17 onResume po przyznaniu obu odswieza stan`() {
        val fake = FakeSmsPermissions(false, false)
        val vm = vm(fake)
        assertFalse(vm.uiState.value.allGranted)
        fake.send = true
        fake.receive = true
        vm.onResume()
        assertTrue(vm.uiState.value.allGranted)
    }

    @Test
    fun `UI-17 trzy onResume daja po cztery odczyty kazdego uprawnienia`() {
        val spy = spyk(FakeSmsPermissions(true, true))
        val vm = SmsPermissionsViewModel(spy)
        repeat(3) { vm.onResume() }
        verify(exactly = 4) { spy.canSend() }
        verify(exactly = 4) { spy.canReceive() }
    }

    @Test
    fun `UI-18 lista do prosby zawiera tylko brakujace w kolejnosci`() {
        val fake = FakeSmsPermissions(false, false)
        val vm = vm(fake)
        assertEquals(
            listOf("android.permission.SEND_SMS", "android.permission.RECEIVE_SMS"),
            vm.permissionsToRequest(),
        )
        fake.send = true
        assertEquals(listOf("android.permission.RECEIVE_SMS"), vm.permissionsToRequest())
    }

    @Test
    fun `UI-18 wynik z obydwoma przyznanymi daje denial NONE`() {
        val fake = FakeSmsPermissions(false, false)
        val vm = vm(fake)
        fake.send = true
        fake.receive = true
        vm.onPermissionsResult { false }
        assertEquals(PermissionDenial.NONE, vm.uiState.value.denial)
    }

    @Test
    fun `UI-18 brak RECEIVE_SMS z rationale daje DENIED i Zezwol dostepne`() {
        val vm = vm(FakeSmsPermissions(true, false))
        vm.onPermissionsResult { it == "android.permission.RECEIVE_SMS" }
        assertEquals(PermissionDenial.DENIED, vm.uiState.value.denial)
        assertTrue(vm.uiState.value.canRequest)
    }

    @Test
    fun `UI-18 brak obu bez rationale daje PERMANENTLY_DENIED i Zezwol dostepne`() {
        val vm = vm(FakeSmsPermissions(false, false))
        vm.onPermissionsResult { false }
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, vm.uiState.value.denial)
        assertTrue(vm.uiState.value.canRequest)
    }

    @Test
    fun `UI-18 rationale tylko dla RECEIVE_SMS daje DENIED`() {
        val vm = vm(FakeSmsPermissions(false, false))
        vm.onPermissionsResult { it == "android.permission.RECEIVE_SMS" }
        assertEquals(PermissionDenial.DENIED, vm.uiState.value.denial)
    }

    @Test
    fun `UI-18 trwala odmowa znika po onResume z przyznanymi uprawnieniami`() {
        val fake = FakeSmsPermissions(false, false)
        val vm = vm(fake)
        vm.onPermissionsResult { false }
        vm.onResume()
        assertEquals(PermissionDenial.PERMANENTLY_DENIED, vm.uiState.value.denial)
        fake.send = true
        fake.receive = true
        vm.onResume()
        assertEquals(PermissionDenial.NONE, vm.uiState.value.denial)
    }

    @Test
    fun `UI-15 konstruktor ViewModelu uprawnien nie ma zaleznosci od SessionCoordinator`() {
        val params = SmsPermissionsViewModel::class.java.constructors.single().parameterTypes
        assertEquals(listOf("pl.twojanazwa.aapsremote.sms.SmsPermissions"), params.map { it.name })
    }
}
