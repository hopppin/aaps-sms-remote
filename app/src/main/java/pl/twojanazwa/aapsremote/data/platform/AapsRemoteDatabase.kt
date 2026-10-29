package pl.twojanazwa.aapsremote.data.platform

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Baza aplikacji, wersja 2 (DATA-14, BOL-17). Zmiana schematu wymaga nowej specyfikacji i migracji. */
@Database(entities = [HistoryEntity::class], version = 2, exportSchema = true)
abstract class AapsRemoteDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        const val FILE_NAME = "aaps_remote.db"

        /** BOL-17: jedyna zmiana to nowa kolumna dopuszczająca NULL; istniejące wiersze bez zmian. */
        const val MIGRATION_1_2_SQL = "ALTER TABLE command_history ADD COLUMN bolus_centi_units INTEGER"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_1_2_SQL)
            }
        }
    }
}
