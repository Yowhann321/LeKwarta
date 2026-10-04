// app/adapters/TransactionAdapter.kt
package com.lekwarta.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lekwarta.app.R
import com.lekwarta.app.models.GroupedTransaction
import com.lekwarta.app.models.Transaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val groupedTransactions = mutableListOf<GroupedTransaction>()
    private val pesoFmt = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    fun submitList(transactions: List<Transaction>) {
        groupTransactions(transactions)
        notifyDataSetChanged()
    }

    private fun groupTransactions(transactions: List<Transaction>) {
        groupedTransactions.clear()
        val grouped = transactions.groupBy { getDateLabel(it.date) }
        grouped.forEach { (label, list) ->
            groupedTransactions.add(
                GroupedTransaction(
                    date = list.first().date,
                    dateLabel = label,
                    transactions = list.sortedByDescending { it.date }
                )
            )
        }
        groupedTransactions.sortByDescending { it.date }
    }

    private fun getDateLabel(date: Date): String {
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val transactionCal = Calendar.getInstance().apply { time = date }

        return when {
            transactionCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    transactionCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "TODAY"
            transactionCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    transactionCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "YESTERDAY"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date).uppercase()
        }
    }

    override fun getItemViewType(position: Int): Int {
        var pos = position
        for (group in groupedTransactions) {
            if (pos == 0) return TYPE_HEADER
            pos--
            if (pos < group.transactions.size) return TYPE_ITEM
            pos -= group.transactions.size
        }
        return TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction, parent, false)
                TransactionViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var pos = position
        for (group in groupedTransactions) {
            if (pos == 0 && holder is HeaderViewHolder) {
                holder.bind(group.dateLabel)
                return
            }
            if (holder is HeaderViewHolder) {
                pos--
            }
            if (pos < group.transactions.size && holder is TransactionViewHolder) {
                holder.bind(group.transactions[pos])
                return
            }
            if (holder is TransactionViewHolder) {
                pos -= group.transactions.size
            }
        }
    }

    override fun getItemCount(): Int {
        return groupedTransactions.sumOf { 1 + it.transactions.size }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tvHeader)

        fun bind(dateLabel: String) {
            tvHeader.text = dateLabel
        }
    }

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon: TextView = itemView.findViewById(R.id.tvTxnIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvTxnName)
        private val tvAccount: TextView = itemView.findViewById(R.id.tvTxnAccount)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvTxnAmount)

        fun bind(transaction: Transaction) {
            tvIcon.text = getCategoryIcon(transaction.category)
            tvName.text = transaction.description
            tvAccount.text = transaction.accountName

            when (transaction.type) {
                "expense" -> {
                    tvAmount.text = "-₱${pesoFmt.format(transaction.amount)}"
                    tvAmount.setTextColor(itemView.resources.getColor(R.color.red_soft, null))
                }
                "income" -> {
                    tvAmount.text = "+₱${pesoFmt.format(transaction.amount)}"
                    tvAmount.setTextColor(itemView.resources.getColor(R.color.green_primary, null))
                }
                else -> {
                    tvAmount.text = "↺ ₱${pesoFmt.format(transaction.amount)}"
                    tvAmount.setTextColor(itemView.resources.getColor(R.color.amber, null))
                }
            }

            itemView.setOnClickListener { onItemClick(transaction) }
            itemView.setOnLongClickListener {
                onItemLongClick(transaction)
                true
            }
        }

        private fun getCategoryIcon(category: String): String = when (category.lowercase()) {
            "food" -> "🍔"
            "transport" -> "🚌"
            "shopping" -> "🛍️"
            "bills" -> "📄"
            "health" -> "💊"
            "entertainment" -> "🎮"
            "income" -> "💵"
            "transfer" -> "🔄"
            else -> "💳"
        }
    }
}