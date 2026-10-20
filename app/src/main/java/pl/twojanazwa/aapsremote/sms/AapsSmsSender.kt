package pl.twojanazwa.aapsremote.sms

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import pl.twojanazwa.aapsremote.session.OutgoingSms
import pl.twojanazwa.aapsremote.session.SendResult
import pl.twojanazwa.aapsremote.session.SmsSender

/**
 * Implementacja SmsSender. Pola wyłącznie niezmienne zależności (SMS-13).
 * Nie rzuca niczego poza CancellationException (SMS-11, SMS-12); wyjątki nie niosą treści.
 */
@Singleton
class AapsSmsSender @Inject constructor(
    private val numberProvider: AapsNumberProvider,
    private val permissions: SmsPermissions,
    private val system: SmsSystemApi,
) : SmsSender {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun send(message: OutgoingSms): SendResult {
        return try {
            // SMS-11: anulowana korutyna nie może wysłać SMS.
            currentCoroutineContext().ensureActive()
            // SMS-12: uprawnienie sprawdzane przy każdym send, przed odczytem numeru.
            if (!permissions.canSend()) return SendResult.Failed
            // SMS-09: numer odczytywany przy każdym wywołaniu.
            val number = AapsNumber.parse(numberProvider.currentNumber()) ?: return SendResult.Failed
            // Tekst tylko jako zmienna lokalna (SMS-13).
            val text = when (message) {
                is OutgoingSms.CommandText -> message.text
                is OutgoingSms.CodeReply -> String(message.chars)
            }
            sendAndAwait(number.destination, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failed
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendAndAwait(destination: String, text: String): SendResult {
        val parts = system.divideMessage(text)
        if (parts.isEmpty()) return SendResult.Failed
        return suspendCancellableCoroutine { cont ->
            val tracker = MultipartSendTracker(parts.size)
            val holder = AtomicReference<SentResultsRegistration?>(null)
            val registration = system.registerSentResults { index, ok ->
                // SMS-11: spóźniony wynik po anulowaniu jest ignorowany.
                if (!cont.isActive) return@registerSentResults
                tracker.onPartResult(index, ok)?.let { result ->
                    holder.get()?.unregister()
                    cont.resume(result)
                }
            }
            holder.set(registration)
            cont.invokeOnCancellation { registration.unregister() }
            // SMS-11: anulowanie w trakcie rejestracji – nie wysyłaj.
            if (!cont.isActive) return@suspendCancellableCoroutine
            try {
                system.sendParts(destination, parts, registration)
            } catch (e: Exception) {
                registration.unregister()
                if (cont.isActive) cont.resume(SendResult.Failed)
            }
        }
    }

    override fun toString(): String = "AapsSmsSender(***)"
}
