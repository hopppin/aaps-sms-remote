package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation
import pl.twojanazwa.aapsremote.data.platform.HistoryEntity
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.UnknownReason

class HistoryCodecProfileTest {
    private val t0 = 1_000L

    private fun result(command: Command, outcome: SessionOutcome) = SessionResult(
        command = command,
        startedAtEpochMillis = t0,
        commandSentAtEpochMillis = t0 + 1,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = t0 + 2,
        outcome = outcome,
        unexpectedMessages = emptyList(),
    )

    @Test
    fun `PROF-17 wynik zmiany profilu Unknown daje wpis PROFILE_SWITCH z numerem i procentem bez nazwy`() {
        val command =
            (Command.ProfileSwitch.of(115) as ProfileSwitchValidation.Valid).command
        val row = HistoryCodec.toRow(
            result(command, SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "Profil zmieniony")),
        )
        assertEquals("PROFILE_SWITCH", row.command)
        assertEquals(1, row.profileNumber)
        assertEquals(115, row.profilePercent)
        assertEquals("Profil zmieniony", row.replyText)
        val entry = HistoryCodec.toEntry(row)
        assertEquals(HistoryCommand.PROFILE_SWITCH, entry.command)
        assertEquals(1, entry.profileNumber)
        assertEquals(115, entry.profilePercent)
        val entity = HistoryEntity.fromRow(row)
        for (text in listOf(row.toString(), entry.toString(), entity.toString())) {
            assertFalse(text.contains("LocalProfile2"))
        }
        // Żadne pole poza treścią odpowiedzi nie zawiera nazwy.
        val fields = listOf(
            row.command, row.outcome, row.reason, row.carbsGrams, row.bolusCentiUnits, row.profileNumber,
            row.profilePercent,
        )
        assertFalse(fields.any { it.toString().contains("LocalProfile2") })
    }

    @Test
    fun `PROF-17 wiersze PROFILE_STATUS i PROFILE_LIST dekoduja sie, a numer i procent znikaja`() {
        val kinds = listOf("PROFILE_STATUS" to HistoryCommand.PROFILE_STATUS, "PROFILE_LIST" to HistoryCommand.PROFILE_LIST)
        for ((code, cmd) in kinds) {
            val row = HistoryRow(1, code, null, "COMPLETED", null, t0, t0, null, t0, "x", 0, null, 3, 120)
            val entry = HistoryCodec.toEntry(row)
            assertEquals(cmd, entry.command)
            assertNull(entry.profileNumber)
            assertNull(entry.profilePercent)
        }
    }

    @Test
    fun `PROF-17 polecenia spoza zmiany profilu zapisuja brak numeru i procentu`() {
        val row = HistoryCodec.toRow(result(Command.ProfileList, SessionOutcome.Cancelled))
        assertEquals("PROFILE_LIST", row.command)
        assertNull(row.profileNumber)
        assertNull(row.profilePercent)
        val carbs = HistoryCodec.toEntry(
            HistoryRow(1, "CARBS", 20, "CANCELLED", null, t0, t0, null, t0, null, 0, null, 3, 120),
        )
        assertNull(carbs.profileNumber)
    }
}
