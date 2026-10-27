package org.fossify.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.ItemSavedQueueBinding
import org.fossify.musicplayer.models.SavedQueue

/** Plain list adapter for the Saved Queues screen: tap resumes, long-press forgets. */
class SavedQueuesAdapter(
    private val activity: SimpleActivity,
    private var items: ArrayList<SavedQueue>,
    private val itemClick: (SavedQueue) -> Unit,
    private val itemLongClick: (SavedQueue) -> Unit
) : RecyclerView.Adapter<SavedQueuesAdapter.ViewHolder>() {

    fun updateItems(newItems: List<SavedQueue>) {
        items = ArrayList(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemSavedQueueBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SavedQueue) {
            val state = item.state
            val track = item.currentTrack

            binding.savedQueueTitle.text = when {
                state.label.isNotBlank() -> state.label
                track?.album?.isNotBlank() == true -> track.album
                track?.artist?.isNotBlank() == true -> track.artist
                else -> activity.getString(R.string.saved_queue)
            }

            val positionText = (state.lastPositionMs / 1000).toInt().getFormattedDuration()
            val parts = ArrayList<String>()
            if (track != null) {
                parts.add(
                    if (track.artist.isNotBlank()) "${track.artist} — ${track.title}" else track.title
                )
            }
            parts.add(activity.getString(R.string.saved_queue_resume_at, positionText))
            val displayIndex = (state.currentIndex + 1).coerceAtMost(state.trackCount.coerceAtLeast(1))
            parts.add(activity.getString(R.string.saved_queue_track_position, displayIndex, state.trackCount))
            binding.savedQueueSubtitle.text = parts.joinToString(" • ")

            binding.root.setOnClickListener { itemClick(item) }
            binding.root.setOnLongClickListener {
                itemLongClick(item)
                true
            }
        }
    }
}
