package com.lekwarta.app

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.lekwarta.app.data.LeKwartaDb
import com.lekwarta.app.data.Money
import com.lekwarta.app.data.Periods
import com.lekwarta.app.models.Transaction
import com.lekwarta.app.models.TransactionCategory
import com.lekwarta.app.models.User
import com.lekwarta.app.ui.Ui
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * More: profile, the "box score" (earned/spent summaries by day, month, or year),
 * and settings like the daily budget and switching accounts.
 */
class MoreFragment : Fragment() {

    private enum class Period { DAY, MONTH, YEAR }

    private lateinit var periodViews: Map<Period, TextView>
    private lateinit var tvPeriod: TextView
    private lateinit var tvSumIncome: TextView
    private lateinit var tvSumExpense: TextView
    private lateinit var tvSumNet: TextView
    private lateinit var tvSumCount: TextView
    private lateinit var layoutMonthly: View
    private lateinit var monthBars: LinearLayout
    private lateinit var monthLabels: LinearLayout
    private lateinit var categoryBars: LinearLayout
    private lateinit var tvBudgetValue: TextView

    private val db by lazy { LeKwartaDb.get(requireContext()) }
    private lateinit var user: User
    private var period = Period.MONTH
    private val cursor = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_more, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        periodViews = mapOf(
            Period.DAY to view.findViewById(R.id.periodDay),
            Period.MONTH to view.findViewById(R.id.periodMonth),
            Period.YEAR to view.findViewById(R.id.periodYear)
        )
        tvPeriod      = view.findViewById(R.id.tvPeriod)
        tvSumIncome   = view.findViewById(R.id.tvSumIncome)
        tvSumExpense  = view.findViewById(R.id.tvSumExpense)
        tvSumNet      = view.findViewById(R.id.tvSumNet)
        tvSumCount    = view.findViewById(R.id.tvSumCount)
        layoutMonthly = view.findViewById(R.id.layoutMonthly)
        monthBars     = view.findViewById(R.id.monthBars)
        monthLabels   = view.findViewById(R.id.monthLabels)
        categoryBars  = view.findViewById(R.id.categoryBars)
        tvBudgetValue = view.findViewById(R.id.tvBudgetValue)

        periodViews.forEach { (p, tv) -> tv.setOnClickListener { period = p; cursor.timeInMillis = System.currentTimeMillis(); renderSummary() } }
        view.findViewById<ImageButton>(R.id.btnPeriodPrev).setOnClickListener { shift(-1) }
        view.findViewById<ImageButton>(R.id.btnPeriodNext).setOnClickListener { shift(1) }

        view.findViewById<View>(R.id.rowBudget).setOnClickListener {
            Ui.askAmount(requireContext(), "Daily budget", user.dailyBudget) { value ->
                db.setDailyBudget(user.id, value)
                refresh()
            }
        }
        view.findViewById<View>(R.id.rowSwitch).setOnClickListener {
            Ui.confirm(requireContext(), "Switch account?",
                "You'll go back to the login screen to sign in as someone else.", "Switch") {
                (activity as MainActivity).logout()
            }
        }
        view.findViewById<View>(R.id.rowLogout).setOnClickListener {
            Ui.confirm(requireContext(), "Log out?", "Your data stays saved on this phone.", "Log out") {
                (activity as MainActivity).logout()
            }
        }
        view.findViewById<View>(R.id.rowAbout).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("LeKwarta")
                .setMessage("Your money, your court.\n\nLeKwarta mixes LeBron James with \"kwarta\", Filipino slang for money. " +
                    "Track spending, run interest and loan numbers, and follow the market.\n\n" +
                    "Made by Ric Bryan Masagca, Paul Jazzmir Entena, Kurt Tracy Amador, and Johann Lijauco.")
                .setPositiveButton("Close", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        user = (activity as MainActivity).currentUser() ?: return
        val view = requireView()
        view.findViewById<TextView>(R.id.tvMoreName).text = user.name
        view.findViewById<TextView>(R.id.tvMoreEmail).text = user.email
        view.findViewById<TextView>(R.id.tvMoreAvatar).text = user.name.split(" ")
            .filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        view.findViewById<TextView>(R.id.tvMoreSince).text =
            "On the roster since " + SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(user.createdAt)
        tvBudgetValue.text = Money.peso(user.dailyBudget)
        renderSummary()
    }

    // ─── Box score ────────────────────────────────────────────────────────────
    private fun shift(delta: Int) {
        when (period) {
            Period.DAY -> cursor.add(Calendar.DAY_OF_MONTH, delta)
            Period.MONTH -> cursor.add(Calendar.MONTH, delta)
            Period.YEAR -> cursor.add(Calendar.YEAR, delta)
        }
        renderSummary()
    }

    private fun renderSummary() {
        periodViews.forEach { (p, tv) ->
            val on = p == period
            tv.setBackgroundResource(if (on) R.drawable.bg_type_selected else R.drawable.bg_type)
            tv.setTextColor(color(if (on) R.color.gold else R.color.text_secondary))
        }

        val year = cursor.get(Calendar.YEAR)
        val (from, to) = when (period) {
            Period.DAY -> Periods.day(cursor.timeInMillis)
            Period.MONTH -> Periods.month(year, cursor.get(Calendar.MONTH))
            Period.YEAR -> Periods.year(year)
        }
        tvPeriod.text = when (period) {
            Period.DAY -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(cursor.time)
            Period.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cursor.time)
            Period.YEAR -> year.toString()
        }

        val txns = db.getTransactions(user.id, from, to)
        val income = txns.filter { it.type == Transaction.INCOME }.sumOf { it.amount }
        val expense = txns.filter { it.type == Transaction.EXPENSE }.sumOf { it.amount }
        val net = income - expense
        tvSumIncome.text = Money.peso(income)
        tvSumExpense.text = Money.peso(expense)
        tvSumNet.text = (if (net > 0) "+" else "") + Money.peso(net)
        tvSumNet.setTextColor(color(if (net >= 0) R.color.gold else R.color.red_soft))
        tvSumCount.text = "${txns.size} transaction${if (txns.size == 1) "" else "s"}"

        layoutMonthly.visibility = if (period == Period.YEAR) View.VISIBLE else View.GONE
        if (period == Period.YEAR) renderMonthBars(txns)
        renderCategoryBars(txns.filter { it.type == Transaction.EXPENSE }, expense)
    }

    /** Twelve vertical bars, one per month, scaled to the biggest month. */
    private fun renderMonthBars(txns: List<Transaction>) {
        val perMonth = DoubleArray(12)
        val cal = Calendar.getInstance()
        txns.filter { it.type == Transaction.EXPENSE }.forEach { cal.time = it.date; perMonth[cal.get(Calendar.MONTH)] += it.amount }
        val max = perMonth.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val currentMonth = if (Calendar.getInstance().get(Calendar.YEAR) == cursor.get(Calendar.YEAR))
            Calendar.getInstance().get(Calendar.MONTH) else -1

        monthBars.removeAllViews()
        monthLabels.removeAllViews()
        val chartHeight = Ui.dp(requireContext(), 110)
        "JFMAMJJASOND".forEachIndexed { i, letter ->
            val slot = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, chartHeight, 1f)
            }
            val barHeight = ((perMonth[i] / max) * (chartHeight - Ui.dp(requireContext(), 4))).toInt()
                .coerceAtLeast(Ui.dp(requireContext(), 3))
            slot.addView(View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(Ui.dp(requireContext(), 14), barHeight, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(requireContext(), 4).toFloat()
                    setColor(color(when {
                        perMonth[i] <= 0 -> R.color.heat_0
                        i == currentMonth -> R.color.gold
                        else -> R.color.purple_light
                    }))
                }
            })
            monthBars.addView(slot)
            monthLabels.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = letter.toString()
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(color(if (i == currentMonth) R.color.gold else R.color.text_hint))
            })
        }
    }

    /** Horizontal bars showing each category's share of spending. */
    private fun renderCategoryBars(expenses: List<Transaction>, total: Double) {
        categoryBars.removeAllViews()
        if (expenses.isEmpty()) {
            categoryBars.addView(TextView(requireContext()).apply {
                text = "No spending in this period."
                textSize = 13f
                setTextColor(color(R.color.text_hint))
                setPadding(0, Ui.dp(requireContext(), 8), 0, 0)
            })
            return
        }
        expenses.groupBy { TransactionCategory.from(it.category) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .forEach { (cat, amount) ->
                val share = (amount / total).toFloat()
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, Ui.dp(requireContext(), 8), 0, 0)
                }
                row.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${cat.icon}  ${cat.displayName}"
                        textSize = 13f
                        setTextColor(color(R.color.text_primary))
                    })
                    addView(TextView(context).apply {
                        text = "${Money.peso(amount)} · ${(share * 100).toInt()}%"
                        textSize = 12f
                        setTextColor(color(R.color.text_secondary))
                    })
                })
                val track = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 6))
                        .apply { topMargin = Ui.dp(requireContext(), 5) }
                    background = GradientDrawable().apply { cornerRadius = Ui.dp(context, 3).toFloat(); setColor(color(R.color.heat_0)) }
                }
                track.addView(View(requireContext()).apply {
                    background = GradientDrawable().apply { cornerRadius = Ui.dp(context, 3).toFloat(); setColor(color(R.color.gold)) }
                })
                track.post {
                    track.getChildAt(0).layoutParams = FrameLayout.LayoutParams((track.width * share).toInt().coerceAtLeast(Ui.dp(track.context, 4)), FrameLayout.LayoutParams.MATCH_PARENT)
                    track.getChildAt(0).requestLayout()
                }
                row.addView(track)
                categoryBars.addView(row)
            }
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
}
