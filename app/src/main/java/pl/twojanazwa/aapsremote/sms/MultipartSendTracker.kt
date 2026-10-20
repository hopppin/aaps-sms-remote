package pl.twojanazwa.aapsremote.sms

import pl.twojanazwa.aapsremote.session.SendResult

/**
 * SMS-10. Synchronizowane. Wynik zwracany dokładnie raz, w wywołaniu, które go ustala; poza tym null.
 * ok=false daje Failed od razu; Sent dopiero, gdy wszystkie indeksy 0 until partCount zgłosiły ok=true.
 * Powtórzenia i indeksy spoza zakresu są ignorowane.
 */
class MultipartSendTracker(private val partCount: Int) {
    private val lock = Any()
    private val accepted: BooleanArray
    private var acceptedCount = 0
    private var settled = false

    init {
        require(partCount >= 1) { "partCount musi byc >= 1" }
        accepted = BooleanArray(partCount)
    }

    fun onPartResult(partIndex: Int, ok: Boolean): SendResult? = synchronized(lock) {
        when {
            settled || partIndex !in 0 until partCount -> null
            !ok -> {
                settled = true
                SendResult.Failed
            }
            accepted[partIndex] -> null
            else -> {
                accepted[partIndex] = true
                acceptedCount++
                if (acceptedCount == partCount) {
                    settled = true
                    SendResult.Sent
                } else {
                    null
                }
            }
        }
    }

    override fun toString(): String = "MultipartSendTracker(***)"
}
