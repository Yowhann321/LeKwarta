// app/models/TransactionModel.kt
package com.lekwarta.app.models

import java.util.Date

data class Transaction(
    val id: Int = 0,
    val userId: Int,
    val type: String, // "expense", "income", "transfer"
    val amount: Double,
    val category: String,
    val description: String,
    val accountId: Int,
    val accountName: String,
    val toAccountId: Int? = null,
    val toAccountName: String? = null,
    val date: Date
) {
    companion object {
        const val EXPENSE = "expense"
        const val INCOME = "income"
        const val TRANSFER = "transfer"
    }
}

enum class TransactionCategory(val value: String, val displayName: String, val icon: String) {
    FOOD("food", "Food", "🍔"),
    TRANSPORT("transport", "Transport", "🚌"),
    SHOPPING("shopping", "Shopping", "🛍️"),
    BILLS("bills", "Bills", "📄"),
    HEALTH("health", "Health", "💊"),
    ENTERTAINMENT("entertainment", "Entertainment", "🎮"),
    OTHER("other", "Other", "💳");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: OTHER

        fun iconFor(t: Transaction) = when (t.type) {
            Transaction.INCOME -> "💵"
            Transaction.TRANSFER -> "🔄"
            else -> from(t.category).icon
        }
    }
}
