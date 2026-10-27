package org.fossify.musicplayer.helpers

/** A track list's saved stopping point, for highlighting and scrolling to it. */
data class ResumeMarker(
    /** Index of the track playback stopped on, within the displayed list. */
    val index: Int,
    /** MediaStore id of the track playback stopped on. */
    val trackId: Long,
    /** Saved playback position in milliseconds. */
    val positionMs: Long
)
