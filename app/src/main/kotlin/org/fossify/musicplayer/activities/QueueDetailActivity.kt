package org.fossify.musicplayer.activities

import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.TracksAdapter
import org.fossify.musicplayer.databinding.ActivityQueueDetailBinding
import org.fossify.musicplayer.dialogs.NewQueueDialog
import org.fossify.musicplayer.helpers.NamedQueuesHelper
import org.fossify.musicplayer.helpers.ResumeMarker
import org.fossify.musicplayer.models.NamedQueue
import org.fossify.musicplayer.models.Track

/**
 * One named queue's tracks (fork): the song playback stopped on is
 * highlighted in the accent colour with its saved position, and the list
 * opens scrolled straight to it. Tap the highlighted song to continue from
 * its saved position; tap any other song to start that song from the
 * beginning. Switching to another queue and back keeps each queue's place,
 * because each queue stores its own position.
 */
class QueueDetailActivity : SimpleControllerActivity() {

    companion object {
        const val EXTRA_QUEUE_ID = "queue_id"
    }

    private val binding by viewBinding(ActivityQueueDetailBinding::inflate)

    private var queueId: Long = -1L
    private var queue: NamedQueue? = null
    private var tracks = ArrayList<Track>()
    private var resumeMarker: ResumeMarker? = null
    private var hasScrolledToMarker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1L)
        if (queueId == -1L) {
            finish()
            return
        }

        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.queueDetailList))
        setupMaterialScrollListener(binding.queueDetailList, binding.queueDetailAppbar)

        binding.queueDetailToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.rename_queue -> askRename()
                R.id.delete_queue -> askDelete()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        binding.queueDetailFastscroller.updateColors(getProperPrimaryColor())
        loadQueue()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.queueDetailAppbar, NavigationIcon.Arrow)
        // Refresh the highlight: the position may have moved while this
        // queue played from somewhere else. Never re-scrolls after the
        // first open.
        if (queue != null) {
            loadQueue()
        }
    }

    private fun getAdapter() = binding.queueDetailList.adapter as? TracksAdapter

    private fun loadQueue() {
        ensureBackgroundThread {
            val loadedQueue = NamedQueuesHelper.getQueue(this, queueId)
            if (loadedQueue == null) {
                runOnUiThread { finish() }
                return@ensureBackgroundThread
            }
            val loadedTracks = NamedQueuesHelper.loadTracks(this, queueId)
            val marker = NamedQueuesHelper.resumeMarker(loadedQueue, loadedTracks)

            runOnUiThread {
                queue = loadedQueue
                tracks = loadedTracks
                resumeMarker = marker
                binding.queueDetailToolbar.title = loadedQueue.name
                binding.queueDetailPlaceholder.beVisibleIf(loadedTracks.isEmpty())

                val currAdapter = getAdapter()
                if (currAdapter == null) {
                    TracksAdapter(
                        activity = this,
                        recyclerView = binding.queueDetailList,
                        sourceType = TracksAdapter.TYPE_TRACKS,
                        items = loadedTracks
                    ) {
                        trackClicked(it as Track)
                    }.apply {
                        setResumeMarker(marker)
                        binding.queueDetailList.adapter = this
                    }

                    if (areSystemAnimationsEnabled) {
                        binding.queueDetailList.scheduleLayoutAnimation()
                    }

                    if (marker != null && !hasScrolledToMarker) {
                        hasScrolledToMarker = true
                        binding.queueDetailList.post {
                            binding.queueDetailList.scrollToPosition(marker.index)
                        }
                    }
                } else {
                    currAdapter.updateItems(loadedTracks)
                    currAdapter.setResumeMarker(marker)
                }
            }
        }
    }

    private fun trackClicked(track: Track) {
        val index = tracks.indexOf(track)
        if (index < 0) return
        val marker = resumeMarker
        if (marker != null && track.mediaStoreId == marker.trackId) {
            // The highlighted song: continue from its saved position.
            prepareAndPlay(tracks, index, marker.positionMs)
        } else {
            // Any other song: start that song from the beginning.
            prepareAndPlay(tracks, index, forceFresh = true)
        }
    }

    private fun askRename() {
        val current = queue ?: return
        NewQueueDialog(this, current.name) { name ->
            ensureBackgroundThread {
                NamedQueuesHelper.renameQueue(this, queueId, name)
                runOnUiThread {
                    queue?.name = name
                    binding.queueDetailToolbar.title = name
                }
            }
        }
    }

    private fun askDelete() {
        ConfirmationDialog(
            this, "", R.string.delete_queue_warning,
            org.fossify.commons.R.string.ok, org.fossify.commons.R.string.cancel
        ) {
            ensureBackgroundThread {
                NamedQueuesHelper.deleteQueue(this, queueId)
                runOnUiThread {
                    toast(R.string.queue_deleted)
                    finish()
                }
            }
        }
    }
}
