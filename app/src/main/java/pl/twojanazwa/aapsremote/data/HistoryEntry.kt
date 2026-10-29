package pl.twojanazwa.aapsremote.data

/** DATA-07. toString() stały (DATA-17). */
data class HistoryEntry(
    val id: Long,
    val command: HistoryCommand,
    val carbsGrams: Int?,
    val outcome: HistoryOutcome,
    val startedAtEpochMillis: Long?,
    val commandSentAtEpochMillis: Long?,
    val codeSentAtEpochMillis: Long?,
    val finishedAtEpochMillis: Long?,
    val replyText: String?,
    val unexpectedMessageCount: Int,
    val bolusCentiUnits: Int?,
    val profileNumber: Int?,
    val profilePercent: Int?,
) {
    override fun toString(): String = "HistoryEntry(***)"
}
