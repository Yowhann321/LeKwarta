// app/models/TransactionModel.kt - Make sure all categories are here
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
    val date: Date,
    val createdAt: String? = null
)

enum class TransactionType(val value: String, val displayName: String, val icon: String) {
    EXPENSE("expense", "Expense", "💸"),
    INCOME("income", "Income", "💰"),
    TRANSFER("transfer", "Transfer", "🔄")
}

enum class TransactionCategory(val value: String, val displayName: String, val icon: String) {
    FOOD("food", "Food", "🍔"),
    TRANSPORT("transport", "Transport", "🚌"),
    SHOPPING("shopping", "Shopping", "🛍️"),
    BILLS("bills", "Bills", "📄"),
    HEALTH("health", "Health", "💊"),
    ENTERTAINMENT("entertainment", "Entertainment", "🎮"),
    OTHER("other", "Other", "💳")
}

data class GroupedTransaction(
    val date: Date,
    val dateLabel: String,
    val transactions: List<Transaction>
)