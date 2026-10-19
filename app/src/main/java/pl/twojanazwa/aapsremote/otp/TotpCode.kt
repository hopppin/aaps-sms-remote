package pl.twojanazwa.aapsremote.otp

/** Kod 6-cyfrowy z oknem ważności [validFromEpochMillis, validUntilEpochMillis). */
class TotpCode(
    val value: String,
    val validFromEpochMillis: Long,
    val validUntilEpochMillis: Long,
) {
    fun remainingMillis(nowEpochMillis: Long): Long = validUntilEpochMillis - nowEpochMillis

    override fun toString(): String =
        "TotpCode(***, validUntilEpochMillis=$validUntilEpochMillis)"
}
