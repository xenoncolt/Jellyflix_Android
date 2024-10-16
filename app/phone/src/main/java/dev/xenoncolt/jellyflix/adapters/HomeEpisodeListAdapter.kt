package dev.xenoncolt.jellyflix.adapters

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.xenoncolt.jellyflix.bindCardItemImage
import dev.xenoncolt.jellyflix.databinding.HomeEpisodeItemBinding
import dev.xenoncolt.jellyflix.models.FindroidEpisode
import dev.xenoncolt.jellyflix.models.FindroidItem
import dev.xenoncolt.jellyflix.models.FindroidMovie
import dev.xenoncolt.jellyflix.models.isDownloaded
import dev.xenoncolt.jellyflix.core.R as CoreR

class HomeEpisodeListAdapter(private val onClickListener: (item: FindroidItem) -> Unit) : ListAdapter<FindroidItem, HomeEpisodeListAdapter.EpisodeViewHolder>(DiffCallback) {
    class EpisodeViewHolder(
        private var binding: HomeEpisodeItemBinding,
        private val parent: ViewGroup,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FindroidItem) {
            if (item.playbackPositionTicks > 0) {
                binding.progressBar.layoutParams.width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (item.playbackPositionTicks.div(item.runtimeTicks.toFloat()).times(224)),
                    binding.progressBar.context.resources.displayMetrics,
                ).toInt()
                binding.progressBar.visibility = View.VISIBLE
            }

            binding.downloadedIcon.isVisible = item.isDownloaded()

            when (item) {
                is FindroidMovie -> {
                    binding.primaryName.text = item.name
                    binding.secondaryName.visibility = View.GONE
                }
                is FindroidEpisode -> {
                    binding.primaryName.text = item.seriesName
                    binding.secondaryName.text = if (item.indexNumberEnd == null) {
                        parent.resources.getString(CoreR.string.episode_name_extended, item.parentIndexNumber, item.indexNumber, item.name)
                    } else {
                        parent.resources.getString(CoreR.string.episode_name_extended_with_end, item.parentIndexNumber, item.indexNumber, item.indexNumberEnd, item.name)
                    }
                }
            }

            bindCardItemImage(binding.episodeImage, item)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FindroidItem>() {
        override fun areItemsTheSame(oldItem: FindroidItem, newItem: FindroidItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FindroidItem, newItem: FindroidItem): Boolean {
            return oldItem.name == newItem.name
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        return EpisodeViewHolder(
            HomeEpisodeItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            parent,
        )
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener(item)
        }
        holder.bind(item)
    }
}
