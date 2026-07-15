package top.lvziwang.ntfyhub.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        fun create(context: Context): HubDatabase =
            Room.databaseBuilder(
                context,
                HubDatabase::class.java,
                "ntfy-message-hub.db"
            ).fallbackToDestructiveMigration().build()
    }
}
