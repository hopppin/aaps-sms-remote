package pl.twojanazwa.aapsremote.ui.history

sealed interface HistoryContent {
    data object Loading : HistoryContent
    data object Empty : HistoryContent
    data object Unavailable : HistoryContent
    data class Entries(val items: List<HistoryItem>) : HistoryContent {
        override fun toString(): String = "Entries(size=${items.size})"
    }
}

data class HistoryUiState(
    val content: HistoryContent = HistoryContent.Loading,
    val details: HistoryDetails? = null,
    /** Liczba wpisów w otwartym oknie czyszczenia; null = okno zamknięte. */
    val confirmingClearCount: Int? = null,
    val clearing: Boolean = false,
    val message: HistoryMessage? = null,
) {
    val canClear: Boolean get() = content is HistoryContent.Entries && details == null && !clearing
    val interceptsBack: Boolean get() = confirmingClearCount != null || details != null

    /** HISTUI-15: bez gramów, treści i czasów. */
    override fun toString(): String =
        "HistoryUiState(content=$content, hasDetails=${details != null}, " +
            "confirming=${confirmingClearCount != null}, clearing=$clearing, message=$message)"
}
