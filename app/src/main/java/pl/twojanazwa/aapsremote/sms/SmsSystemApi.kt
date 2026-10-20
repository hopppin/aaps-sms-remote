package pl.twojanazwa.aapsremote.sms

/** Cienka warstwa nad SmsManager domyślnej karty i receiverem wyniku. Implementacja: platform. */
interface SmsSystemApi {
    /** SmsManager.divideMessage. Rzuca IllegalStateException, gdy brak SmsManager. */
    fun divideMessage(text: String): List<String>

    /**
     * Rejestruje odbiór wyników wysłania dla jednego wywołania send() (unikalna akcja).
     * [onPartResult] (indeks części, wynik == RESULT_OK) wołane z wątku głównego.
     */
    fun registerSentResults(onPartResult: (partIndex: Int, ok: Boolean) -> Unit): SentResultsRegistration

    /** 1 część: sendTextMessage, więcej: sendMultipartTextMessage; PendingIntent wyniku dla każdej części, bez deliveryIntent. */
    fun sendParts(destination: String, parts: List<String>, registration: SentResultsRegistration)
}

/** Uchwyt rejestracji wyników jednego wysłania. Nie przechowuje treści. */
interface SentResultsRegistration {
    /** Wyrejestrowuje receiver i anuluje PendingIntent; idempotentne, nie rzuca. */
    fun unregister()
}
