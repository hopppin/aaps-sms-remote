package pl.twojanazwa.aapsremote.data.platform

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Baza aplikacji, wersja 3 (DATA-14, BOL-17, PROF-17). Zmiana schematu wymaga nowej specyfikacji i migracji. */
@Database(entities = [HistoryEntity::class], version = 3, exportSchema = true)
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

        /** PROF-17: dwie nowe kolumny dopuszczające NULL (numer i procent profilu); nazwy profilu nie zapisujemy. */
        const val MIGRATION_2_3_SQL_NUMBER = "ALTER TABLE command_history ADD COLUMN profile_number INTEGER"
        const val MIGRATION_2_3_SQL_PERCENT = "ALTER TABLE command_history ADD COLUMN profile_percent INTEGER"

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_2_3_SQL_NUMBER)
                db.execSQL(MIGRATION_2_3_SQL_PERCENT)
            }
        }
    }
}
