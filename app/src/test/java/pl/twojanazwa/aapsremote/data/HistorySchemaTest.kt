package pl.twojanazwa.aapsremote.data

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** DATA-14: wyeksportowany schemat Room i źródła platformy (katalog roboczy testów = moduł app). */
class HistorySchemaTest {

    private val schemaFile = File(
        "schemas/pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase/1.json",
    )
    private val platformDir = File("src/main/java/pl/twojanazwa/aapsremote/data/platform")

    private fun schema(): String {
        assertTrue(schemaFile.isFile, "brak pliku schematu: ${schemaFile.path}")
        return schemaFile.readText()
    }

    // nazwa kolumny -> (affinity, notNull)
    private val expectedColumns = linkedMapOf(
        "id" to ("INTEGER" to true),
        "command" to ("TEXT" to true),
        "carbs_grams" to ("INTEGER" to false),
        "outcome" to ("TEXT" to true),
        "reason" to ("TEXT" to false),
        "started_at" to ("INTEGER" to false),
        "command_sent_at" to ("INTEGER" to false),
        "code_sent_at" to ("INTEGER" to false),
        "finished_at" to ("INTEGER" to false),
        "reply_text" to ("TEXT" to false),
        "unexpected_count" to ("INTEGER" to true),
    )

    @Test
    fun `DATA-14 schemat ma wersje 1 i dokladnie jedna tabele command_history`() {
        val text = schema()
        assertTrue(Regex("\"database\"\\s*:\\s*\\{\\s*\"version\"\\s*:\\s*1\\s*,").containsMatchIn(text))
        val tables = Regex("\"tableName\"\\s*:\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(listOf("command_history"), tables)
    }

    @Test
    fun `DATA-14 kolumny maja oczekiwane typy i NOT NULL`() {
        val text = schema()
        val fieldRegex = Regex(
            "\\{\\s*\"fieldPath\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*\"columnName\"\\s*:\\s*\"([^\"]+)\"\\s*," +
                "\\s*\"affinity\"\\s*:\\s*\"([^\"]+)\"\\s*(?:,\\s*\"notNull\"\\s*:\\s*(true|false))?\\s*\\}",
        )
        val actual = fieldRegex.findAll(text).associate {
            it.groupValues[1] to (it.groupValues[2] to (it.groupValues[3] == "true"))
        }
        assertEquals(expectedColumns, actual)
        assertEquals(expectedColumns.keys.toList(), actual.keys.toList())
    }

    @Test
    fun `DATA-14 zapytanie tworzace tabele zawiera AUTOINCREMENT`() {
        val create = Regex("\"createSql\"\\s*:\\s*\"([^\"]*)\"").find(schema())?.groupValues?.get(1)
        assertTrue(create != null && create.contains("AUTOINCREMENT"))
    }

    @Test
    fun `DATA-14 zrodla data platform nie zawieraja fallbackToDestructiveMigration`() {
        val files = platformDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "nie znaleziono zrodel w ${platformDir.path}")
        val offending = files.filter { it.readText().contains("fallbackToDestructiveMigration") }
        assertFalse(offending.isNotEmpty(), "znaleziono fallbackToDestructiveMigration w: ${offending.map { it.name }}")
    }
}
