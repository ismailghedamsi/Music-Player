package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named queue, like a playlist: a fixed list of tracks the user keeps,
 * each remembering exactly where playback stopped in it.
 *
 * A named queue keeps its identity when the user switches to another queue
 * and back: the row (and its [queueKey], the hash of its ordered track
 * list) stays put, so switching queues never loses a stopping point. This
 * is separate from the automatic per-list positions in [QueueState], which
 * only exist for lists that were actually played and paused.
 */
@Entity(
    tableName = "named_queues",
    indices = [Index(value = ["queue_key"], unique = true), Index(value = ["updated_at"])]
)
data class NamedQueue(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "queue_key") var queueKey: String,
    @ColumnInfo(name = "current_track_id") var currentTrackId: Long,
    @ColumnInfo(name = "current_index") var currentIndex: Int,
    /** Exact playback position in milliseconds. 0 means never played. */
    @ColumnInfo(name = "last_position_ms") var lastPositionMs: Long,
    @ColumnInfo(name = "track_count") var trackCount: Int,
    @ColumnInfo(name = "updated_at") var updatedAt: Long
)
