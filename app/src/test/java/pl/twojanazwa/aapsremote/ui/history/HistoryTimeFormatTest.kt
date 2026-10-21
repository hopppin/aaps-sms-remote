package pl.twojanazwa.aapsremote.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId

class HistoryTimeFormatTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val utc = ZoneId.of("UTC")

    @Test
    fun `HISTUI-05 T0 w Europe-Warsaw i UTC`() {
        assertEquals("18.03.2005 02:57:31", HistoryTimeFormat.details(T0, warsaw))
        assertEquals("18.03.2005 02:57", HistoryTimeFormat.row(T0, warsaw))
        assertEquals("18.03.2005 01:57:31", HistoryTimeFormat.details(T0, utc))
        assertEquals("18.03.2005 01:57", HistoryTimeFormat.row(T0, utc))
    }

    @Test
    fun `HISTUI-05 czas letni w Europe-Warsaw`() {
        assertEquals("29.06.2005 01:06:40", HistoryTimeFormat.details(1120000000000L, warsaw))
    }
}
