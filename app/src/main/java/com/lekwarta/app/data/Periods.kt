package com.lekwarta.app.data

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Start/end timestamps for days, months, and years. Ranges are [from, to). */
object Periods {
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun day(millis: Long): Pair<Long, Long> {
        val from = startOfDay(millis)
        val to = Calendar.getInstance().apply { timeInMillis = from; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        return from to to
    }

    /** [month] is 0-based, like [Calendar.MONTH]. */
    fun month(year: Int, month: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return from to cal.timeInMillis
    }

    fun year(year: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1) }
        val from = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        return from to cal.timeInMillis
    }
}

/** Peso formatting used across the app. */
object Money {
    private val whole = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 0; maximumFractionDigits = 0
    }
    private val cents = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }

    fun peso(amount: Double) = (if (amount < 0) "-₱" else "₱") + whole.format(kotlin.math.abs(amount))
    fun pesoCents(amount: Double) = (if (amount < 0) "-₱" else "₱") + cents.format(kotlin.math.abs(amount))
    fun number(amount: Double): String = whole.format(amount)
}
