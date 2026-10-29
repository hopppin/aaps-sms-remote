package pl.twojanazwa.aapsremote.data.platform

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import pl.twojanazwa.aapsremote.data.HistoryRow

/** Wiersz tabeli historii (DATA-14). Pola 1:1 jak [HistoryRow]; toString() stały (DATA-17). */
@Entity(tableName = "command_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "command") val command: String,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Int?,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long?,
    @ColumnInfo(name = "command_sent_at") val commandSentAtEpochMillis: Long?,
    @ColumnInfo(name = "code_sent_at") val codeSentAtEpochMillis: Long?,
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long?,
    @ColumnInfo(name = "reply_text") val replyText: String?,
    @ColumnInfo(name = "unexpected_count") val unexpectedMessageCount: Int,
    @ColumnInfo(name = "bolus_centi_units") val bolusCentiUnits: Int?,
) {
    fun toRow(): HistoryRow = HistoryRow(
        id = id,
        command = command,
        carbsGrams = carbsGrams,
        outcome = outcome,
        reason = reason,
        startedAtEpochMillis = startedAtEpochMillis,
        commandSentAtEpochMillis = commandSentAtEpochMillis,
        codeSentAtEpochMillis = codeSentAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        replyText = replyText,
        unexpectedMessageCount = unexpectedMessageCount,
        bolusCentiUnits = bolusCentiUnits,
    )

    override fun toString(): String = "HistoryEntity(***)"

    companion object {
        fun fromRow(row: HistoryRow): HistoryEntity = HistoryEntity(
            id = row.id,
            command = row.command,
            carbsGrams = row.carbsGrams,
            outcome = row.outcome,
            reason = row.reason,
            startedAtEpochMillis = row.startedAtEpochMillis,
            commandSentAtEpochMillis = row.commandSentAtEpochMillis,
            codeSentAtEpochMillis = row.codeSentAtEpochMillis,
            finishedAtEpochMillis = row.finishedAtEpochMillis,
            replyText = row.replyText,
            unexpectedMessageCount = row.unexpectedMessageCount,
            bolusCentiUnits = row.bolusCentiUnits,
        )
    }
}
