package pl.twojanazwa.aapsremote.data

/** Postać zapisana (teksty z tabeli kodów). id = 0 przed zapisem. toString() stały (DATA-17). */
data class HistoryRow(
    val id: Long,
    val command: String,
    val carbsGrams: Int?,
    val outcome: String,
    val reason: String?,
    val startedAtEpochMillis: Long?,
    val commandSentAtEpochMillis: Long?,
    val codeSentAtEpochMillis: Long?,
    val finishedAtEpochMillis: Long?,
    val replyText: String?,
    val unexpectedMessageCount: Int,
    val bolusCentiUnits: Int?,
) {
    override fun toString(): String = "HistoryRow(***)"
}
