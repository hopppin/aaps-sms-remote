package pl.twojanazwa.aapsremote.sms

/** Jedna część z intentu SMS_RECEIVED. toString() bez nadawcy i treści (SMS-16). */
class RawSmsPart(
    val sender: String?,
    val body: String?,
    val serviceCenterTimestampMillis: Long,
) {
    override fun toString(): String = "RawSmsPart(***)"
}

/** Wiadomość złożona z części jednego nadawcy z jednego broadcastu. toString() bez nadawcy i treści (SMS-16). */
class AssembledSms(
    val sender: String?,
    val body: String,
    val serviceCenterTimestampMillis: Long,
) {
    override fun toString(): String = "AssembledSms(***)"
}
