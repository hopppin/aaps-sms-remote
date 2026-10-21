package pl.twojanazwa.aapsremote.data.platform

import androidx.room.Database
import androidx.room.RoomDatabase

/** Baza aplikacji, wersja 1 (DATA-14). Zmiana schematu wymaga nowej specyfikacji i migracji. */
@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class AapsRemoteDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        const val FILE_NAME = "aaps_remote.db"
    }
}
