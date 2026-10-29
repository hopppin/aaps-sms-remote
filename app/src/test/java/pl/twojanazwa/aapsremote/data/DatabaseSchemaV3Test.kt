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

/** PROF-17: schemat v3 (wygenerowany przez Room) i migracje 2 -> 3 oraz 1 -> 3 (katalog roboczy testów = moduł app). */
class DatabaseSchemaV3Test {

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
    fun `PROF-17 schemat 3 json ma wersje 3 kolumny wersji 2 i dwie nowe bez NOT NULL`() {
        val v3 = schema("3.json")
        assertTrue(Regex("\"database\"\\s*:\\s*\\{\\s*\"version\"\\s*:\\s*3\\s*,").containsMatchIn(v3))
        val v2Columns = columns(schema("2.json"))
        val v3Columns = columns(v3)
        assertEquals(
            v2Columns + ("profile_number" to ("INTEGER" to false)) + ("profile_percent" to ("INTEGER" to false)),
            v3Columns,
        )
        assertEquals(v2Columns.keys.toList() + "profile_number" + "profile_percent", v3Columns.keys.toList())
    }

    @Test
    fun `PROF-17 MIGRATION_2_3 wykonuje dokladnie dwa ALTER TABLE`() {
        val migration = AapsRemoteDatabase.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        migration.migrate(db)
        verify(exactly = 2) { db.execSQL(any()) }
        verify(exactly = 1) { db.execSQL("ALTER TABLE command_history ADD COLUMN profile_number INTEGER") }
        verify(exactly = 1) { db.execSQL("ALTER TABLE command_history ADD COLUMN profile_percent INTEGER") }
    }

    @Test
    fun `PROF-17 jedno wywolanie addMigrations z obiema migracjami i brak migracji destrukcyjnej`() {
        val module = File("src/main/java/pl/twojanazwa/aapsremote/data/platform/DataModule.kt").readText()
        assertEquals(1, Regex("addMigrations\\(").findAll(module).count())
        assertTrue(module.contains("addMigrations(AapsRemoteDatabase.MIGRATION_1_2, AapsRemoteDatabase.MIGRATION_2_3)"))
        val files = platformDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertFalse(files.any { it.readText().contains("fallbackToDestructiveMigration") })
    }

    @Test
    fun `PROF-17 sciezka 1 do 3 to MIGRATION_1_2 i MIGRATION_2_3 z trzema poleceniami ALTER TABLE`() {
        val migrations = listOf(AapsRemoteDatabase.MIGRATION_1_2, AapsRemoteDatabase.MIGRATION_2_3)
        var version = 1
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        for (m in migrations) {
            assertEquals(version, m.startVersion)
            m.migrate(db)
            version = m.endVersion
        }
        assertEquals(3, version)
        verify(exactly = 3) { db.execSQL(match { it.startsWith("ALTER TABLE command_history ADD COLUMN ") }) }
        verify(exactly = 3) { db.execSQL(any()) }
    }
}
