package pl.twojanazwa.aapsremote.ui.settings.secret

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClipboardClearingTest {
    private val uriRfc =
        "otpauth://totp/AAPS?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=AAPS&algorithm=SHA1&digits=6&period=30"

    @Test
    fun `UI-24 identyczny tekst w schowku czysci schowek dokladnie raz`() {
        val clipboard = FakeSecretClipboard(uriRfc)
        clearClipboardIfPasted(clipboard, uriRfc)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun `UI-24 inny tekst w schowku nie czysci`() {
        val clipboard = FakeSecretClipboard("otpauth://totp/INNE?secret=GEZDGNBV")
        clearClipboardIfPasted(clipboard, uriRfc)
        assertEquals(0, clipboard.clearCount)
    }

    @Test
    fun `UI-24 tekst ze spacja na poczatku nie czysci`() {
        val clipboard = FakeSecretClipboard(" $uriRfc")
        clearClipboardIfPasted(clipboard, uriRfc)
        assertEquals(0, clipboard.clearCount)
    }

    @Test
    fun `UI-24 pusty schowek nie czysci`() {
        val clipboard = FakeSecretClipboard(null)
        clearClipboardIfPasted(clipboard, uriRfc)
        assertEquals(0, clipboard.clearCount)
    }

    @Test
    fun `UI-24 wyjatek przy odczycie nie czysci i nie wycieka`() {
        val clipboard = FakeSecretClipboard(uriRfc).apply { throwOnRead = true }
        clearClipboardIfPasted(clipboard, uriRfc)
        assertEquals(0, clipboard.clearCount)
    }

    @Test
    fun `UI-24 pusty tekst pola nie czyta schowka`() {
        val clipboard = FakeSecretClipboard(uriRfc)
        clearClipboardIfPasted(clipboard, "")
        assertEquals(0, clipboard.readCount)
        assertEquals(0, clipboard.clearCount)
    }
}
