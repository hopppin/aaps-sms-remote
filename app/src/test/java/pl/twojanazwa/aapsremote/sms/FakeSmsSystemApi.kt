package pl.twojanazwa.aapsremote.sms

/**
 * Atrapa warstwy systemowej: zapisuje zlecenia, wyniki części zgłaszane ręcznie.
 * Opcjonalne wyjątki symulują błędy platformy; toString() bez treści.
 */
class FakeSmsSystemApi(
    private val divider: (String) -> List<String> = { listOf(it) },
) : SmsSystemApi {
    class SendRequest(val destination: String, val parts: List<String>)

    var divideException: Exception? = null
    var registerException: Exception? = null
    var sendPartsException: Exception? = null

    val requests: MutableList<SendRequest> = mutableListOf()
    var unregisterCount: Int = 0
        private set
    var divideCalls: Int = 0
        private set
    var registerCalls: Int = 0
        private set
    var sendPartsCalls: Int = 0
        private set

    val totalCalls: Int get() = divideCalls + registerCalls + sendPartsCalls

    private var onPartResult: ((Int, Boolean) -> Unit)? = null

    override fun divideMessage(text: String): List<String> {
        divideCalls++
        divideException?.let { throw it }
        return divider(text)
    }

    override fun registerSentResults(
        onPartResult: (partIndex: Int, ok: Boolean) -> Unit,
    ): SentResultsRegistration {
        registerCalls++
        registerException?.let { throw it }
        this.onPartResult = onPartResult
        return object : SentResultsRegistration {
            override fun unregister() {
                unregisterCount++
            }
        }
    }

    override fun sendParts(destination: String, parts: List<String>, registration: SentResultsRegistration) {
        sendPartsCalls++
        sendPartsException?.let { throw it }
        requests += SendRequest(destination, parts.toList())
    }

    /** Zgłasza wynik części tak jak receiver wyniku. */
    fun reportPart(partIndex: Int, ok: Boolean) {
        val callback = checkNotNull(onPartResult) { "brak rejestracji" }
        callback(partIndex, ok)
    }

    override fun toString(): String = "FakeSmsSystemApi(***)"
}
