// app/models/Account.kt
package com.lekwarta.app.models

data class Account(
    val id: Int,
    val name: String,
    val type: String, // "cash", "bank", "ewallet"
    val balance: Double,
    val currency: String = "PHP"
)