package org.fossify.musicplayer.activities

import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.SavedQueuesAdapter
import org.fossify.musicplayer.databinding.ActivitySavedQueuesBinding
import org.fossify.musicplayer.helpers.QueueResumeHelper
import org.fossify.musicplayer.models.SavedQueue

/**
 * Saved Queues (fork): every queue that has a saved stopping point, newest
 * first. Tap a queue to resume it at its exact saved track and position;
 * long-press a queue to forget its saved position (the music is untouched).
 */
class SavedQueuesActivity : SimpleControllerActivity() {

    private val binding by viewBinding(ActivitySavedQueuesBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.savedQueuesList))
        setupMaterialScrollListener(binding.savedQueuesList, binding.savedQueuesAppbar)

        binding.savedQueuesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.forget_all_saved_queues -> askForgetAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        binding.savedQueuesList.adapter = SavedQueuesAdapter(
            activity = this,
            items = arrayListOf(),
            itemClick = { savedQueue -> resumeQueue(savedQueue) },
            itemLongClick = { savedQueue -> askForgetOne(savedQueue) }
        )
        binding.savedQueuesFastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.savedQueuesAppbar, NavigationIcon.Arrow)
        loadSavedQueues()
    }

    private fun getAdapter() = binding.savedQueuesList.adapter as? SavedQueuesAdapter

    private fun loadSavedQueues() {
        ensureBackgroundThread {
            val items = QueueResumeHelper.listSavedQueues(this).mapNotNull { state ->
                val tracks = QueueResumeHelper.loadTracksForState(this, state)
                if (tracks.isEmpty()) {
                    null
                } else {
                    val currentTrack = tracks.getOrNull(state.currentIndex)
                        ?: tracks.firstOrNull { it.mediaStoreId == state.currentTrackId }
                    SavedQueue(state, currentTrack, tracks.size)
                }
            }

            runOnUiThread {
                getAdapter()?.updateItems(items)
                binding.savedQueuesPlaceholder.beVisibleIf(items.isEmpty())
            }
        }
    }

    private fun resumeQueue(savedQueue: SavedQueue) {
        ensureBackgroundThread {
            val tracks = QueueResumeHelper.loadTracksForState(this, savedQueue.state)
            runOnUiThread {
                if (tracks.isEmpty()) {
                    toast(R.string.saved_queue_no_tracks)
                } else {
                    // startPositionMs 0 lets prepareAndPlay look the saved
                    // position up, so the queue resumes where it stopped.
                    prepareAndPlay(tracks)
                }
            }
        }
    }

    private fun askForgetOne(savedQueue: SavedQueue) {
        ConfirmationDialog(
            this, "", R.string.forget_saved_queue_warning,
            org.fossify.commons.R.string.ok, org.fossify.commons.R.string.cancel
        ) {
            ensureBackgroundThread {
                QueueResumeHelper.forgetSavedQueue(this, savedQueue.state.queueKey)
                runOnUiThread { loadSavedQueues() }
            }
        }
    }

    private fun askForgetAll() {
        ConfirmationDialog(
            this, "", R.string.forget_all_saved_queues_warning,
            org.fossify.commons.R.string.ok, org.fossify.commons.R.string.cancel
        ) {
            ensureBackgroundThread {
                QueueResumeHelper.forgetAllSavedQueues(this)
                runOnUiThread { loadSavedQueues() }
            }
        }
    }
}
