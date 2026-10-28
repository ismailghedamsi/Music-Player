package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.musicplayer.models.PlaylistState

@Dao
interface PlaylistStatesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(state: PlaylistState)

    @Query("SELECT * FROM playlist_states WHERE playlist_id = :playlistId LIMIT 1")
    fun getState(playlistId: Int): PlaylistState?

    @Query("SELECT * FROM playlist_states ORDER BY updated_at DESC")
    fun getAll(): List<PlaylistState>

    @Query("DELETE FROM playlist_states WHERE playlist_id = :playlistId")
    fun deleteState(playlistId: Int)

    @Query("DELETE FROM playlist_states WHERE playlist_id IN (:playlistIds)")
    fun deleteStates(playlistIds: List<Int>)

    @Query("DELETE FROM playlist_states")
    fun deleteAll()
}
