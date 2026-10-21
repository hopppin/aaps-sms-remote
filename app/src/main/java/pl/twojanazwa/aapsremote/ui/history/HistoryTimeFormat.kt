package pl.twojanazwa.aapsremote.ui.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** HISTUI-05: jawna strefa, wzorce bez zależności od języka telefonu. */
object HistoryTimeFormat {
    private val detailsFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT)
    private val rowFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)

    /** dd.MM.yyyy HH:mm:ss w podanej strefie. */
    fun details(epochMillis: Long, zone: ZoneId): String =
        detailsFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** dd.MM.yyyy HH:mm w podanej strefie. */
    fun row(epochMillis: Long, zone: ZoneId): String =
        rowFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
