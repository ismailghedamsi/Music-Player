package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved playback position for one queue.
 *
 * Stock Fossify keeps a single queue in [QueueItem] and overwrites it whenever a
 * different album/playlist is played, so only the most recently played queue can
 * ever be resumed. A [QueueState] is keyed by the exact ordered track list (see
 * [org.fossify.musicplayer.helpers.QueueResumeHelper.queueKey]), so every queue
 * remembers its own track and millisecond-exact position independently.
 */
@Entity(
    tableName = "queue_states",
    indices = [Index(value = ["queue_key"], unique = true), Index(value = ["updated_at"])]
)
data class QueueState(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "queue_key") var queueKey: String,
    @ColumnInfo(name = "label") var label: String,
    @ColumnInfo(name = "current_track_id") var currentTrackId: Long,
    @ColumnInfo(name = "current_index") var currentIndex: Int,
    /** Exact playback position in milliseconds (stock queue resume is whole seconds). */
    @ColumnInfo(name = "last_position_ms") var lastPositionMs: Long,
    @ColumnInfo(name = "track_count") var trackCount: Int,
    @ColumnInfo(name = "updated_at") var updatedAt: Long,
    @ColumnInfo(name = "shuffle_enabled") var shuffleEnabled: Boolean,
    @ColumnInfo(name = "repeat_setting") var repeatSetting: Int
)
