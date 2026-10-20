package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.SendResult

class MultipartSendTrackerTest {
    @Test
    fun `SMS-10 jedna czesc ok daje Sent`() {
        assertEquals(SendResult.Sent, MultipartSendTracker(1).onPartResult(0, true))
    }

    @Test
    fun `SMS-10 Sent dopiero po ok wszystkich czesci w dowolnej kolejnosci`() {
        val t = MultipartSendTracker(3)
        assertNull(t.onPartResult(0, true))
        assertNull(t.onPartResult(2, true))
        assertEquals(SendResult.Sent, t.onPartResult(1, true))
    }

    @Test
    fun `SMS-10 pierwszy blad daje Failed od razu`() {
        assertEquals(SendResult.Failed, MultipartSendTracker(3).onPartResult(1, false))
    }

    @Test
    fun `SMS-10 powtorzony indeks nie liczy sie dwa razy`() {
        val t = MultipartSendTracker(2)
        assertNull(t.onPartResult(0, true))
        assertNull(t.onPartResult(0, true))
        assertEquals(SendResult.Sent, t.onPartResult(1, true))
    }

    @Test
    fun `SMS-10 indeks spoza zakresu jest ignorowany`() {
        val t = MultipartSendTracker(2)
        assertNull(t.onPartResult(-1, false))
        assertNull(t.onPartResult(2, true))
        assertNull(t.onPartResult(0, true))
        assertEquals(SendResult.Sent, t.onPartResult(1, true))
    }

    @Test
    fun `SMS-10 po ustaleniu wyniku kazde zdarzenie daje null`() {
        val sent = MultipartSendTracker(1).apply { onPartResult(0, true) }
        assertNull(sent.onPartResult(0, false))
        assertNull(sent.onPartResult(5, true))
        val failed = MultipartSendTracker(2).apply { onPartResult(0, false) }
        assertNull(failed.onPartResult(0, true))
        assertNull(failed.onPartResult(1, true))
        assertNull(failed.onPartResult(1, false))
    }

    @Test
    fun `SMS-10 liczba czesci mniejsza od 1 jest odrzucona`() {
        assertThrows(IllegalArgumentException::class.java) { MultipartSendTracker(0) }
    }
}
