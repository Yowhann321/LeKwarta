package com.lekwarta.app

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lekwarta.app.data.LeKwartaDb
import com.lekwarta.app.data.Money
import com.lekwarta.app.data.Periods
import com.lekwarta.app.models.Transaction
import com.lekwarta.app.models.TransactionCategory
import com.lekwarta.app.models.User
import com.lekwarta.app.ui.ShotClockView
import com.lekwarta.app.ui.TransactionSheet
import com.lekwarta.app.ui.Ui
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The dashboard: balance scoreboard, daily-budget shot clock, a spending heatmap calendar,
 * and the transactions for whichever day is selected (today by default).
 */
class HomeFragment : Fragment() {

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var tvGreeting: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvMonthIn: TextView
    private lateinit var tvMonthOut: TextView
    private lateinit var shotClock: ShotClockView
    private lateinit var tvShotClockMain: TextView
    private lateinit var tvShotClockSub: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvCalendarMonth: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var legend: LinearLayout
    private lateinit var tvDayLabel: TextView
    private lateinit var tvDaySummary: TextView
    private lateinit var rvDay: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var chips: Map<String, TextView>

    // ─── State ────────────────────────────────────────────────────────────────
    private val db by lazy { LeKwartaDb.get(requireContext()) }
    private lateinit var user: User
    private val displayMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selectedDay = Calendar.getInstance()
    private var filter = "all"
    private val dayAdapter = DayAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        rvDay.adapter = dayAdapter
        buildLegend()

        view.findViewById<ImageButton>(R.id.btnCalPrev).setOnClickListener { shiftMonth(-1) }
        view.findViewById<ImageButton>(R.id.btnCalNext).setOnClickListener { shiftMonth(1) }
        view.findViewById<View>(R.id.cardShotClock).setOnClickListener { editBudget() }
        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            TransactionSheet(this, user.id, selectedDay) {
                toast("Logged")
                refresh()
            }.show()
        }
        chips.forEach { (key, chip) -> chip.setOnClickListener { setFilter(key) } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindViews(view: View) {
        tvGreeting      = view.findViewById(R.id.tvGreeting)
        tvUserName      = view.findViewById(R.id.tvUserName)
        tvAvatar        = view.findViewById(R.id.tvAvatar)
        tvTotalBalance  = view.findViewById(R.id.tvTotalBalance)
        tvMonthIn       = view.findViewById(R.id.tvMonthIn)
        tvMonthOut      = view.findViewById(R.id.tvMonthOut)
        shotClock       = view.findViewById(R.id.shotClock)
        tvShotClockMain = view.findViewById(R.id.tvShotClockMain)
        tvShotClockSub  = view.findViewById(R.id.tvShotClockSub)
        tvStreak        = view.findViewById(R.id.tvStreak)
        tvCalendarMonth = view.findViewById(R.id.tvCalendarMonth)
        calendarGrid    = view.findViewById(R.id.calendarGrid)
        legend          = view.findViewById(R.id.legend)
        tvDayLabel      = view.findViewById(R.id.tvDayLabel)
        tvDaySummary    = view.findViewById(R.id.tvDaySummary)
        rvDay           = view.findViewById(R.id.rvDay)
        tvEmpty         = view.findViewById(R.id.tvEmpty)
        chips = mapOf(
            "all" to view.findViewById(R.id.chipAll),
            Transaction.EXPENSE to view.findViewById(R.id.chipExpense),
            Transaction.INCOME to view.findViewById(R.id.chipIncome),
            Transaction.TRANSFER to view.findViewById(R.id.chipTransfer)
        )
    }

    // ─── Refresh everything ───────────────────────────────────────────────────
    private fun refresh() {
        user = (activity as MainActivity).currentUser() ?: return
        tvGreeting.text = greeting()
        tvUserName.text = user.name
        tvAvatar.text = user.name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }

        renderScoreboard()
        renderShotClock()
        renderCalendar()
        renderDay()
    }

    private fun renderScoreboard() {
        tvTotalBalance.text = Money.peso(db.getAccounts(user.id).sumOf { it.balance })
        val now = Calendar.getInstance()
        val (from, to) = Periods.month(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        val month = db.getTransactions(user.id, from, to)
        tvMonthIn.text = Money.peso(month.filter { it.type == Transaction.INCOME }.sumOf { it.amount })
        tvMonthOut.text = Money.peso(month.filter { it.type == Transaction.EXPENSE }.sumOf { it.amount })
    }

    private fun renderShotClock() {
        val (from, to) = Periods.day(System.currentTimeMillis())
        val spent = db.expenseBetween(user.id, from, to)
        val budget = user.dailyBudget
        shotClock.progress = if (budget > 0) (spent / budget).toFloat() else 0f
        tvShotClockMain.text = when {
            budget <= 0 -> "No daily budget set"
            spent <= budget -> "${Money.peso(budget - spent)} left today"
            else -> "Over by ${Money.peso(spent - budget)}"
        }
        tvShotClockSub.text = "Spent ${Money.peso(spent)} of ${Money.peso(budget)} · tap to change"
        val streak = db.budgetStreak(user)
        tvStreak.text = if (streak > 0) "🏀 $streak-day streak under budget" else "🏀 Start a streak today"
    }

    private fun editBudget() {
        Ui.askAmount(requireContext(), "Daily budget", user.dailyBudget) { value ->
            db.setDailyBudget(user.id, value)
            refresh()
        }
    }

    // ─── Calendar heatmap ─────────────────────────────────────────────────────
    private fun shiftMonth(delta: Int) {
        displayMonth.add(Calendar.MONTH, delta)
        renderCalendar()
    }

    private fun renderCalendar() {
        tvCalendarMonth.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayMonth.time)
        calendarGrid.removeAllViews()

        listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
            calendarGrid.addView(TextView(requireContext()).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(color(R.color.text_hint))
                layoutParams = cellParams(Ui.dp(requireContext(), 22))
            })
        }

        val year = displayMonth.get(Calendar.YEAR)
        val month = displayMonth.get(Calendar.MONTH)
        val totals = db.dailyTotals(user.id, year, month)
        val firstDow = displayMonth.get(Calendar.DAY_OF_WEEK) - 1
        val days = displayMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()

        repeat(firstDow) { calendarGrid.addView(View(requireContext()).apply { layoutParams = cellParams(cellSize()) }) }

        for (day in 1..days) {
            val (spent, earned) = totals[day] ?: (0.0 to 0.0)
            val isToday = sameDay(today, year, month, day)
            val isSelected = sameDay(selectedDay, year, month, day)
            val isFuture = Calendar.getInstance().apply { set(year, month, day) }.after(today) && !isToday

            calendarGrid.addView(TextView(requireContext()).apply {
                text = day.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = cellParams(cellSize())
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(requireContext(), 9).toFloat()
                    setColor(if (isSelected) color(R.color.gold) else heatColor(spent))
                    if (isToday && !isSelected) setStroke(Ui.dp(requireContext(), 2), color(R.color.gold))
                }
                setTextColor(when {
                    isSelected -> color(R.color.bg_dark)
                    isFuture -> color(R.color.text_hint)
                    earned > 0 -> color(R.color.green_primary)
                    else -> color(R.color.text_primary)
                })
                if (isSelected || isToday) setTypeface(typeface, android.graphics.Typeface.BOLD)
                if (!isFuture) setOnClickListener {
                    selectedDay = Calendar.getInstance().apply { set(year, month, day) }
                    renderCalendar()
                    renderDay()
                }
            })
        }
    }

    /** Darker purple the closer a day gets to the daily budget; red once it goes over. */
    private fun heatColor(spent: Double): Int {
        val budget = user.dailyBudget.takeIf { it > 0 } ?: 500.0
        return color(when {
            spent <= 0 -> R.color.heat_0
            spent > budget -> R.color.heat_over
            spent > budget * 0.6 -> R.color.heat_3
            spent > budget * 0.25 -> R.color.heat_2
            else -> R.color.heat_1
        })
    }

    private fun buildLegend() {
        legend.removeAllViews()
        fun swatch(colorRes: Int) = View(requireContext()).apply {
            val s = Ui.dp(requireContext(), 11)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = Ui.dp(requireContext(), 3) }
            background = GradientDrawable().apply { cornerRadius = Ui.dp(requireContext(), 3).toFloat(); setColor(color(colorRes)) }
        }
        fun label(text: String, colorRes: Int = R.color.text_hint) = TextView(requireContext()).apply {
            this.text = text
            textSize = 11f
            setTextColor(color(colorRes))
            setPadding(Ui.dp(requireContext(), 4), 0, Ui.dp(requireContext(), 10), 0)
        }
        legend.addView(label("Less"))
        listOf(R.color.heat_0, R.color.heat_1, R.color.heat_2, R.color.heat_3).forEach { legend.addView(swatch(it)) }
        legend.addView(label("More"))
        legend.addView(swatch(R.color.heat_over))
        legend.addView(label("Over"))
        legend.addView(label("Income day", R.color.green_primary))
    }

    private fun cellSize() = Ui.dp(requireContext(), 38)

    private fun cellParams(height: Int) = GridLayout.LayoutParams().apply {
        width = 0
        this.height = height
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        val m = Ui.dp(requireContext(), 2)
        setMargins(m, m, m, m)
    }

    private fun sameDay(c: Calendar, y: Int, m: Int, d: Int) =
        c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == d

    // ─── Selected day ─────────────────────────────────────────────────────────
    private fun setFilter(key: String) {
        filter = key
        chips.forEach { (k, chip) ->
            val on = k == key
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chip.setTextColor(color(if (on) R.color.bg_dark else R.color.text_primary))
        }
        renderDay()
    }

    private fun renderDay() {
        val (from, to) = Periods.day(selectedDay.timeInMillis)
        val all = db.getTransactions(user.id, from, to)
        val shown = if (filter == "all") all else all.filter { it.type == filter }

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
        val dateText = SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedDay.time)
        tvDayLabel.text = when {
            sameDay(today, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH)) -> "Today · $dateText"
            sameDay(yesterday, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH)) -> "Yesterday · $dateText"
            else -> SimpleDateFormat("EEEE · MMM d", Locale.getDefault()).format(selectedDay.time)
        }
        val spent = all.filter { it.type == Transaction.EXPENSE }.sumOf { it.amount }
        val earned = all.filter { it.type == Transaction.INCOME }.sumOf { it.amount }
        tvDaySummary.text = "Spent ${Money.peso(spent)} · Earned ${Money.peso(earned)}"

        dayAdapter.submit(shown)
        tvEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDetails(t: Transaction) {
        val time = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(t.date)
        val lines = buildList {
            add("Amount: ${Money.pesoCents(t.amount)}")
            add("Type: ${t.type.replaceFirstChar { it.uppercase() }}")
            if (t.type == Transaction.EXPENSE) add("Category: ${TransactionCategory.from(t.category).displayName}")
            add(if (t.type == Transaction.TRANSFER) "From ${t.accountName} to ${t.toAccountName}" else "Account: ${t.accountName}")
            add("When: $time")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("${TransactionCategory.iconFor(t)}  ${t.description}")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Close", null)
            .setNeutralButton("Delete") { _, _ ->
                Ui.confirm(requireContext(), "Delete this entry?",
                    "Its amount goes back to ${t.accountName}.", "Delete") {
                    db.deleteTransaction(t)
                    toast("Deleted")
                    refresh()
                }
            }
            .show()
    }

    private fun greeting() = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning,"
        in 12..17 -> "Good afternoon,"
        else -> "Good evening,"
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    // ─── Adapter ──────────────────────────────────────────────────────────────
    inner class DayAdapter : RecyclerView.Adapter<DayAdapter.VH>() {
        private var items: List<Transaction> = emptyList()

        fun submit(list: List<Transaction>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(R.id.tvTxnIcon)
            val tvName: TextView = view.findViewById(R.id.tvTxnName)
            val tvAcct: TextView = view.findViewById(R.id.tvTxnAccount)
            val tvAmount: TextView = view.findViewById(R.id.tvTxnAmount)
            val tvTime: TextView = view.findViewById(R.id.tvTxnTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = items[position]
            holder.tvIcon.text = TransactionCategory.iconFor(t)
            holder.tvName.text = t.description
            holder.tvAcct.text = when (t.type) {
                Transaction.TRANSFER -> "${t.accountName} → ${t.toAccountName}"
                Transaction.INCOME -> "${t.accountName} · Income"
                else -> "${t.accountName} · ${TransactionCategory.from(t.category).displayName}"
            }
            holder.tvTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(t.date)
            when (t.type) {
                Transaction.EXPENSE -> { holder.tvAmount.text = "-${Money.peso(t.amount)}"; holder.tvAmount.setTextColor(color(R.color.red_soft)) }
                Transaction.INCOME -> { holder.tvAmount.text = "+${Money.peso(t.amount)}"; holder.tvAmount.setTextColor(color(R.color.green_primary)) }
                else -> { holder.tvAmount.text = Money.peso(t.amount); holder.tvAmount.setTextColor(color(R.color.blue_soft)) }
            }
            holder.itemView.setOnClickListener { showDetails(t) }
        }
    }
}
