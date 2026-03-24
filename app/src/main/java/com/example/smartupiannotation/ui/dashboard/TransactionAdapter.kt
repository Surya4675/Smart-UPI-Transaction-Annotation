package com.example.smartupiannotation.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartupiannotation.data.local.entity.TransactionWithParticipants
import com.example.smartupiannotation.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onDeleteClick: (TransactionWithParticipants) -> Unit,
    private val onItemClick: (TransactionWithParticipants) -> Unit
) : ListAdapter<TransactionWithParticipants, TransactionAdapter.TransactionViewHolder>(DiffCallback) {

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        fun bind(item: TransactionWithParticipants) {
            binding.tvReceiverName.text = item.transaction.receiverName
            binding.tvAmount.text = "₹${"%.2f".format(item.transaction.amount)}"
            binding.tvDate.text = dateFormat.format(Date(item.transaction.transactionDate))
            
            val participantsSummary = if (item.participants.isNotEmpty()) {
                "\nSplit with: " + item.participants.joinToString { it.participantName }
            } else ""
            
            binding.tvNote.text = (item.transaction.note ?: "") + participantsSummary

            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<TransactionWithParticipants>() {
        override fun areItemsTheSame(oldItem: TransactionWithParticipants, newItem: TransactionWithParticipants): Boolean {
            return oldItem.transaction.transactionId == newItem.transaction.transactionId
        }

        override fun areContentsTheSame(oldItem: TransactionWithParticipants, newItem: TransactionWithParticipants): Boolean {
            return oldItem == newItem
        }
    }
}
