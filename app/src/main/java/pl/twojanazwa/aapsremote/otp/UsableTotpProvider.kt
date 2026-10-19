package pl.twojanazwa.aapsremote.otp

import kotlinx.coroutines.delay

/**
 * Zwraca kod TOTP, który ma jeszcze co najmniej [MIN_REMAINING_MILLIS] ważności (TOTP-02).
 * Czas pochodzi wyłącznie z wstrzykniętego [clock] (TOTP-04). Bez stanu między wywołaniami;
 * nie loguje kodów ani sekretu.
 */
class UsableTotpProvider(private val clock: EpochClock) {

    /** Czeka (anulowalnie), aż bieżący kod będzie miał zapas ważności, i zwraca go. */
    suspend fun awaitUsableCode(secret: TotpSecret): TotpCode {
        while (true) {
            val now = clock.nowEpochMillis()
            val window = TotpGenerator.window(now)
            if (window.remainingMillis >= MIN_REMAINING_MILLIS) {
                return TotpGenerator.generate(secret, now)
            }
            delay(window.remainingMillis)
        }
    }

    companion object {
        const val MIN_REMAINING_MILLIS: Long = 5_000
    }
}
