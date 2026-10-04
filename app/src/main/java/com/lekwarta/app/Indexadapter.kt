package com.lekwarta.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lekwarta.app.R
import com.lekwarta.app.models.IndexItem

class IndexAdapter : RecyclerView.Adapter<IndexAdapter.IndexViewHolder>() {

    private val items = mutableListOf<IndexItem>()

    fun submitList(newItems: List<IndexItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class IndexViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIndexTicker: TextView = itemView.findViewById(R.id.tvIndexTicker)
        val tvIndexName: TextView = itemView.findViewById(R.id.tvIndexName)
        val tvIndexPrice: TextView = itemView.findViewById(R.id.tvIndexPrice)
        val tvIndexChange: TextView = itemView.findViewById(R.id.tvIndexChange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IndexViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_index_card, parent, false)
        return IndexViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: IndexViewHolder, position: Int) {
        val index = items[position]

        holder.tvIndexTicker.text = index.ticker
        holder.tvIndexName.text = index.displayName

        holder.tvIndexPrice.text = if (index.price > 0) {
            // PSEi is in the thousands — no $ prefix; others use $
            if (index.ticker == "^PSEi" || index.ticker == "^PSEI")
                "%,.2f".format(index.price)
            else
                "$%,.2f".format(index.price)
        } else "—"

        if (index.changePercent != null) {
            val pct = index.changePercent
            val sign = if (pct >= 0) "▲ +" else "▼ "
            holder.tvIndexChange.text = "$sign${"%.2f".format(pct)}%"
            val chipColor = if (pct >= 0) 0xFF1A3A2A.toInt() else 0xFF3A1A1A.toInt()
            val textColor = if (pct >= 0) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
            holder.tvIndexChange.background.mutate().setTint(chipColor)
            holder.tvIndexChange.setTextColor(textColor)
        } else {
            holder.tvIndexChange.text = "—"
            holder.tvIndexChange.background.mutate().setTint(0xFF2A2A2A.toInt())
            holder.tvIndexChange.setTextColor(Color.GRAY)
        }
    }
}