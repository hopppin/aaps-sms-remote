package pl.twojanazwa.aapsremote.sms

/** SMS-14. Stan w chwili wywołania, bez zapamiętywania. Implementacja: platform.AndroidSmsPermissions. */
interface SmsPermissions {
    fun canSend(): Boolean

    fun canReceive(): Boolean

    companion object {
        const val SEND_SMS: String = "android.permission.SEND_SMS"
        const val RECEIVE_SMS: String = "android.permission.RECEIVE_SMS"

        /** Dla ui (prośba o uprawnienia). Dokładnie te dwa, w tej kolejności. */
        val REQUIRED: List<String> = listOf(SEND_SMS, RECEIVE_SMS)
    }
}
