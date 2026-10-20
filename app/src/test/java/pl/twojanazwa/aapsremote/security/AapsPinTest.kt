package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AapsPinTest {

    @Test
    fun `SESSION-02 utworzenie z tablicy zeruje tablice wejsciowa`() {
        val source = charArrayOf('4', '3', '2', '1')
        val pin = AapsPin.fromChars(source)
        assertArrayEquals(CharArray(4), source)
        assertEquals(4, pin.length)
        assertArrayEquals(charArrayOf('4', '3', '2', '1'), pin.copyChars())
    }

    @Test
    fun `SESSION-02 pusta tablica rzuca IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { AapsPin.fromChars(CharArray(0)) }
    }

    @Test
    fun `SESSION-02 podwojne czyszczenie oznacza PIN jako wyczyszczony a odczyt rzuca`() {
        val pin = AapsPin.fromChars("4321".toCharArray())
        assertFalse(pin.isCleared)
        pin.clear()
        pin.clear()
        assertTrue(pin.isCleared)
        assertThrows(IllegalStateException::class.java) { pin.copyChars() }
    }
}
