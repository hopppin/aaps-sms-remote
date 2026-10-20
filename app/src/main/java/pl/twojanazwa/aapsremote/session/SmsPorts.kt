package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.flow.Flow

/**
 * Wiadomość od numeru AAPS, już po filtrze numeru i sklejeniu części.
 * receivedAtEpochMillis: odczyt tego samego EpochClock co sesja w chwili odbioru.
 * Każda wiadomość dostarczana najwyżej raz.
 */
data class IncomingSms(val body: String, val receivedAtEpochMillis: Long) {
    override fun toString(): String = "IncomingSms(***, receivedAtEpochMillis=$receivedAtEpochMillis)"
}

/** Gorący strumień (bez powtórek starych wiadomości). */
fun interface IncomingSmsSource {
    fun messages(): Flow<IncomingSms>
}

sealed interface OutgoingSms {
    /** command.smsText(). toString() bez treści (ilość CARBS). */
    class CommandText(val text: String) : OutgoingSms {
        override fun toString(): String = "OutgoingSms.CommandText(***)"
    }

    /**
     * "<6 cyfr><PIN>". Tablica należy do sesji i jest zerowana po powrocie z send();
     * port nie przechowuje jej ani kopii po powrocie.
     */
    class CodeReply(val chars: CharArray) : OutgoingSms {
        override fun toString(): String = "OutgoingSms.CodeReply(***)"
    }
}

sealed interface SendResult {
    data object Sent : SendResult

    data object Failed : SendResult
}

/** Zawiesza się do wyniku wysłania. Anulowalne. */
fun interface SmsSender {
    suspend fun send(message: OutgoingSms): SendResult
}
