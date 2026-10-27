package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.musicplayer.models.NamedQueue
import org.fossify.musicplayer.models.NamedQueueItem

@Dao
interface NamedQueuesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQueue(queue: NamedQueue): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItems(items: List<NamedQueueItem>)

    @Query("SELECT * FROM named_queues ORDER BY updated_at DESC")
    fun getAllRecent(): List<NamedQueue>

    @Query("SELECT * FROM named_queues WHERE id = :queueId LIMIT 1")
    fun getQueue(queueId: Long): NamedQueue?

    @Query("SELECT * FROM named_queues WHERE queue_key = :queueKey LIMIT 1")
    fun getQueueByKey(queueKey: String): NamedQueue?

    @Query("SELECT track_id FROM named_queue_items WHERE queue_id = :queueId ORDER BY track_order")
    fun getTrackIds(queueId: Long): List<Long>

    @Query("UPDATE named_queues SET name = :name WHERE id = :queueId")
    fun renameQueue(queueId: Long, name: String)

    @Query(
        "UPDATE named_queues SET current_track_id = :trackId, current_index = :index, " +
            "last_position_ms = :positionMs, updated_at = :updatedAt WHERE id = :queueId"
    )
    fun updatePosition(queueId: Long, trackId: Long, index: Int, positionMs: Long, updatedAt: Long)

    @Query("DELETE FROM named_queue_items WHERE queue_id = :queueId")
    fun deleteItems(queueId: Long)

    @Query("DELETE FROM named_queues WHERE id = :queueId")
    fun deleteQueueRow(queueId: Long)

    @Query("DELETE FROM named_queues")
    fun deleteAllQueues()

    @Query("DELETE FROM named_queue_items")
    fun deleteAllItems()

    /** Replace a queue and its track list atomically. */
    @Transaction
    fun saveQueue(queue: NamedQueue, items: List<NamedQueueItem>): Long {
        deleteItems(queue.id)
        val id = insertQueue(queue)
        insertItems(items.map { it.copy(queueId = id) })
        return id
    }

    /** Remove a queue and its track list atomically. */
    @Transaction
    fun deleteQueue(queueId: Long) {
        deleteItems(queueId)
        deleteQueueRow(queueId)
    }
}
