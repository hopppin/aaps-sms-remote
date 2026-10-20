package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsPermissionsTest {

    @Test
    fun `SMS-14 lista wymaganych uprawnien to SEND_SMS i RECEIVE_SMS w tej kolejnosci`() {
        assertEquals(
            listOf("android.permission.SEND_SMS", "android.permission.RECEIVE_SMS"),
            SmsPermissions.REQUIRED,
        )
    }
}
