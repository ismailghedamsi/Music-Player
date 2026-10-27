package org.fossify.musicplayer.helpers

import android.content.Context
import androidx.media3.common.MediaItem
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.queueStatesDAO
import org.fossify.musicplayer.models.QueueState
import org.fossify.musicplayer.models.QueueStateItem
import org.fossify.musicplayer.models.Track
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Per-queue resume: each queue remembers exactly where it stopped.
 *
 * A queue is identified by its exact ordered list of track ids, so an album
 * paused halfway through keeps its place even after other albums/playlists
 * have been played in between. Positions are stored in milliseconds and are
 * written on pause/track-change events and every [SAVE_INTERVAL_MS] while
 * playing, so killing the app mid-track does not lose the position either.
 *
 * Restore semantics (see [lookupResume] and `SimpleControllerActivity`):
 * - Tapping the first track of a queue, or the track playback stopped on,
 *   resumes that queue at its saved track and position.
 * - Tapping any other track honours the tap and starts that track from 0.
 * - A saved position under [MIN_RESUME_POSITION_MS] is ignored (a fresh
 *   start), and a position within [END_MARGIN_MS] of the track's end is
 *   treated as finished: the queue restarts from its first track.
 * - Restoring never auto-plays on a cold start: the player is prepared
 *   paused at the saved position and the user presses play.
 */
object QueueResumeHelper {

    /** How many different queues keep a saved position. Oldest are pruned. */
    const val MAX_SAVED_QUEUES = 20

    /** Positions shorter than this are a fresh start, not a resume. */
    const val MIN_RESUME_POSITION_MS = 5_000L

    /** A position this close to the end counts as "finished this track". */
    const val END_MARGIN_MS = 3_000L

    /** Minimum gap between periodic saves of the same queue while playing. */
    const val SAVE_INTERVAL_MS = 10_000L

    private val executor by lazy { Executors.newSingleThreadExecutor() }

    @Volatile
    private var lastSavedKey: String? = null

    @Volatile
    private var lastSavedPositionMs: Long = -1L

    @Volatile
    private var lastSavedAtMs: Long = 0L

    /** Stable key for a queue: SHA-256 of its ordered track ids. */
    fun queueKey(trackIds: List<Long>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(trackIds.joinToString(",").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun queueKey(mediaItems: List<MediaItem>): String =
        queueKey(mediaItems.mapNotNull { it.mediaId.toLongOrNull() })

    /** Human label for a saved queue, from the tracks themselves. */
    fun deriveLabel(tracks: List<Track>): String {
        if (tracks.isEmpty()) return ""
        val album = tracks.first().album
        return if (album.isNotEmpty() && tracks.all { it.album == album }) {
            val artist = tracks.first().artist
            if (artist.isNotEmpty()) "$artist — $album" else album
        } else {
            tracks.first().let { "${it.artist} — ${it.title}" }
        }
    }

    /**
     * Save [current] track + [positionMs] for this exact queue. Cheap no-op
     * when nothing meaningful changed and [force] is false; pass [force]=true
     * on pause/track-change so the exact stopping point is always recorded.
     */
    fun saveQueueState(
        context: Context,
        mediaItems: List<MediaItem>,
        current: MediaItem,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatSettingOrdinal: Int,
        label: String = "",
        force: Boolean = false
    ) {
        if (!context.config.rememberQueuePositions || mediaItems.isEmpty()) return
        val trackIds = mediaItems.mapNotNull { it.mediaId.toLongOrNull() }
        if (trackIds.isEmpty()) return
        val currentId = current.mediaId.toLongOrNull() ?: return
        val currentIndex = trackIds.indexOf(currentId).takeIf { it >= 0 } ?: return
        val key = queueKey(trackIds)
        val now = System.currentTimeMillis()
        if (!force && key == lastSavedKey && kotlin.math.abs(positionMs - lastSavedPositionMs) < SAVE_INTERVAL_MS &&
            now - lastSavedAtMs < SAVE_INTERVAL_MS
        ) {
            return
        }
        lastSavedKey = key
        lastSavedPositionMs = positionMs
        lastSavedAtMs = now

        executor.execute {
            try {
                val dao = context.queueStatesDAO
                val state = QueueState(
                    queueKey = key,
                    label = label,
                    currentTrackId = currentId,
                    currentIndex = currentIndex,
                    lastPositionMs = positionMs,
                    trackCount = trackIds.size,
                    updatedAt = now,
                    shuffleEnabled = shuffleEnabled,
                    repeatSetting = repeatSettingOrdinal
                )
                val items = trackIds.mapIndexed { index, trackId ->
                    QueueStateItem(queueKey = key, trackId = trackId, trackOrder = index)
                }
                dao.saveState(state, items)
                dao.getKeysBeyondNewest(MAX_SAVED_QUEUES).forEach { dao.deleteQueue(it) }
            } catch (ignored: Exception) {
            }
        }
    }

    /** Saved state for this exact track list, or null when there is none. Runs on the calling thread: call from a background thread. */
    fun getSavedState(context: Context, trackIds: List<Long>): QueueState? {
        if (!context.config.rememberQueuePositions || trackIds.isEmpty()) return null
        return try {
            context.queueStatesDAO.getState(queueKey(trackIds))
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Where playback of [tracks] should resume, or null to start fresh.
     * Returns (startIndex, startPositionMs). The saved track must still be in
     * the queue and the position must be a real mid-track stop.
     */
    fun lookupResume(context: Context, tracks: List<Track>, tappedIndex: Int): Pair<Int, Long>? {
        if (tracks.isEmpty()) return null
        val trackIds = tracks.map { it.mediaStoreId }
        val state = getSavedState(context, trackIds) ?: return null
        // Only resume when the user tapped the start of the queue or the very
        // track playback stopped on; any other tap is an explicit fresh start.
        if (tappedIndex != 0 && tappedIndex != state.currentIndex) return null
        val savedIndex = trackIds.indexOf(state.currentTrackId).takeIf { it >= 0 } ?: return null
        val savedTrack = tracks[savedIndex]
        val durationMs = savedTrack.duration * 1000L
        if (state.lastPositionMs < MIN_RESUME_POSITION_MS) return null
        if (durationMs > 0 && state.lastPositionMs >= durationMs - END_MARGIN_MS) {
            // Finished the last track of the queue: restart the queue fresh.
            if (savedIndex == tracks.lastIndex) return null
            return Pair(savedIndex + 1, 0L)
        }
        if (durationMs > 0 && state.lastPositionMs >= durationMs) return null
        return Pair(savedIndex, state.lastPositionMs)
    }

    /** Forget every saved queue position (used when the feature is toggled off). */
    fun clearAll(context: Context) {
        executor.execute {
            try {
                context.queueStatesDAO.deleteAllItems()
                context.queueStatesDAO.deleteAllStates()
            } catch (ignored: Exception) {
            }
        }
    }
}
