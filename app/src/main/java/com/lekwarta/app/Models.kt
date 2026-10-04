package com.lekwarta.app.models

/**
 * A user-added watchlist stock.
 */
data class StockItem(
    val ticker: String,
    val companyName: String = "",
    val price: Double = 0.0,
    val changePercent: Double? = null
)

/**
 * A market index shown in the horizontal overview strip.
 */
data class IndexItem(
    val ticker: String,          // Yahoo Finance symbol, e.g. "^GSPC"
    val displayName: String,     // Human label, e.g. "S&P 500"
    val price: Double = 0.0,
    val changePercent: Double? = null
)