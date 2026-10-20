package pl.twojanazwa.aapsremote.sms

/** Atrapa warstwy systemowej: zapisuje zlecenia, wyniki części zgłaszane ręcznie. */
class FakeSmsSystemApi(
    private val divider: (String) -> List<String> = { listOf(it) },
) : SmsSystemApi {
    class SendRequest(val destination: String, val parts: List<String>)

    val requests: MutableList<SendRequest> = mutableListOf()
    var unregisterCount: Int = 0
        private set

    private var onPartResult: ((Int, Boolean) -> Unit)? = null

    override fun divideMessage(text: String): List<String> = divider(text)

    override fun registerSentResults(
        onPartResult: (partIndex: Int, ok: Boolean) -> Unit,
    ): SentResultsRegistration {
        this.onPartResult = onPartResult
        return object : SentResultsRegistration {
            override fun unregister() {
                unregisterCount++
            }
        }
    }

    override fun sendParts(destination: String, parts: List<String>, registration: SentResultsRegistration) {
        requests += SendRequest(destination, parts.toList())
    }

    /** Zgłasza wynik części tak jak receiver wyniku. */
    fun reportPart(partIndex: Int, ok: Boolean) {
        val callback = checkNotNull(onPartResult) { "brak rejestracji" }
        callback(partIndex, ok)
    }
}
