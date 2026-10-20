package pl.twojanazwa.aapsremote.sms

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.session.IncomingSms
import pl.twojanazwa.aapsremote.session.IncomingSmsSource

/**
 * Implementacja [IncomingSmsSource] (SMS-04 – SMS-06). Jedna na aplikację.
 * Kanał `UNLIMITED` na kolektora, jedna rejestracja receivera. Brak `RECEIVE_SMS` albo wyjątek
 * rejestratora (SMS-07, SMS-08) nie przerywa strumienia: kolektor czeka bez wiadomości,
 * a kolejny kolektor ponawia sprawdzenie uprawnienia i rejestrację.
 */
@Singleton
class IncomingSmsHub @Inject constructor(
    private val registrar: SmsBroadcastRegistrar,
    private val permissions: SmsPermissions,
    private val numberProvider: AapsNumberProvider,
    private val clock: EpochClock,
    private val registry: DeliveredSmsRegistry,
) : IncomingSmsSource {
    private val lock = Any()
    private val subscribers = mutableListOf<Channel<IncomingSms>>()
    private var registered = false

    override fun messages(): Flow<IncomingSms> = flow {
        val channel = Channel<IncomingSms>(Channel.UNLIMITED)
        try {
            join(channel)
            for (sms in channel) emit(sms)
        } finally {
            leave(channel)
        }
    }

    private fun join(channel: Channel<IncomingSms>) {
        synchronized(lock) {
            subscribers.add(channel)
            if (!registered && hasReceivePermission()) {
                try {
                    registrar.register(::onBroadcast)
                    registered = true
                } catch (e: Exception) {
                    // Rejestracja nieudana (np. SecurityException): kolektor czeka bez wiadomości.
                }
            }
        }
    }

    /** Wyjątek z [SmsPermissions.canReceive] traktowany jest jak brak uprawnienia. */
    private fun hasReceivePermission(): Boolean = try {
        permissions.canReceive()
    } catch (e: Exception) {
        false
    }

    private fun leave(channel: Channel<IncomingSms>) {
        synchronized(lock) {
            subscribers.remove(channel)
            channel.close()
            if (subscribers.isEmpty() && registered) {
                registered = false
                try {
                    registrar.unregister()
                } catch (e: Exception) {
                    // Wyrejestrowanie nieudane (np. IllegalArgumentException): kończymy zwykłym anulowaniem.
                }
            }
        }
    }

    /** Obsługa jednego broadcastu (SMS-04 – SMS-06). Nie rzuca wyjątków. */
    internal fun onBroadcast(parts: List<RawSmsPart>) {
        try {
            val receivedAt = clock.nowEpochMillis()
            val number = AapsNumber.parse(numberProvider.currentNumber()) ?: return
            val fresh = SmsAssembler.assemble(parts)
                .filter { number.matchesSender(it.sender) }
                .filter { registry.markFirstDelivery(it) }
            val targets = synchronized(lock) { subscribers.toList() }
            for (sms in fresh) {
                val incoming = IncomingSms(sms.body, receivedAt)
                for (target in targets) target.trySend(incoming)
            }
        } catch (e: Exception) {
            // Broadcast pominięty; wyjątek nie jest ani logowany, ani propagowany (może nieść dane).
        }
    }

    override fun toString(): String = "IncomingSmsHub(***)"
}
