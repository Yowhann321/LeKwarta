// app/adapters/CategoryAdapter.kt
package com.lekwarta.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lekwarta.app.R
import com.lekwarta.app.models.TransactionCategory

class CategoryAdapter(
    private val onCategorySelected: (TransactionCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    // All 7 categories explicitly defined
    private val categories = listOf(
        TransactionCategory.FOOD,
        TransactionCategory.TRANSPORT,
        TransactionCategory.SHOPPING,
        TransactionCategory.BILLS,
        TransactionCategory.HEALTH,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.OTHER
    )

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_chip, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        val isSelected = position == selectedPosition
        holder.bind(category, isSelected)

        holder.itemView.setOnClickListener {
            if (selectedPosition != position) {
                val prevSelected = selectedPosition
                selectedPosition = position
                notifyItemChanged(prevSelected)
                notifyItemChanged(selectedPosition)
                onCategorySelected(category)
            }
        }
    }

    override fun getItemCount() = categories.size

    fun getSelectedCategory(): TransactionCategory = categories[selectedPosition]

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategory: TextView = itemView as TextView

        fun bind(category: TransactionCategory, isSelected: Boolean) {
            tvCategory.text = "${category.icon} ${category.displayName}"

            if (isSelected) {
                tvCategory.background = itemView.resources.getDrawable(R.drawable.bg_chip_active, null)
                tvCategory.setTextColor(itemView.resources.getColor(R.color.bg_dark, null))
            } else {
                tvCategory.background = itemView.resources.getDrawable(R.drawable.bg_chip, null)
                tvCategory.setTextColor(itemView.resources.getColor(R.color.text_secondary, null))
            }
        }
    }
}