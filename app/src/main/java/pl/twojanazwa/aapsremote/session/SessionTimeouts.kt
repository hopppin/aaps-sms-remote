package pl.twojanazwa.aapsremote.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** SESSION-01. */
data class SessionTimeouts(
    val reply: Duration = 3.minutes,
    val confirmation: Duration = 6.minutes,
) {
    init {
        require(reply.isPositive() && confirmation.isPositive()) { "timeouts must be positive" }
    }
}

object SessionLimits {
    /** A1: czas na kod w AAPS (Constants.SMS_CONFIRM_TIMEOUT) – informacyjnie, do opisu marginesu. */
    const val AAPS_CODE_VALIDITY_MILLIS: Long = 300_000

    /** SESSION-10: maks. wiek prośby o kod w chwili rozpoczęcia wysyłania kodu. */
    const val CODE_REQUEST_MAX_AGE_MILLIS: Long = 240_000

    /** SESSION-10: maks. czas fazy PREPARING_CODE (czas korutyn). */
    const val CODE_PREPARATION_TIMEOUT_MILLIS: Long = 10_000
}
