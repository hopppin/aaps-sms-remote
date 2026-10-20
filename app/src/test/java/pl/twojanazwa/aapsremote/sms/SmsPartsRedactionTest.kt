package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples

class SmsPartsRedactionTest {
    private val sender = "+447700900123"
    private val body = AapsSamples.bg

    private fun assertRedacted(text: String) {
        assertFalse(text.contains("7700"), "numer w toString")
        assertFalse(text.contains("210"), "tresc w toString")
        body.lines().filter { it.isNotBlank() }.forEach {
            assertFalse(text.contains(it), "linia tresci w toString")
        }
    }

    @Test
    fun `SMS-16 RawSmsPart toString nie zawiera nadawcy ani tresci`() {
        assertRedacted(RawSmsPart(sender, body, 1111111000000L).toString())
    }

    @Test
    fun `SMS-16 AssembledSms toString nie zawiera nadawcy ani tresci`() {
        assertRedacted(AssembledSms(sender, body, 1111111000000L).toString())
    }

    @Test
    fun `SMS-16 DeliveredSmsRegistry toString nie zawiera nadawcy tresci ani skrotu`() {
        val registry = DeliveredSmsRegistry()
        registry.markFirstDelivery(AssembledSms(sender, body, 1111111000000L))
        val text = registry.toString()
        assertRedacted(text)
        assertFalse(Regex("[0-9a-f]{64}").containsMatchIn(text), "toString() nie moze zawierac skrotu SHA-256")
    }
}
