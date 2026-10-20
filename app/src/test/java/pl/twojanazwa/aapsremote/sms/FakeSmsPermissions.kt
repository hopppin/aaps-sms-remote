package pl.twojanazwa.aapsremote.sms

/** Podstawka z przełączanymi uprawnieniami. */
class FakeSmsPermissions(
    @Volatile var send: Boolean = true,
    @Volatile var receive: Boolean = true,
) : SmsPermissions {
    override fun canSend(): Boolean = send

    override fun canReceive(): Boolean = receive
}
