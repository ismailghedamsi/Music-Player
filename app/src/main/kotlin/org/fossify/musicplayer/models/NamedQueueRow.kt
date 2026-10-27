package org.fossify.musicplayer.models

/**
 * One row of the Queues screen: a [NamedQueue] plus the track playback
 * stopped on (if it still exists) and how many of the queue's tracks are
 * still playable. Queues whose files are all gone are hidden.
 */
data class NamedQueueRow(
    val queue: NamedQueue,
    val currentTrack: Track?,
    val availableTrackCount: Int
)
