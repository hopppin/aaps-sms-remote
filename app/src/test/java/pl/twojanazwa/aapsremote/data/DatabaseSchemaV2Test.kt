package pl.twojanazwa.aapsremote.data

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase

/** BOL-17: schemat v2 (wygenerowany przez Room) i migracja 1 -> 2 (katalog roboczy testów = moduł app). */
class DatabaseSchemaV2Test {

    private val dbDir = "schemas/pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase"
    private val platformDir = File("src/main/java/pl/twojanazwa/aapsremote/data/platform")

    private fun schema(name: String): String {
        val f = File("$dbDir/$name")
        assertTrue(f.isFile, "brak pliku schematu: ${f.path}")
        return f.readText()
    }

    private fun columns(text: String): Map<String, Pair<String, Boolean>> {
        val fieldRegex = Regex(
            "\\{\\s*\"fieldPath\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*\"columnName\"\\s*:\\s*\"([^\"]+)\"\\s*," +
                "\\s*\"affinity\"\\s*:\\s*\"([^\"]+)\"\\s*(?:,\\s*\"notNull\"\\s*:\\s*(true|false))?\\s*\\}",
        )
        return fieldRegex.findAll(text).associate {
            it.groupValues[1] to (it.groupValues[2] to (it.groupValues[3] == "true"))
        }
    }

    @Test
    fun `BOL-17 schemat 2 json ma wersje 2 kolumny wersji 1 i bolus_centi_units`() {
        val v2 = schema("2.json")
        assertTrue(Regex("\"database\"\\s*:\\s*\\{\\s*\"version\"\\s*:\\s*2\\s*,").containsMatchIn(v2))
        val v1Columns = columns(schema("1.json"))
        val v2Columns = columns(v2)
        assertEquals(v1Columns + ("bolus_centi_units" to ("INTEGER" to false)), v2Columns)
        assertEquals(v1Columns.keys.toList() + "bolus_centi_units", v2Columns.keys.toList())
    }

    @Test
    fun `BOL-17 MIGRATION_1_2 wykonuje dokladnie jeden ALTER TABLE`() {
        assertEquals(
            "ALTER TABLE command_history ADD COLUMN bolus_centi_units INTEGER",
            AapsRemoteDatabase.MIGRATION_1_2_SQL,
        )
        val migration = AapsRemoteDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        migration.migrate(db)
        verify(exactly = 1) { db.execSQL(any()) }
        verify(exactly = 1) { db.execSQL("ALTER TABLE command_history ADD COLUMN bolus_centi_units INTEGER") }
    }

    @Test
    fun `BOL-17 jedno wywolanie addMigrations z MIGRATION_1_2 i brak migracji destrukcyjnej`() {
        val module = File("src/main/java/pl/twojanazwa/aapsremote/data/platform/DataModule.kt").readText()
        assertEquals(1, Regex("addMigrations\\(").findAll(module).count())
        assertTrue(module.contains("addMigrations(AapsRemoteDatabase.MIGRATION_1_2, AapsRemoteDatabase.MIGRATION_2_3)"))
        val files = platformDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertFalse(files.any { it.readText().contains("fallbackToDestructiveMigration") })
    }
}
