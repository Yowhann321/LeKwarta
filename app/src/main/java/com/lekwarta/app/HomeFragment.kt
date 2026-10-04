package com.lekwarta.app

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var tvGreeting: TextView
    private lateinit var tvUserName: TextView
    private lateinit var tvCalendarMonth: TextView
    private lateinit var btnCalPrev: ImageButton
    private lateinit var btnCalNext: ImageButton
    private lateinit var calendarGrid: GridLayout
    private lateinit var tvTodaySpend: TextView
    private lateinit var tvMonthlySpend: TextView
    private lateinit var tvTotalBalance: TextView
    private lateinit var rvRecent: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var pbHome: ProgressBar

    // ─── State ────────────────────────────────────────────────────────────────
    private var currentCalendar = Calendar.getInstance()
    private val recentTransactions = mutableListOf<JSONObject>()

    // Days that have transactions this month: date-of-month -> "expense"|"income"|"both"
    private val markedDays = mutableMapOf<Int, String>()

    // ─── Formatter ────────────────────────────────────────────────────────────
    private val pesoFmt = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupRecyclerView()
        setupFab()
        setupCalendarNav()

        val name = SessionManager.getUserName(requireContext())
        tvUserName.text = name
        tvGreeting.text = getGreeting()

        renderCalendar()
        fetchDashboardData()
    }

    // ─── Bind ─────────────────────────────────────────────────────────────────
    private fun bindViews(view: View) {
        tvGreeting      = view.findViewById(R.id.tvGreeting)
        tvUserName      = view.findViewById(R.id.tvUserName)
        tvCalendarMonth = view.findViewById(R.id.tvCalendarMonth)
        btnCalPrev      = view.findViewById(R.id.btnCalPrev)
        btnCalNext      = view.findViewById(R.id.btnCalNext)
        calendarGrid    = view.findViewById(R.id.calendarGrid)
        tvTodaySpend    = view.findViewById(R.id.tvTodaySpend)
        tvMonthlySpend  = view.findViewById(R.id.tvMonthlySpend)
        tvTotalBalance  = view.findViewById(R.id.tvTotalBalance)
        rvRecent        = view.findViewById(R.id.rvRecent)
        fab             = view.findViewById(R.id.fabAdd)
        pbHome          = view.findViewById(R.id.pbHome)
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        rvRecent.layoutManager = LinearLayoutManager(requireContext())
        rvRecent.adapter = RecentTransactionAdapter(recentTransactions)
    }

    // ─── FAB ──────────────────────────────────────────────────────────────────
    private fun setupFab() {
        fab.setOnClickListener {
            // Navigate to Transactions tab and open Add dialog
            (activity as? MainActivity)?.navigateTo(R.id.nav_transactions)
        }
    }

    // ─── Calendar nav ─────────────────────────────────────────────────────────
    private fun setupCalendarNav() {
        btnCalPrev.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            renderCalendar()
            fetchDashboardData()
        }
        btnCalNext.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            renderCalendar()
            fetchDashboardData()
        }
    }

    // ─── Calendar render ──────────────────────────────────────────────────────
    private fun renderCalendar() {
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvCalendarMonth.text = monthFmt.format(currentCalendar.time)

        calendarGrid.removeAllViews()

        // Day-of-week headers
        val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
        dayHeaders.forEach { label ->
            val tv = TextView(requireContext()).apply {
                text = label
                textSize = 11f
                setTextColor(resources.getColor(R.color.text_hint, null))
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            calendarGrid.addView(tv)
        }

        // Build day cells
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
        val daysInMonth    = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today          = Calendar.getInstance()
        val isCurrentMonth = cal.get(Calendar.YEAR)  == today.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == today.get(Calendar.MONTH)

        // Empty cells before first day
        repeat(firstDayOfWeek) {
            addEmptyCell()
        }

        // Day cells
        for (day in 1..daysInMonth) {
            val isToday = isCurrentMonth && day == today.get(Calendar.DAY_OF_MONTH)
            val mark    = markedDays[day]
            addDayCell(day, isToday, mark)
        }
    }

    private fun addEmptyCell() {
        val view = View(requireContext())
        view.layoutParams = GridLayout.LayoutParams().apply {
            width  = 0
            height = 36
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        calendarGrid.addView(view)
    }

    private fun addDayCell(day: Int, isToday: Boolean, mark: String?) {
        val tv = TextView(requireContext()).apply {
            text     = day.toString()
            textSize = 11f
            gravity  = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width  = 0
                height = 36
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }

            when {
                isToday -> {
                    setBackgroundResource(R.drawable.bg_calendar_today)
                    setTextColor(resources.getColor(R.color.bg_dark, null))
                }
                mark == "expense" -> setTextColor(resources.getColor(R.color.red_soft, null))
                mark == "income"  -> setTextColor(resources.getColor(R.color.green_primary, null))
                mark == "both"    -> setTextColor(resources.getColor(R.color.amber, null))
                else              -> setTextColor(resources.getColor(R.color.text_secondary, null))
            }
        }
        calendarGrid.addView(tv)
    }

    // ─── API call ─────────────────────────────────────────────────────────────
    private fun fetchDashboardData() {
        pbHome.visibility = View.VISIBLE

        val year  = currentCalendar.get(Calendar.YEAR)
        val month = currentCalendar.get(Calendar.MONTH) + 1
        val token = SessionManager.getToken(requireContext())

        val request = Request.Builder()
            .url("${ApiClient.BASE_URL}/dashboard.php?year=$year&month=$month")
            .addHeader("Authorization", "Bearer $token")
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    pbHome.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                activity?.runOnUiThread {
                    pbHome.visibility = View.GONE
                    try {
                        val json = JSONObject(body)

                        // Summary cards
                        tvTodaySpend.text  = "₱${pesoFmt.format(json.getDouble("today_spend"))}"
                        tvMonthlySpend.text = "₱${pesoFmt.format(json.getDouble("monthly_spend"))}"
                        tvTotalBalance.text = "₱${pesoFmt.format(json.getDouble("total_balance"))}"

                        // Calendar marks
                        markedDays.clear()
                        val marks = json.getJSONObject("calendar_marks")
                        marks.keys().forEach { key ->
                            markedDays[key.toInt()] = marks.getString(key)
                        }
                        renderCalendar()

                        // Recent transactions
                        recentTransactions.clear()
                        val arr = json.getJSONArray("recent_transactions")
                        for (i in 0 until arr.length()) {
                            recentTransactions.add(arr.getJSONObject(i))
                        }
                        rvRecent.adapter?.notifyDataSetChanged()

                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error parsing data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // ─── Greeting helper ──────────────────────────────────────────────────────
    private fun getGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11  -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
    }

    // ─── Inner adapter for recent transactions ────────────────────────────────
    inner class RecentTransactionAdapter(
        private val items: List<JSONObject>
    ) : RecyclerView.Adapter<RecentTransactionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon   : TextView = view.findViewById(R.id.tvTxnIcon)
            val tvName   : TextView = view.findViewById(R.id.tvTxnName)
            val tvAcct   : TextView = view.findViewById(R.id.tvTxnAccount)
            val tvAmount : TextView = view.findViewById(R.id.tvTxnAmount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item   = items[position]
            val type   = item.getString("type")   // "expense" or "income"
            val amount = item.getDouble("amount")

            holder.tvName.text  = item.getString("description")
            holder.tvAcct.text  = item.getString("account_name")
            holder.tvIcon.text  = getCategoryEmoji(item.getString("category"))

            if (type == "expense") {
                holder.tvAmount.text      = "-₱${pesoFmt.format(amount)}"
                holder.tvAmount.setTextColor(resources.getColor(R.color.red_soft, null))
            } else {
                holder.tvAmount.text      = "+₱${pesoFmt.format(amount)}"
                holder.tvAmount.setTextColor(resources.getColor(R.color.green_primary, null))
            }
        }

        private fun getCategoryEmoji(category: String): String = when (category.lowercase()) {
            "food"          -> "🍔"
            "transport"     -> "🚌"
            "shopping"      -> "🛍️"
            "income"        -> "💵"
            "bills"         -> "📄"
            "health"        -> "💊"
            "entertainment" -> "🎮"
            else            -> "💳"
        }
    }
}