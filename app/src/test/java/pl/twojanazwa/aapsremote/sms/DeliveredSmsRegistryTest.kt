package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples

class DeliveredSmsRegistryTest {
    private val aaps = "+447700900123"

    private fun sms(body: String, ts: Long = 1111111000000L, sender: String? = aaps) =
        AssembledSms(sender, body, ts)

    @Test
    fun `SMS-06 ta sama wiadomosc zgloszona dwa razy jest duplikatem`() {
        val registry = DeliveredSmsRegistry()
        val body = AapsSamples.carbsCodeRequest
        assertTrue(registry.markFirstDelivery(sms(body)))
        assertFalse(registry.markFirstDelivery(sms(body)))
    }

    @Test
    fun `SMS-06 ta sama tresc z innym znacznikiem centrum SMS nie jest duplikatem`() {
        val registry = DeliveredSmsRegistry()
        val body = AapsSamples.carbsCodeRequest
        assertTrue(registry.markFirstDelivery(sms(body, 1111111000000L)))
        assertTrue(registry.markFirstDelivery(sms(body, 1111111060000L)))
    }

    @Test
    fun `SMS-06 ta sama tresc od innego nadawcy nie jest duplikatem`() {
        val registry = DeliveredSmsRegistry()
        assertTrue(registry.markFirstDelivery(sms("x")))
        assertTrue(registry.markFirstDelivery(sms("x", sender = "+447700900999")))
    }

    @Test
    fun `SMS-06 sklejenie nadawcy i znacznika nie tworzy kolizji klucza`() {
        val registry = DeliveredSmsRegistry()
        assertTrue(registry.markFirstDelivery(AssembledSms("1", "2|3", 4L)))
        assertTrue(registry.markFirstDelivery(AssembledSms("1|2", "3", 4L)))
    }

    @Test
    fun `SMS-06 setna wiadomosc po W wypycha W z pamieci`() {
        val registry = DeliveredSmsRegistry()
        val w = sms("W")
        assertTrue(registry.markFirstDelivery(w))
        repeat(100) { assertTrue(registry.markFirstDelivery(sms("inna $it"))) }
        assertTrue(registry.markFirstDelivery(w))
    }

    @Test
    fun `SMS-06 dziewiecdziesiat dziewiec wiadomosci po W nie wypycha W`() {
        val registry = DeliveredSmsRegistry()
        val w = sms("W")
        assertTrue(registry.markFirstDelivery(w))
        repeat(99) { assertTrue(registry.markFirstDelivery(sms("inna $it"))) }
        assertFalse(registry.markFirstDelivery(w))
    }

    @Test
    fun `SMS-06 duplikat nie zmienia kolejnosci usuwania FIFO`() {
        val registry = DeliveredSmsRegistry(capacity = 2)
        assertTrue(registry.markFirstDelivery(sms("A")))
        assertTrue(registry.markFirstDelivery(sms("B")))
        assertFalse(registry.markFirstDelivery(sms("A")))
        assertTrue(registry.markFirstDelivery(sms("C"))) // wypycha A, nie B
        assertFalse(registry.markFirstDelivery(sms("B")))
        assertTrue(registry.markFirstDelivery(sms("A")))
    }

    @Test
    fun `SMS-06 pojemnosc mniejsza niz 1 jest odrzucona`() {
        assertThrows(IllegalArgumentException::class.java) { DeliveredSmsRegistry(capacity = 0) }
    }
}
