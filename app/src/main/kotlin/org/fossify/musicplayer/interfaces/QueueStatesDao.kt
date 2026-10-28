package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.musicplayer.models.QueueState
import org.fossify.musicplayer.models.QueueStateItem

@Dao
interface QueueStatesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertState(state: QueueState): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItems(items: List<QueueStateItem>)

    @Query("SELECT * FROM queue_states WHERE queue_key = :queueKey LIMIT 1")
    fun getState(queueKey: String): QueueState?

    @Query("SELECT track_id FROM queue_state_items WHERE queue_key = :queueKey ORDER BY track_order")
    fun getTrackIds(queueKey: String): List<Long>

    @Query("SELECT * FROM queue_states ORDER BY updated_at DESC")
    fun getAllRecent(): List<QueueState>

    @Query("SELECT queue_key FROM queue_states ORDER BY updated_at DESC LIMIT -1 OFFSET :keepCount")
    fun getKeysBeyondNewest(keepCount: Int): List<String>

    @Query("DELETE FROM queue_states WHERE queue_key = :queueKey")
    fun deleteState(queueKey: String)

    /** Update only the stopping point of a queue whose track list is already stored. */
    @Query(
        "UPDATE queue_states SET current_track_id = :trackId, current_index = :index, " +
            "last_position_ms = :positionMs, track_count = :trackCount, updated_at = :updatedAt, " +
            "shuffle_enabled = :shuffleEnabled, repeat_setting = :repeatSetting WHERE queue_key = :queueKey"
    )
    fun updatePosition(
        queueKey: String,
        trackId: Long,
        index: Int,
        positionMs: Long,
        trackCount: Int,
        updatedAt: Long,
        shuffleEnabled: Boolean,
        repeatSetting: Int
    )

    @Query("DELETE FROM queue_state_items WHERE queue_key = :queueKey")
    fun deleteItems(queueKey: String)

    @Query("DELETE FROM queue_states")
    fun deleteAllStates()

    @Query("DELETE FROM queue_state_items")
    fun deleteAllItems()

    /** Replace a queue's saved state and its track list atomically. */
    @Transaction
    fun saveState(state: QueueState, items: List<QueueStateItem>) {
        deleteItems(state.queueKey)
        insertState(state)
        insertItems(items)
    }

    /** Remove a queue's saved state and its track list atomically. */
    @Transaction
    fun deleteQueue(queueKey: String) {
        deleteItems(queueKey)
        deleteState(queueKey)
    }
}
