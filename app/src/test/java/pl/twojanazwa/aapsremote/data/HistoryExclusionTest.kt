package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.session.IncomingSms
import pl.twojanazwa.aapsremote.session.SessionResult
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

class HistoryExclusionTest {

    private val entryFields = setOf(
        "id", "command", "carbsGrams", "outcome", "startedAtEpochMillis",
        "commandSentAtEpochMillis", "codeSentAtEpochMillis", "finishedAtEpochMillis",
        "replyText", "unexpectedMessageCount", "bolusCentiUnits", "profileNumber", "profilePercent",
    )

    private val forbiddenTypes: List<Class<*>> = listOf(
        java.util.List::class.java, java.util.Collection::class.java, java.lang.Iterable::class.java,
        CharArray::class.java, ByteArray::class.java, AapsReply::class.java,
        Command::class.java, IncomingSms::class.java, SessionResult::class.java,
    )

    private fun fields(type: KClass<*>) =
        type.java.declaredFields.filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }

    private fun assertNoForbiddenTypes(type: KClass<*>) {
        for (field in fields(type)) {
            assertFalse(field.type.isArray, "pole tablicowe ${field.name}")
            for (forbidden in forbiddenTypes) {
                assertFalse(forbidden.isAssignableFrom(field.type), "zabroniony typ pola ${field.name}")
            }
        }
    }

    @Test
    fun `DATA-08 pola wpisu to zamknieta lista`() {
        assertEquals(entryFields, fields(HistoryEntry::class).map { it.name }.toSet())
        assertEquals(entryFields.size, fields(HistoryEntry::class).size)
    }

    @Test
    fun `DATA-08 pola wiersza to pola wpisu i reason`() {
        assertEquals(entryFields + "reason", fields(HistoryRow::class).map { it.name }.toSet())
        assertEquals(entryFields.size + 1, fields(HistoryRow::class).size)
    }

    @Test
    fun `DATA-08 wpis nie ma pol zabronionych typow`() {
        assertNoForbiddenTypes(HistoryEntry::class)
    }

    @Test
    fun `DATA-08 wiersz nie ma pol zabronionych typow`() {
        assertNoForbiddenTypes(HistoryRow::class)
    }

    @Test
    fun `DATA-08 wiersz nie zawiera tresci wiadomosci nieoczekiwanych`() {
        val result = HistoryFixtures.carbsConfirmed(
            listOf(
                HistoryFixtures.sms("Pomyslnie wprowadzono 7 g weglowodanow"),
                HistoryFixtures.sms("Inny tekst 4321"),
            ),
        )
        val row = HistoryCodec.toRow(result)
        val texts = listOfNotNull(row.command, row.outcome, row.reason, row.replyText)
        for (t in texts) {
            assertFalse(t.contains("wprowadzono 7 g"), "tresc wiadomosci nieoczekiwanej")
            assertFalse(t.contains("Inny tekst"), "tresc wiadomosci nieoczekiwanej")
        }
        assertEquals(2, row.unexpectedMessageCount)
    }

    @Test
    fun `DATA-08 wpis nie zawiera tresci wiadomosci nieoczekiwanych`() {
        val result = HistoryFixtures.carbsConfirmed(
            listOf(
                HistoryFixtures.sms("Pomyslnie wprowadzono 7 g weglowodanow"),
                HistoryFixtures.sms("Inny tekst 4321"),
            ),
        )
        val entry = HistoryCodec.toEntry(HistoryCodec.toRow(result))
        val texts = listOfNotNull(entry.command.toString(), entry.outcome.toString(), entry.replyText, entry.toString())
        for (t in texts) {
            assertFalse(t.contains("wprowadzono 7 g"), "tresc wiadomosci nieoczekiwanej")
            assertFalse(t.contains("Inny tekst"), "tresc wiadomosci nieoczekiwanej")
        }
        assertEquals(2, entry.unexpectedMessageCount)
    }
}
