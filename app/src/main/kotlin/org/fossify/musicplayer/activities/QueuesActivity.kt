package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.QueuesAdapter
import org.fossify.musicplayer.databinding.ActivityQueuesBinding
import org.fossify.musicplayer.helpers.NamedQueuesHelper
import org.fossify.musicplayer.models.NamedQueueRow

/**
 * Queues (fork): the named queues the user keeps, like playlists, newest
 * first. Each queue remembers exactly where playback stopped in it. Tap a
 * queue to open its tracks with the stopped-on song highlighted; long-press
 * a queue to delete it (the music is untouched).
 */
class QueuesActivity : SimpleControllerActivity() {

    private val binding by viewBinding(ActivityQueuesBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.queuesList))
        setupMaterialScrollListener(binding.queuesList, binding.queuesAppbar)

        binding.queuesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.delete_all_queues -> askDeleteAll()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        binding.queuesList.adapter = QueuesAdapter(
            activity = this,
            items = arrayListOf(),
            itemClick = { row -> openQueue(row) },
            itemLongClick = { row -> askDeleteOne(row) }
        )
        binding.queuesFastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.queuesAppbar, NavigationIcon.Arrow)
        loadQueues()
    }

    private fun getAdapter() = binding.queuesList.adapter as? QueuesAdapter

    private fun loadQueues() {
        ensureBackgroundThread {
            val items = NamedQueuesHelper.listQueues(this)

            runOnUiThread {
                getAdapter()?.updateItems(items)
                binding.queuesPlaceholder.beVisibleIf(items.isEmpty())
            }
        }
    }

    private fun openQueue(row: NamedQueueRow) {
        val intent = Intent(this, QueueDetailActivity::class.java).apply {
            putExtra(QueueDetailActivity.EXTRA_QUEUE_ID, row.queue.id)
        }
        startActivity(intent)
    }

    private fun askDeleteOne(row: NamedQueueRow) {
        ConfirmationDialog(
            this, "", R.string.delete_queue_warning,
            org.fossify.commons.R.string.ok, org.fossify.commons.R.string.cancel
        ) {
            ensureBackgroundThread {
                NamedQueuesHelper.deleteQueue(this, row.queue.id)
                runOnUiThread { loadQueues() }
            }
        }
    }

    private fun askDeleteAll() {
        ConfirmationDialog(
            this, "", R.string.delete_all_queues_warning,
            org.fossify.commons.R.string.ok, org.fossify.commons.R.string.cancel
        ) {
            ensureBackgroundThread {
                NamedQueuesHelper.deleteAllQueues(this)
                runOnUiThread { loadQueues() }
            }
        }
    }
}
