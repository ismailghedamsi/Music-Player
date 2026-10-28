package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Where playback stopped in one playlist, keyed by the playlist's own id.
 *
 * Keying by playlist id (not by the track list) is what makes playlist
 * resume survive re-sorting the list, adding or removing songs, and
 * playing in shuffle: the playlist is the same playlist whatever order
 * its songs are shown or played in, and the stopped-on song is found in
 * the current list by its track id.
 */
@Entity(
    tableName = "playlist_states",
    indices = [Index(value = ["updated_at"])]
)
data class PlaylistState(
    @PrimaryKey var playlistId: Int,
    @ColumnInfo(name = "current_track_id") var currentTrackId: Long,
    @ColumnInfo(name = "current_index") var currentIndex: Int,
    /** Exact playback position in milliseconds. 0 means never played. */
    @ColumnInfo(name = "last_position_ms") var lastPositionMs: Long,
    @ColumnInfo(name = "track_count") var trackCount: Int,
    @ColumnInfo(name = "updated_at") var updatedAt: Long
)
