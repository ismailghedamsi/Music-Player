package org.fossify.musicplayer.models

/**
 * One row of the Saved Queues screen: a saved [QueueState] plus the track
 * playback stopped on (if it still exists) and how many of the queue's
 * tracks are still playable. Queues whose files are all gone are hidden.
 */
data class SavedQueue(
    val state: QueueState,
    val currentTrack: Track?,
    val availableTrackCount: Int
)
