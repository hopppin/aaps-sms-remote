package pl.twojanazwa.aapsremote.data.platform

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Zapytania historii (DATA-10 – DATA-12). Bez decyzji; logika w pakiecie `data`. */
@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("SELECT COUNT(*) FROM command_history")
    suspend fun count(): Int

    @Query("DELETE FROM command_history WHERE id IN (SELECT id FROM command_history ORDER BY id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("SELECT * FROM command_history ORDER BY id DESC")
    fun observeNewestFirst(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM command_history")
    suspend fun deleteAll()
}
