package pl.twojanazwa.aapsremote.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class SessionTimeoutsTest {

    @Test
    fun `SESSION-01 domyslne limity to 180000 ms i 360000 ms`() {
        val timeouts = SessionTimeouts()
        assertEquals(180_000L, timeouts.reply.inWholeMilliseconds)
        assertEquals(360_000L, timeouts.confirmation.inWholeMilliseconds)
    }

    @Test
    fun `SESSION-01 wlasny limit odpowiedzi 30000 ms jest odczytany`() {
        assertEquals(30_000L, SessionTimeouts(reply = 30_000.milliseconds).reply.inWholeMilliseconds)
    }

    @Test
    fun `SESSION-01 limit odpowiedzi 0 ms rzuca IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { SessionTimeouts(reply = 0.milliseconds) }
    }

    @Test
    fun `SESSION-01 ujemny limit potwierdzenia rzuca IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { SessionTimeouts(confirmation = (-1).milliseconds) }
    }
}
