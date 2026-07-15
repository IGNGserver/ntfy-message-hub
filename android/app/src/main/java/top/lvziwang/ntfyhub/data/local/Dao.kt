package top.lvziwang.ntfyhub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY CAST(id AS INTEGER) DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE key = 'global' LIMIT 1")
    fun observeState(): Flow<SyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}
