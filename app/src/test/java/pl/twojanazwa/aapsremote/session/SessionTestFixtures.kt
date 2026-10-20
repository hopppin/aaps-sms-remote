package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.AapsPin

/** Wspólne dane testów sesji. Sekret to wektor RFC 6238, PIN jest fikcyjny. */
object SessionTestFixtures {
    /** Chwila z RFC 6238 (T = 1111111051 s). */
    const val T0: Long = 1111111051000

    const val PIN_TEXT = "4321"

    fun secret(): TotpSecret = TotpSecret.fromBytes("12345678901234567890".toByteArray(Charsets.US_ASCII))

    fun pin(): AapsPin = AapsPin.fromChars(PIN_TEXT.toCharArray())

    fun credentials(): CodeCredentials = CodeCredentials(pin(), secret())
}

/** Sesja na czasie wirtualnym (zegar i znaczniki limitów z planisty testu). */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.testSession(
    command: Command,
    sender: SmsSender,
    incoming: IncomingSmsSource,
    credentials: CodeCredentials? = null,
    timeouts: SessionTimeouts = SessionTimeouts(),
    clock: EpochClock = virtualClock(),
    timeSource: kotlin.time.TimeSource.WithComparableMarks = testScheduler.timeSource,
): CommandSession = CommandSession(
    command = command,
    credentials = credentials,
    sender = sender,
    incoming = incoming,
    clock = clock,
    totp = UsableTotpProvider(clock),
    timeouts = timeouts,
    timeSource = timeSource,
)

/**
 * Źródło czasu spóźnione o 1 ms po starcie (odczyt = czas wirtualny - 1 ms dla czasu > 0): delay potrafi
 * wybudzić korutynę, zanim znacznik uzna termin za minięty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.laggingTimeSource(): kotlin.time.TimeSource.WithComparableMarks =
    object : kotlin.time.AbstractLongTimeSource(kotlin.time.DurationUnit.MILLISECONDS) {
        override fun read(): Long = testScheduler.currentTime.let { if (it > 0) it - 1 else 0 }
    }

/** Zegar na czasie wirtualnym: T0 + upłynięty czas testu. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.virtualClock(): EpochClock = EpochClock { SessionTestFixtures.T0 + testScheduler.currentTime }
