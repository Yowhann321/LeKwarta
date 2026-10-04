// app/models/Account.kt
package com.lekwarta.app.models

data class Account(
    val id: Int,
    val name: String,
    val type: String, // "cash", "bank", "ewallet"
    val balance: Double,
    val color: Int
) {
    companion object {
        const val TYPE_CASH = "cash"
        const val TYPE_BANK = "bank"
        const val TYPE_EWALLET = "ewallet"

        val TYPES = listOf(TYPE_CASH to "Cash", TYPE_BANK to "Bank", TYPE_EWALLET to "E-wallet")

        /** Card colors users can pick from: purple, gold, blue, red, green, teal. */
        val COLORS = listOf(
            0xFF552583.toInt(), 0xFFC9921A.toInt(), 0xFF2F5FB3.toInt(),
            0xFFB3302A.toInt(), 0xFF1F8A55.toInt(), 0xFF1C7C8C.toInt()
        )

        fun typeLabel(type: String) = TYPES.firstOrNull { it.first == type }?.second ?: "Account"
    }
}

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val dailyBudget: Double,
    val createdAt: Long
)
