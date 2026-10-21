package pl.twojanazwa.aapsremote.ui.history

data class HistoryItem(
    val id: Long,
    val command: HistoryCommandLabel,
    /** Tylko dla CARBS. */
    val grams: Int?,
    val result: HistoryResultView,
    /** startedAt ?: finishedAt (HISTUI-05). */
    val listTimeEpochMillis: Long?,
) {
    /** HISTUI-15: bez gramów, wyniku i czasów. */
    override fun toString(): String = "HistoryItem(id=$id, ***)"
}

data class HistoryDetails(
    val id: Long,
    val command: HistoryCommandLabel,
    val grams: Int?,
    val result: HistoryResultView,
    /** Kolejność = HistoryTimeLabel.entries; wartość null = „brak”. */
    val times: List<Pair<HistoryTimeLabel, Long?>>,
    val replyText: String?,
    /** null, gdy liczba = 0 (HISTUI-06). */
    val unexpectedCount: Int?,
) {
    /** HISTUI-15: bez gramów, treści i czasów. */
    override fun toString(): String = "HistoryDetails(id=$id, ***)"
}
