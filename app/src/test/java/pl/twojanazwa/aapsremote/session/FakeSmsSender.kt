package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** Atrapa portu SmsSender. Zapisuje kopie treści; dla CodeReply także referencję tablicy. */
class FakeSmsSender : SmsSender {
    var commandResult: SendResult = SendResult.Sent
    var codeResult: SendResult = SendResult.Sent
    var delayMillis: Long = 0
    var failure: Throwable? = null
    var suspendForever: Boolean = false

    /** Treści wysłanych poleceń. */
    val sentCommands = mutableListOf<String>()

    /** Kopie kodów (wartość w chwili wysyłania). */
    val sentCodeCopies = mutableListOf<String>()

    /** Referencje tablic z CodeReply (do sprawdzania zerowania po send()). */
    val codeArrays = mutableListOf<CharArray>()

    val sendCount: Int get() = sentCommands.size + sentCodeCopies.size

    override suspend fun send(message: OutgoingSms): SendResult {
        when (message) {
            is OutgoingSms.CommandText -> sentCommands += message.text
            is OutgoingSms.CodeReply -> {
                sentCodeCopies += String(message.chars)
                codeArrays += message.chars
            }
        }
        if (delayMillis > 0) delay(delayMillis)
        failure?.let { throw it }
        if (suspendForever) CompletableDeferred<Unit>().await()
        return if (message is OutgoingSms.CodeReply) codeResult else commandResult
    }
}
