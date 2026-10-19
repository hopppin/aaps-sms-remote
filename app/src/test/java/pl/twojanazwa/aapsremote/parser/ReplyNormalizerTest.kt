package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReplyNormalizerTest {

    @Test
    fun `PARSER-02 CRLF zamieniane na LF`() {
        assertEquals("a\nb\nc", ReplyNormalizer.normalize("a\r\nb\r\nc"))
    }

    @Test
    fun `PARSER-02 samotny CR zamieniany na LF`() {
        assertEquals("a\nb\nc", ReplyNormalizer.normalize("a\rb\rc"))
    }

    @Test
    fun `PARSER-02 mieszane konce linii`() {
        assertEquals("a\nb\nc", ReplyNormalizer.normalize("a\r\nb\rc"))
    }

    @Test
    fun `PARSER-02 koncowe spacje i tabulatory linii usuwane`() {
        assertEquals("a\nb\nc", ReplyNormalizer.normalize("a \t\nb\t \r\nc  "))
    }

    @Test
    fun `PARSER-02 trim calosci obejmuje spacje tabulatory i nowe linie`() {
        assertEquals("a b", ReplyNormalizer.normalize(" \t\n\r\na b\n\r\n \t"))
    }

    @Test
    fun `PARSER-02 wielkie litery podwojne spacje i NBSP zachowane`() {
        val text = "Ost.  POL x"
        assertEquals(text, ReplyNormalizer.normalize(text))
    }

    @Test
    fun `PARSER-02 poczatkowe spacje linii pozostaja`() {
        assertEquals("a\n  b", ReplyNormalizer.normalize("a\n  b"))
    }

    @Test
    fun `PARSER-02 granica 2000 dozwolona 2001 odrzucona`() {
        assertNotNull(ReplyNormalizer.normalize("a".repeat(2000)))
        assertNull(ReplyNormalizer.normalize("a".repeat(2001)))
        assertEquals(2000, ReplyNormalizer.MAX_LENGTH)
    }

    @Test
    fun `PARSER-02 pusta tresc`() {
        assertEquals("", ReplyNormalizer.normalize(""))
    }
}
