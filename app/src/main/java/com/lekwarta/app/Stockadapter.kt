package com.lekwarta.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lekwarta.app.R
import com.lekwarta.app.models.StockItem

class StockAdapter(
    private val onItemLongClick: (StockItem) -> Unit
) : ListAdapter<StockItem, StockAdapter.StockViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<StockItem>() {
            override fun areItemsTheSame(old: StockItem, new: StockItem) =
                old.ticker == new.ticker
            override fun areContentsTheSame(old: StockItem, new: StockItem) = old == new
        }

        // Muted accent colours for avatar backgrounds — cycles through list
        private val AVATAR_COLORS = listOf(
            0xFF1E3A5F.toInt(), // deep blue
            0xFF2D4A22.toInt(), // deep green
            0xFF4A2222.toInt(), // deep red
            0xFF3A2D4A.toInt(), // deep purple
            0xFF4A3A1E.toInt(), // deep amber
            0xFF1E4A4A.toInt(), // deep teal
        )
    }

    inner class StockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTickerAvatar: TextView = itemView.findViewById(R.id.tvTickerAvatar)
        val tvTicker: TextView = itemView.findViewById(R.id.tvTicker)
        val tvCompanyName: TextView = itemView.findViewById(R.id.tvCompanyName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvChangePercent: TextView = itemView.findViewById(R.id.tvChangePercent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_card, parent, false)
        return StockViewHolder(view)
    }

    override fun onBindViewHolder(holder: StockViewHolder, position: Int) {
        val stock = getItem(position)

        // Avatar: first 1–3 chars of ticker, coloured background
        holder.tvTickerAvatar.text = stock.ticker.take(3)
        val bgColor = AVATAR_COLORS[position % AVATAR_COLORS.size]
        holder.tvTickerAvatar.background.mutate().setTint(bgColor)

        holder.tvTicker.text = stock.ticker
        holder.tvCompanyName.text = stock.companyName.ifBlank { stock.ticker }

        // Price
        holder.tvPrice.text = if (stock.price > 0) "$%.2f".format(stock.price) else "—"

        // % change — colour chip green/red
        if (stock.changePercent != null) {
            val pct = stock.changePercent
            val sign = if (pct >= 0) "+" else ""
            holder.tvChangePercent.text = "$sign${"%.2f".format(pct)}%"
            val chipColor = if (pct >= 0) 0xFF1A3A2A.toInt() else 0xFF3A1A1A.toInt()
            val textColor = if (pct >= 0) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
            holder.tvChangePercent.background.mutate().setTint(chipColor)
            holder.tvChangePercent.setTextColor(textColor)
        } else {
            holder.tvChangePercent.text = "—"
            holder.tvChangePercent.background.mutate().setTint(0xFF2A2A2A.toInt())
            holder.tvChangePercent.setTextColor(Color.GRAY)
        }

        // Long press = delete prompt
        holder.itemView.setOnLongClickListener {
            onItemLongClick(stock)
            true
        }
    }
}