package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsSamples

/** Wspólne scenariusze CARBS po wysłaniu kodu (używane też przez testy niezmienników). */
fun carbsCommand(grams: Int): Command = (Command.Carbs.of(grams) as CarbsValidation.Valid).command

/** Przesuwa czas wirtualny do [millis] i dostarcza SMS odebrany w tej chwili. */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.deliverAt(source: FakeIncomingSmsSource, millis: Long, body: String) {
    advanceTimeBy(millis - currentTime)
    runCurrent()
    source.emit(IncomingSms(body, SessionTestFixtures.T0 + millis))
    runCurrent()
}

/** Dostarcza prośbę o kod w chwili [millis]; sesja od razu zaczyna wysyłać kod (kod RFC ważny w tej chwili). */
suspend fun TestScope.requestCodeAt(source: FakeIncomingSmsSource, millis: Long = 60_000) =
    deliverAt(source, millis, AapsSamples.carbsCodeRequest)

/** Port: polecenie wychodzi od razu, kod czeka [codeDelayMillis] ms i zwraca [codeResult]. */
fun delayedCodeSender(
    codeDelayMillis: Long,
    codeResult: SendResult = SendResult.Sent,
    onCode: (CharArray) -> Unit = {},
): SmsSender = SmsSender { message ->
    when (message) {
        is OutgoingSms.CommandText -> SendResult.Sent
        is OutgoingSms.CodeReply -> {
            onCode(message.chars)
            delay(codeDelayMillis)
            codeResult
        }
    }
}

/** Port, który dla kodu nigdy nie zwraca wyniku. */
fun hangingCodeSender(onCode: (CharArray) -> Unit = {}): SmsSender = SmsSender { message ->
    when (message) {
        is OutgoingSms.CommandText -> SendResult.Sent
        is OutgoingSms.CodeReply -> {
            onCode(message.chars)
            CompletableDeferred<Unit>().await()
            SendResult.Sent
        }
    }
}
