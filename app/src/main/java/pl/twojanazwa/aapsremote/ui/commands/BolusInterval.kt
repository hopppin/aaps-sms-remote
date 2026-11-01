package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.session.UnknownReason

internal object BolusInterval {
    private const val MINUTE_MILLIS = 60_000L

    /**
     * BOL-12. null = można; inaczej liczba minut do końca odstępu (>= 1). Liczą się wpisy BOLUS z wysłanym kodem
     * albo wynikiem BolusConfirmed/Unknown; chwila odniesienia = wysłanie kodu, zakończenie, rozpoczęcie.
     * Wyjątek: wynik Unknown(BOLUS_FAILED) (AAPS potwierdził błąd bolusa) się nie liczy.
     * Chwila w przyszłości (zegar cofnięty) = pełny odstęp.
     */
    fun minutesLeft(entries: List<HistoryEntry>, nowEpochMillis: Long, intervalMinutes: Int): Int? {
        val intervalMillis = intervalMinutes * MINUTE_MILLIS
        var left: Int? = null
        for (entry in entries) {
            if (entry.command != HistoryCommand.BOLUS) continue
            val counts = entry.outcome != HistoryOutcome.Unknown(UnknownReason.BOLUS_FAILED) &&
                (entry.codeSentAtEpochMillis != null ||
                    entry.outcome is HistoryOutcome.BolusConfirmed || entry.outcome is HistoryOutcome.Unknown)
            if (!counts) continue
            val t = entry.codeSentAtEpochMillis ?: entry.finishedAtEpochMillis ?: entry.startedAtEpochMillis ?: continue
            val entryLeft = when {
                t > nowEpochMillis -> intervalMinutes
                nowEpochMillis - t < intervalMillis ->
                    (((intervalMillis - (nowEpochMillis - t)) + MINUTE_MILLIS - 1) / MINUTE_MILLIS).toInt()
                else -> continue
            }
            left = maxOf(left ?: 0, entryLeft)
        }
        return left
    }
}
