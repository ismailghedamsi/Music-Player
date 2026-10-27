package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of a saved queue: the ordered track list belonging to a [QueueState],
 * identified by the same [QueueState.queueKey]. Kept in its own table so a saved
 * queue survives the stock `queue_items` table being rewritten for the queue
 * that is currently playing.
 */
@Entity(
    tableName = "queue_state_items",
    indices = [Index(value = ["queue_key"])]
)
data class QueueStateItem(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "queue_key") var queueKey: String,
    @ColumnInfo(name = "track_id") var trackId: Long,
    @ColumnInfo(name = "track_order") var trackOrder: Int
)
