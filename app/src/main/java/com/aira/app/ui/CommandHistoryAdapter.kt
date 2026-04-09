package com.aira.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aira.app.R
import com.aira.app.data.model.CommandHistoryItem
import com.aira.app.databinding.ItemCommandHistoryBinding
import java.text.DateFormat
import java.util.Date

class CommandHistoryAdapter :
    ListAdapter<CommandHistoryItem, CommandHistoryAdapter.CommandHistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandHistoryViewHolder {
        val binding = ItemCommandHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return CommandHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommandHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommandHistoryViewHolder(
        private val binding: ItemCommandHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandHistoryItem) {
            binding.textSpoken.text = item.spokenText
            binding.textActionSummary.text = item.actionSummary
            binding.textResult.text = item.resultSummary.ifBlank { item.status }
            binding.textTime.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.createdAt))
            binding.textStatus.text = item.status.replace("_", " ")
            applyStatusTone(binding.textStatus, item.status)
        }

        private fun applyStatusTone(view: TextView, status: String) {
            val colorRes = when (status) {
                "COMPLETED" -> R.color.status_success
                "FAILED", "BLOCKED" -> R.color.status_error
                "PENDING_CONFIRMATION" -> R.color.status_warning
                "EXECUTING" -> R.color.status_active
                else -> R.color.status_neutral
            }
            view.setTextColor(view.context.getColor(colorRes))
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CommandHistoryItem>() {
            override fun areItemsTheSame(oldItem: CommandHistoryItem, newItem: CommandHistoryItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CommandHistoryItem, newItem: CommandHistoryItem): Boolean =
                oldItem == newItem
        }
    }
}
