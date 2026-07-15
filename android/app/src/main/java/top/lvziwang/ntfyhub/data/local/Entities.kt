package top.lvziwang.ntfyhub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val ntfyMessageId: String,
    val title: String,
    val rawTitle: String?,
    val messageText: String,
    val priority: Int?,
    val tagsJson: String,
    val clickUrl: String?,
    val ntfyTime: Long?,
    val receivedAt: String,
    val rawJson: String?,
    val topic: String,
    val sourceUser: String?
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String = "global",
    val status: String,
    val startedAtMillis: Long?,
    val finishedAtMillis: Long?,
    val durationMillis: Long?,
    val message: String?
)
