package pl.twojanazwa.aapsremote.otp

data class TotpWindow(
    val validFromEpochMillis: Long,
    val validUntilEpochMillis: Long,
    val remainingMillis: Long,
)
