package org.fossify.musicplayer.helpers

import android.content.Context
import androidx.media3.common.MediaItem
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.playlistStatesDAO
import org.fossify.musicplayer.models.PlaylistState
import org.fossify.musicplayer.models.Track
import java.util.concurrent.Executors

/**
 * Per-playlist resume (fork): every playlist remembers the song playback
 * stopped on in it, and the exact position in that song, so any playlist
 * can be resumed from where it was left.
 *
 * The state is keyed by playlist id, so it survives re-sorting the list,
 * adding or removing songs, and shuffle: the stopped-on song is found in
 * the playlist's current track list by its track id. Restore semantics
 * match the per-list resume: tapping the first song of the playlist, or
 * the song playback stopped on, resumes at the saved song and position;
 * tapping any other song starts that song from 0; a position within 3
 * seconds of the song's end counts as finished. A song played only
 * briefly is still remembered: it resumes from its start.
 */
object PlaylistResumeHelper {

    private val executor by lazy { Executors.newSingleThreadExecutor() }

    @Volatile
    private var lastSyncedPlaylistId: Int = -1

    @Volatile
    private var lastSyncedPositionMs: Long = -1L

    @Volatile
    private var lastSyncedAtMs: Long = 0L

    /**
     * The playlist the player was last started from, or -1. Set by
     * `prepareAndPlay` when playback starts from a playlist's track list.
     * This is needed because the player prepares tracks through the fast
     * media-item path, which carries no metadata extras: the playing
     * items alone cannot say which playlist they came from.
     */
    @Volatile
    var activePlaylistId: Int = -1
        private set

    fun setActivePlaylist(playlistId: Int) {
        activePlaylistId = playlistId
    }

    /** Saved state for one playlist, or null when it was never played. Runs on the calling thread. */
    fun getState(context: Context, playlistId: Int): PlaylistState? {
        if (playlistId <= 0) return null
        return try {
            context.playlistStatesDAO.getState(playlistId)
        } catch (ignored: Exception) {
            null
        }
    }

    /** All saved playlist states, newest first. Runs on the calling thread. */
    fun getAllStates(context: Context): List<PlaylistState> {
        return try {
            context.playlistStatesDAO.getAll()
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    /**
     * Where playback of [tracks] (one playlist's songs) should resume, or
     * null to start fresh. Returns (startIndex, startPositionMs).
     * Runs on the calling thread.
     */
    fun lookupResume(context: Context, playlistId: Int, tracks: List<Track>, tappedIndex: Int): Pair<Int, Long>? {
        if (tracks.isEmpty()) return null
        val state = getState(context, playlistId) ?: return null
        // Only resume when the user tapped the start of the playlist or
        // the very song playback stopped on; any other tap is a fresh start.
        val savedIndex = tracks.indexOfFirst { it.mediaStoreId == state.currentTrackId }.takeIf { it >= 0 } ?: return null
        if (tappedIndex != 0 && tappedIndex != savedIndex) return null
        val savedTrack = tracks[savedIndex]
        val durationMs = savedTrack.duration * 1000L
        // A brief play still counts: the song is remembered even when it
        // played for under 5 seconds, and resumes from its start.
        if (state.lastPositionMs < QueueResumeHelper.MIN_RESUME_POSITION_MS) return Pair(savedIndex, 0L)
        if (durationMs > 0 && state.lastPositionMs >= durationMs - QueueResumeHelper.END_MARGIN_MS) {
            // Finished the last song of the playlist: restart fresh.
            if (savedIndex == tracks.lastIndex) return null
            return Pair(savedIndex + 1, 0L)
        }
        if (durationMs > 0 && state.lastPositionMs >= durationMs) return null
        return Pair(savedIndex, state.lastPositionMs)
    }

    /**
     * Where a playlist's saved stopping point is, for highlighting it in
     * the list and scrolling straight to it. Null when the playlist has no
     * real mid-song stopping point. Runs on the calling thread.
     */
    fun resumeMarker(context: Context, playlistId: Int, tracks: List<Track>): ResumeMarker? {
        if (tracks.isEmpty()) return null
        val state = getState(context, playlistId) ?: return null
        val index = tracks.indexOfFirst { it.mediaStoreId == state.currentTrackId }
        if (index < 0) return null
        val durationMs = tracks[index].duration * 1000L
        // A brief play still counts: the song is highlighted even when it
        // played for under 5 seconds.
        if (durationMs > 0 && state.lastPositionMs >= durationMs - QueueResumeHelper.END_MARGIN_MS) return null
        return ResumeMarker(index, state.currentTrackId, state.lastPositionMs)
    }

    /**
     * One-line "last played" text for a playlist's row in the playlists
     * list ("Artist — Title • resumes at M:SS"), or null when the playlist
     * has no real mid-song stopping point or the song is gone.
     * Runs on the calling thread.
     */
    fun lastPlayedText(context: Context, playlistId: Int): String? {
        val state = getState(context, playlistId) ?: return null
        val track = try {
            context.audioHelper.getTrack(state.currentTrackId)
        } catch (ignored: Exception) {
            null
        } ?: return null
        val durationMs = track.duration * 1000L
        if (durationMs > 0 && state.lastPositionMs >= durationMs - QueueResumeHelper.END_MARGIN_MS) return null
        val title = if (track.artist.isNotBlank()) "${track.artist} — ${track.title}" else track.title
        // A brief play still counts: the song is shown even when it played
        // for under 5 seconds, without a "resumes at" time.
        if (state.lastPositionMs < QueueResumeHelper.MIN_RESUME_POSITION_MS) return title
        val positionText = (state.lastPositionMs / 1000).toInt().getFormattedDuration()
        return "$title • ${context.getString(R.string.saved_queue_resume_at, positionText)}"
    }

    /**
     * Keep a playlist's stored stopping point in step with playback: the
     * playlist is the one playback was started from (see
     * [activePlaylistId]). The "All tracks" pseudo-playlist (id 1) and
     * album/folder lists never write a playlist state. Cheap no-op when
     * nothing meaningful changed and [force] is false; pass [force]=true
     * on pause/track-change so the exact stopping point is always recorded.
     * Runs on the player's thread; the write itself is handed to a
     * background executor.
     */
    fun syncFromPlayback(
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
        val playlistId = activePlaylistId
        if (playlistId <= 0 || playlistId == ALL_TRACKS_PLAYLIST_ID) return
        val now = System.currentTimeMillis()
        if (!force && playlistId == lastSyncedPlaylistId && kotlin.math.abs(positionMs - lastSyncedPositionMs) < QueueResumeHelper.SAVE_INTERVAL_MS &&
            now - lastSyncedAtMs < QueueResumeHelper.SAVE_INTERVAL_MS
        ) {
            return
        }
        lastSyncedPlaylistId = playlistId
        lastSyncedPositionMs = positionMs
        lastSyncedAtMs = now

        executor.execute {
            try {
                context.playlistStatesDAO.upsert(
                    PlaylistState(
                        playlistId = playlistId,
                        currentTrackId = currentId,
                        currentIndex = currentIndex,
                        lastPositionMs = positionMs,
                        trackCount = trackIds.size,
                        updatedAt = now
                    )
                )
            } catch (ignored: Exception) {
            }
        }
    }

    /** Forget one playlist's stopping point (used when the playlist is deleted). Runs on a background executor. */
    fun deleteStates(context: Context, playlistIds: List<Int>) {
        if (playlistIds.isEmpty()) return
        executor.execute {
            try {
                context.playlistStatesDAO.deleteStates(playlistIds)
            } catch (ignored: Exception) {
            }
        }
    }

    /** Forget every playlist's stopping point (used when remembering positions is toggled off). Runs on a background executor. */
    fun clearAll(context: Context) {
        executor.execute {
            try {
                context.playlistStatesDAO.deleteAll()
            } catch (ignored: Exception) {
            }
        }
    }
}
