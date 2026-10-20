package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.session.IncomingSms

/** Zegar z kolejką wartości (lub wyjątkiem) i licznikiem odczytów. Ostatnia wartość się powtarza. */
class QueueClock(vararg values: Long) : EpochClock {
    private val queue = ArrayDeque(values.toList())
    private var last = 0L

    @Volatile
    var failNext = false
    var reads = 0
        private set

    override fun nowEpochMillis(): Long {
        reads++
        if (failNext) throw IllegalStateException("zegar niedostepny")
        if (queue.isNotEmpty()) last = queue.removeFirst()
        return last
    }
}

class MutableNumberProvider(@Volatile var number: String?) : AapsNumberProvider {
    override fun currentNumber(): String? = number
}

/** Hub z atrapami; [registry] można współdzielić między hubami (dwie sesje). */
class HubFixture(
    val clock: QueueClock = QueueClock(1_000L),
    val numberProvider: MutableNumberProvider = MutableNumberProvider(AAPS),
    val registry: DeliveredSmsRegistry = DeliveredSmsRegistry(),
    val registrar: FakeSmsBroadcastRegistrar = FakeSmsBroadcastRegistrar(),
) {
    val hub = IncomingSmsHub(registrar, FakeSmsPermissions(), numberProvider, clock, registry)

    /** Kolektor dołączający synchronicznie; zebrane wiadomości trafiają do [received]. */
    fun collect(scope: CoroutineScope, received: MutableList<IncomingSms>): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) { hub.messages().collect { received.add(it) } }

    companion object {
        const val AAPS = "+447700900123"
        const val OTHER = "+447700900999"
    }
}
