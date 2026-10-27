package org.fossify.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.ItemNamedQueueBinding
import org.fossify.musicplayer.models.NamedQueueRow

/** Plain list adapter for the Queues screen: tap opens the queue, long-press deletes it. */
class QueuesAdapter(
    private val activity: SimpleActivity,
    private var items: ArrayList<NamedQueueRow>,
    private val itemClick: (NamedQueueRow) -> Unit,
    private val itemLongClick: (NamedQueueRow) -> Unit
) : RecyclerView.Adapter<QueuesAdapter.ViewHolder>() {

    fun updateItems(newItems: List<NamedQueueRow>) {
        items = ArrayList(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNamedQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemNamedQueueBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NamedQueueRow) {
            val queue = item.queue
            val track = item.currentTrack

            binding.namedQueueTitle.text = queue.name

            val parts = ArrayList<String>()
            if (track != null) {
                parts.add(
                    if (track.artist.isNotBlank()) "${track.artist} — ${track.title}" else track.title
                )
            }
            if (queue.lastPositionMs > 0L) {
                val positionText = (queue.lastPositionMs / 1000).toInt().getFormattedDuration()
                parts.add(activity.getString(R.string.saved_queue_resume_at, positionText))
            }
            val displayIndex = (queue.currentIndex + 1).coerceAtMost(queue.trackCount.coerceAtLeast(1))
            parts.add(activity.getString(R.string.saved_queue_track_position, displayIndex, queue.trackCount))
            binding.namedQueueSubtitle.text = parts.joinToString(" • ")

            binding.root.setOnClickListener { itemClick(item) }
            binding.root.setOnLongClickListener {
                itemLongClick(item)
                true
            }
        }
    }
}
