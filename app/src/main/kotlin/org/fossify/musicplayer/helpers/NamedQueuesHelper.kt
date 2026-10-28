package org.fossify.musicplayer.helpers

import android.content.Context
import androidx.media3.common.MediaItem
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.namedQueuesDAO
import org.fossify.musicplayer.models.NamedQueue
import org.fossify.musicplayer.models.NamedQueueItem
import org.fossify.musicplayer.models.NamedQueueRow
import org.fossify.musicplayer.models.Track
import java.util.concurrent.Executors

/**
 * Named queues (fork): queues the user keeps, like playlists.
 *
 * Queue 1 can hold 20 songs and Queue 2 can hold 30, and each remembers
 * exactly where playback stopped in it. Playing song 10 in Queue 1, then
 * song 3 in Queue 2, then switching back to Queue 1 shows song 10
 * highlighted, ready to continue from its saved position — because each
 * queue is a stored row of its own, not a position derived from whatever
 * list happened to be played last.
 *
 * A named queue is identified by the hash of its ordered track list (see
 * [QueueResumeHelper.queueKey]), the same key the automatic per-list
 * positions use. That is what lets playback keep a named queue's position
 * up to date: when the playing list matches a named queue's track list,
 * [syncPositionFromPlayback] updates that queue's stored position.
 */
object NamedQueuesHelper {

    private val executor by lazy { Executors.newSingleThreadExecutor() }

    @Volatile
    private var lastSyncedKey: String? = null

    @Volatile
    private var lastSyncedPositionMs: Long = -1L

    @Volatile
    private var lastSyncedAtMs: Long = 0L

    /** Default name for a new queue: "Queue 1", "Queue 2", and so on. Runs on the calling thread. */
    fun nextDefaultName(context: Context): String {
        return try {
            "Queue ${context.namedQueuesDAO.getAllRecent().size + 1}"
        } catch (ignored: Exception) {
            "Queue 1"
        }
    }

    /**
     * Create a named queue from [tracks], or rename and return the existing
     * queue when one with the exact same track list already exists. Any
     * automatic saved position for this track list is carried over, so
     * saving a list that is currently paused mid-track keeps its place.
     * Returns the queue id, or -1 on failure. Runs on the calling thread:
     * call from a background thread.
     */
    fun createQueue(context: Context, name: String, tracks: List<Track>): Long {
        if (tracks.isEmpty()) return -1L
        return try {
            val trackIds = tracks.map { it.mediaStoreId }
            val key = QueueResumeHelper.queueKey(trackIds)
            val dao = context.namedQueuesDAO
            val now = System.currentTimeMillis()
            val existing = dao.getQueueByKey(key)
            val saved = QueueResumeHelper.getSavedState(context, trackIds)
            if (existing != null) {
                dao.renameQueue(existing.id, name)
                return existing.id
            }
            val queue = NamedQueue(
                name = name,
                queueKey = key,
                currentTrackId = saved?.currentTrackId ?: trackIds.first(),
                currentIndex = saved?.currentIndex ?: 0,
                lastPositionMs = saved?.lastPositionMs ?: 0L,
                trackCount = trackIds.size,
                updatedAt = now
            )
            val items = trackIds.mapIndexed { index, trackId ->
                NamedQueueItem(queueId = 0, trackId = trackId, trackOrder = index)
            }
            dao.saveQueue(queue, items)
        } catch (ignored: Exception) {
            -1L
        }
    }

    /** All named queues, newest first, with their stopped-on track resolved. Runs on the calling thread. */
    fun listQueues(context: Context): List<NamedQueueRow> {
        return try {
            context.namedQueuesDAO.getAllRecent().mapNotNull { queue ->
                val tracks = loadTracks(context, queue.id)
                if (tracks.isEmpty()) {
                    null
                } else {
                    val currentTrack = tracks.getOrNull(queue.currentIndex)
                        ?: tracks.firstOrNull { it.mediaStoreId == queue.currentTrackId }
                    NamedQueueRow(queue, currentTrack, tracks.size)
                }
            }
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    /** One named queue by id, or null. Runs on the calling thread. */
    fun getQueue(context: Context, queueId: Long): NamedQueue? {
        return try {
            context.namedQueuesDAO.getQueue(queueId)
        } catch (ignored: Exception) {
            null
        }
    }

    /** A named queue's tracks in queue order, skipping files that are gone. Runs on the calling thread. */
    fun loadTracks(context: Context, queueId: Long): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        try {
            val audioHelper = context.audioHelper
            context.namedQueuesDAO.getTrackIds(queueId).forEach { trackId ->
                audioHelper.getTrack(trackId)?.let { tracks.add(it) }
            }
        } catch (ignored: Exception) {
        }
        return tracks
    }

    /**
     * Where [tracks] (a named queue's list) stopped, for highlighting the
     * stopped-on track and scrolling straight to it. Null when the queue
     * was never played, stopped under 5 seconds in, or the saved track
     * finished. Runs on the calling thread.
     */
    fun resumeMarker(queue: NamedQueue, tracks: List<Track>): ResumeMarker? {
        if (tracks.isEmpty()) return null
        val index = tracks.indexOfFirst { it.mediaStoreId == queue.currentTrackId }
        if (index < 0) return null
        val durationMs = tracks[index].duration * 1000L
        if (queue.lastPositionMs < QueueResumeHelper.MIN_RESUME_POSITION_MS) return null
        if (durationMs > 0 && queue.lastPositionMs >= durationMs - QueueResumeHelper.END_MARGIN_MS) return null
        return ResumeMarker(index, queue.currentTrackId, queue.lastPositionMs)
    }

    /** Rename a named queue. Runs on the calling thread. */
    fun renameQueue(context: Context, queueId: Long, name: String) {
        try {
            context.namedQueuesDAO.renameQueue(queueId, name)
        } catch (ignored: Exception) {
        }
    }

    /** Delete a named queue. Its tracks are untouched. Runs on the calling thread. */
    fun deleteQueue(context: Context, queueId: Long) {
        try {
            context.namedQueuesDAO.deleteQueue(queueId)
        } catch (ignored: Exception) {
        }
    }

    /** Delete every named queue. Their tracks are untouched. Runs on the calling thread. */
    fun deleteAllQueues(context: Context) {
        try {
            context.namedQueuesDAO.deleteAllItems()
            context.namedQueuesDAO.deleteAllQueues()
        } catch (ignored: Exception) {
        }
    }

    /**
     * Forget every named queue's saved position (used when remembering
     * positions is toggled off). The queues and their track lists stay.
     * Runs on a background executor.
     */
    fun clearAllPositions(context: Context) {
        executor.execute {
            try {
                val dao = context.namedQueuesDAO
                val now = System.currentTimeMillis()
                dao.getAllRecent().forEach { queue ->
                    val firstId = dao.getTrackIds(queue.id).firstOrNull() ?: return@forEach
                    dao.updatePosition(queue.id, firstId, 0, 0L, now)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Keep a named queue's stored position in step with playback: when the
     * list now playing is exactly a named queue's track list, that queue's
     * row is updated to the current track and millisecond position.
     * Cheap no-op when nothing meaningful changed and [force] is false;
     * pass [force]=true on pause/track-change so the exact stopping point
     * is always recorded. Runs on the player's thread; the write itself is
     * handed to a background executor.
     */
    fun syncPositionFromPlayback(
        context: Context,
        mediaItems: List<MediaItem>,
        current: MediaItem,
        positionMs: Long,
        force: Boolean = false
    ) {
        if (!context.config.rememberQueuePositions || mediaItems.isEmpty()) return
        val trackIds = mediaItems.mapNotNull { it.mediaId.toLongOrNull() }
        if (trackIds.isEmpty()) return
        val currentId = current.mediaId.toLongOrNull() ?: return
        val currentIndex = trackIds.indexOf(currentId).takeIf { it >= 0 } ?: return
        val key = QueueResumeHelper.queueKeyCached(trackIds)
        val now = System.currentTimeMillis()
        if (!force && key == lastSyncedKey && kotlin.math.abs(positionMs - lastSyncedPositionMs) < QueueResumeHelper.SAVE_INTERVAL_MS &&
            now - lastSyncedAtMs < QueueResumeHelper.SAVE_INTERVAL_MS
        ) {
            return
        }
        lastSyncedKey = key
        lastSyncedPositionMs = positionMs
        lastSyncedAtMs = now

        executor.execute {
            try {
                val queue = context.namedQueuesDAO.getQueueByKey(key) ?: return@execute
                context.namedQueuesDAO.updatePosition(queue.id, currentId, currentIndex, positionMs, now)
            } catch (ignored: Exception) {
            }
        }
    }
}
