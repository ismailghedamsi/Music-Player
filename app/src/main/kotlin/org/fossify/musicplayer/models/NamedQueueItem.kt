package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row of a named queue: the ordered track list belonging to a [NamedQueue]. */
@Entity(
    tableName = "named_queue_items",
    foreignKeys = [ForeignKey(
        entity = NamedQueue::class,
        parentColumns = ["id"],
        childColumns = ["queue_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["queue_id"])]
)
data class NamedQueueItem(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "queue_id") var queueId: Long,
    @ColumnInfo(name = "track_id") var trackId: Long,
    @ColumnInfo(name = "track_order") var trackOrder: Int
)
