package pl.twojanazwa.aapsremote.sms

import java.security.MessageDigest

/**
 * SMS-06. Pamięć ostatnich [capacity] kluczy (FIFO), metody synchronizowane.
 * Klucz = SHA-256 z (nadawca, znacznik centrum SMS, treść) – treść nie jest przechowywana.
 */
class DeliveredSmsRegistry(private val capacity: Int = DEFAULT_CAPACITY) {
    private val keys = LinkedHashSet<String>()

    init {
        require(capacity >= 1) { "Pojemnosc musi byc co najmniej 1" }
    }

    /** true i zapamiętanie przy pierwszym wystąpieniu; false dla duplikatu (bez zmiany kolejności). */
    @Synchronized
    fun markFirstDelivery(sms: AssembledSms): Boolean {
        if (!keys.add(keyOf(sms))) return false
        if (keys.size > capacity) {
            val iterator = keys.iterator()
            iterator.next()
            iterator.remove()
        }
        return true
    }

    @Synchronized
    override fun toString(): String = "DeliveredSmsRegistry(size=${keys.size})"

    private fun keyOf(sms: AssembledSms): String {
        val sender = sms.sender
        val text = "${sender?.length ?: -1}:${sender.orEmpty()}|${sms.serviceCenterTimestampMillis}|${sms.body}"
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 100
    }
}
