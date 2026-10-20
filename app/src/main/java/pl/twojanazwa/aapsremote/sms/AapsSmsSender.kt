package pl.twojanazwa.aapsremote.sms

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import pl.twojanazwa.aapsremote.session.OutgoingSms
import pl.twojanazwa.aapsremote.session.SendResult
import pl.twojanazwa.aapsremote.session.SmsSender

/** Implementacja SmsSender. Pola wyłącznie niezmienne zależności (SMS-13). */
@Singleton
class AapsSmsSender @Inject constructor(
    private val numberProvider: AapsNumberProvider,
    @Suppress("unused") private val permissions: SmsPermissions,
    private val system: SmsSystemApi,
) : SmsSender {
    override suspend fun send(message: OutgoingSms): SendResult {
        // SMS-09: numer odczytywany przy każdym wywołaniu.
        val number = AapsNumber.parse(numberProvider.currentNumber()) ?: return SendResult.Failed
        // Tekst tylko jako zmienna lokalna (SMS-13).
        val text = when (message) {
            is OutgoingSms.CommandText -> message.text
            is OutgoingSms.CodeReply -> String(message.chars)
        }
        return sendAndAwait(number.destination, text)
    }

    private suspend fun sendAndAwait(destination: String, text: String): SendResult {
        val parts = system.divideMessage(text)
        if (parts.isEmpty()) return SendResult.Failed
        return suspendCancellableCoroutine { cont ->
            val tracker = MultipartSendTracker(parts.size)
            val holder = AtomicReference<SentResultsRegistration?>(null)
            val registration = system.registerSentResults { index, ok ->
                tracker.onPartResult(index, ok)?.let { result ->
                    holder.get()?.unregister()
                    cont.resume(result)
                }
            }
            holder.set(registration)
            system.sendParts(destination, parts, registration)
        }
    }

    override fun toString(): String = "AapsSmsSender(***)"
}
