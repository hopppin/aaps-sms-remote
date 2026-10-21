package pl.twojanazwa.aapsremote.ui.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SecureWindowCounterTest {
    private val changes = mutableListOf<Boolean>()
    private val counter = SecureWindowCounter { changes += it }

    @Test
    fun `CMDUI-23 dwa zrodla ochrony, jedno wylaczone - flaga nadal wlaczona`() {
        counter.acquire()
        counter.acquire()
        counter.release()
        assertEquals(listOf(true), changes)
        counter.release()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `CMDUI-23 release przy liczniku 0 nie wywoluje onChange i nie schodzi ponizej zera`() {
        counter.release()
        counter.release()
        assertEquals(emptyList<Boolean>(), changes)
        counter.acquire()
        counter.release()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `CMDUI-23 ponowne zgloszenie po zwolnieniu wlacza flage ponownie`() {
        counter.acquire()
        counter.release()
        counter.acquire()
        assertEquals(listOf(true, false, true), changes)
    }
}
