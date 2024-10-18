package dev.xenoncolt.jellyflix.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.xenoncolt.jellyflix.databinding.DiscoveredServerItemBinding
import dev.xenoncolt.jellyflix.models.DiscoveredServer

class DiscoveredServerListAdapter(
    private val clickListener: (server: DiscoveredServer) -> Unit,
) :
    ListAdapter<DiscoveredServer, DiscoveredServerListAdapter.DiscoveredServerViewHolder>(
        DiffCallback,
    ) {
    class DiscoveredServerViewHolder(private var binding: DiscoveredServerItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(server: DiscoveredServer) {
            binding.serverName.text = server.name
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DiscoveredServer>() {
        override fun areItemsTheSame(
            oldItem: DiscoveredServer,
            newItem: DiscoveredServer,
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: DiscoveredServer,
            newItem: DiscoveredServer,
        ): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DiscoveredServerViewHolder {
        return DiscoveredServerViewHolder(
            DiscoveredServerItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )
    }

    override fun onBindViewHolder(holder: DiscoveredServerViewHolder, position: Int) {
        val server = getItem(position)
        holder.itemView.setOnClickListener { clickListener(server) }
        holder.bind(server)
    }
}
