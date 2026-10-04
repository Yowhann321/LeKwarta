package com.lekwarta.app.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lekwarta.app.BuildConfig
import com.lekwarta.app.R
import com.lekwarta.app.adapters.IndexAdapter
import com.lekwarta.app.adapters.StockAdapter
import com.lekwarta.app.models.IndexItem
import com.lekwarta.app.models.StockItem
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class StocksFragment : Fragment() {

    private val TAG = "StocksFragment"

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var rvIndices: RecyclerView
    private lateinit var rvWatchlist: RecyclerView
    private lateinit var fabAddStock: FloatingActionButton
    private lateinit var tvLastUpdated: TextView
    private lateinit var tvStockCount: TextView
    private lateinit var layoutEmpty: LinearLayout

    // ── Adapters ───────────────────────────────────────────────────────────────
    private val indexAdapter = IndexAdapter()
    private lateinit var stockAdapter: StockAdapter

    // ── Finnhub ────────────────────────────────────────────────────────────────
    private val FINNHUB_KEY     = BuildConfig.FINNHUB_API_KEY
    private val FINNHUB_QUOTE   = "https://finnhub.io/api/v1/quote"
    private val FINNHUB_PROFILE = "https://finnhub.io/api/v1/stock/profile2"
    private val FINNHUB_SEARCH  = "https://finnhub.io/api/v1/search"

    // ── State ──────────────────────────────────────────────────────────────────
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchlistTickers = mutableListOf<String>()
    private val stockDataMap = mutableMapOf<String, StockItem>()
    private val indexDataMap = mutableMapOf<String, IndexItem>()
    private var searchDebounce: Runnable? = null

    private val defaultIndices = listOf(
        IndexItem("VOO", "S&P 500"),
        IndexItem("QQQ", "NASDAQ 100"),
        IndexItem("DIA", "Dow Jones"),
        IndexItem("VTI", "Total Market"),
    )

    private val defaultWatchlist = listOf("AAPL", "MSFT")

    data class SearchResult(val symbol: String, val name: String, val type: String)

    companion object {
        private const val PREFS_NAME    = "lekwarta_stocks"
        private const val KEY_WATCHLIST = "watchlist"
        private const val REFRESH_MS    = 60_000L
        private const val FETCH_DELAY   = 300L
        private const val SEARCH_DELAY  = 500L

        // Dark dialog colours
        private const val COLOR_BG       = 0xFF1D1230.toInt()
        private const val COLOR_SURFACE  = 0xFF261A3B.toInt()
        private const val COLOR_TEXT     = 0xFFF5EFE0.toInt()
        private const val COLOR_HINT     = 0xFF6E5C8A.toInt()
        private const val COLOR_SUBTEXT  = 0xFFB9A6D6.toInt()
        private const val COLOR_DIVIDER  = 0xFF3A2752.toInt()
        private const val COLOR_RED      = 0xFFFF6B6B.toInt()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchAll()
            mainHandler.postDelayed(this, REFRESH_MS)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_stocks, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "✅ onViewCreated called")
        bindViews(view)
        setupAdapters()
        setupSwipeToDelete()
        loadWatchlist()
        pushWatchlistToAdapter()
        pushIndicesToAdapter()
        startAutoRefresh()
        setupFab()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacks(refreshRunnable)
        searchDebounce?.let { mainHandler.removeCallbacks(it) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        rvIndices     = view.findViewById(R.id.rvIndices)
        rvWatchlist   = view.findViewById(R.id.rvWatchlist)
        fabAddStock   = view.findViewById(R.id.fabAddStock)
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated)
        tvStockCount  = view.findViewById(R.id.tvStockCount)
        layoutEmpty   = view.findViewById(R.id.layoutEmpty)
    }

    private fun setupAdapters() {
        rvIndices.adapter = indexAdapter

        // Long press → confirm delete dialog
        stockAdapter = StockAdapter { stock ->
            showRemoveDialog(stock.ticker)
        }
        rvWatchlist.adapter = stockAdapter
    }

    /**
     * Swipe left on any watchlist card → red confirmation dialog to remove.
     */
    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val items    = watchlistTickers.map { t -> stockDataMap[t] ?: StockItem(t) }
                if (position < 0 || position >= items.size) return
                val ticker = items[position].ticker

                // Snap the card back visually before showing dialog
                stockAdapter.notifyItemChanged(position)
                showRemoveDialog(ticker)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvWatchlist)
    }

    private fun setupFab() {
        fabAddStock.setOnClickListener { showSearchDialog() }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Watchlist persistence
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadWatchlist() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)
        val saved = prefs.getString(KEY_WATCHLIST, null)
        watchlistTickers.clear()
        if (saved.isNullOrBlank()) {
            watchlistTickers.addAll(defaultWatchlist)
            saveWatchlist()
        } else {
            watchlistTickers.addAll(
                saved.split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
        }
    }

    private fun saveWatchlist() {
        requireContext().getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_WATCHLIST, watchlistTickers.joinToString(","))
            .apply()
    }

    private fun addStock(ticker: String, companyName: String = "") {
        val upper = ticker.uppercase().trim()
        if (upper.isBlank()) return
        if (watchlistTickers.contains(upper)) {
            Toast.makeText(requireContext(), "$upper is already in your watchlist", Toast.LENGTH_SHORT).show()
            return
        }
        if (companyName.isNotBlank()) {
            stockDataMap[upper] = StockItem(ticker = upper, companyName = companyName)
        }
        watchlistTickers.add(upper)
        saveWatchlist()
        pushWatchlistToAdapter()
        fetchSingleStock(upper)
        Toast.makeText(requireContext(), "✓ Added $upper to watchlist", Toast.LENGTH_SHORT).show()
    }

    private fun removeStock(ticker: String) {
        watchlistTickers.remove(ticker)
        stockDataMap.remove(ticker)
        saveWatchlist()
        pushWatchlistToAdapter()
        Toast.makeText(requireContext(), "Removed $ticker", Toast.LENGTH_SHORT).show()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Remove dialog — dark themed
    // ──────────────────────────────────────────────────────────────────────────

    private fun showRemoveDialog(ticker: String) {
        val stockName = stockDataMap[ticker]?.companyName?.takeIf { it != ticker } ?: ticker

        AlertDialog.Builder(requireContext())
            .setTitle("Remove $ticker?")
            .setMessage("Remove $stockName from your watchlist?")
            .setPositiveButton("Remove") { _, _ -> removeStock(ticker) }
            .setNegativeButton("Keep", null)
            .show()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Search dialog — fully dark themed
    // ──────────────────────────────────────────────────────────────────────────

    private fun showSearchDialog() {
        val ctx = requireContext()
        val dp  = resources.displayMetrics.density

        // ── Root container ────────────────────────────────────────────────────
        val root = LinearLayout(ctx).apply {
            orientation    = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        // ── Search box container (rounded dark bg) ────────────────────────────
        val searchContainer = FrameLayout(ctx).apply {
            setBackgroundColor(COLOR_SURFACE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = 12 * dp
            }
            val pad = (12 * dp).toInt()
            setPadding(pad, 0, pad, 0)
        }

        val searchBox = EditText(ctx).apply {
            hint           = "Search e.g. Apple, Bitcoin, Tesla…"
            inputType      = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_HINT)
            setBackgroundColor(Color.TRANSPARENT)
            textSize       = 15f
        }
        searchContainer.addView(searchBox, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (48 * dp).toInt()
        ))

        val searchParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (10 * dp).toInt() }
        root.addView(searchContainer, searchParams)

        // ── Status label ──────────────────────────────────────────────────────
        val tvStatus = TextView(ctx).apply {
            text     = "Type to search for any stock or ETF"
            setTextColor(COLOR_HINT)
            textSize = 12f
            val vpad = (4 * dp).toInt()
            setPadding(4, vpad, 0, vpad)
        }
        root.addView(tvStatus)

        // ── Results list ──────────────────────────────────────────────────────
        val searchResults = mutableListOf<SearchResult>()

        val listAdapter = object : ArrayAdapter<SearchResult>(
            ctx, android.R.layout.simple_list_item_2, android.R.id.text1, searchResults
        ) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v    = super.getView(pos, convertView, parent)
                val item = searchResults[pos]

                v.setBackgroundColor(if (pos % 2 == 0) COLOR_BG else COLOR_SURFACE)

                v.findViewById<TextView>(android.R.id.text1).apply {
                    text     = item.symbol
                    textSize = 15f
                    setTextColor(COLOR_TEXT)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                v.findViewById<TextView>(android.R.id.text2).apply {
                    text     = item.name + if (item.type.isNotBlank()) "  ·  ${item.type}" else ""
                    textSize = 12f
                    setTextColor(COLOR_SUBTEXT)
                }
                return v
            }
        }

        val listView = ListView(ctx).apply {
            adapter          = listAdapter
            divider          = ColorDrawable(COLOR_DIVIDER)
            dividerHeight    = 1
            setBackgroundColor(COLOR_BG)
        }
        root.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (300 * dp).toInt()
        ))

        // ── Build & show dialog ───────────────────────────────────────────────
        val dialog = AlertDialog.Builder(ctx)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(COLOR_BG))
        }

        // Style the dialog title manually after show()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(COLOR_BG)
                    cornerRadius = 16 * dp
                }
            )
        }

        // Tap result → add and close
        listView.setOnItemClickListener { _, _, pos, _ ->
            val picked = searchResults[pos]
            dialog.dismiss()
            addStock(picked.symbol, picked.name)
        }

        // Debounced search on typing
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchDebounce?.let { mainHandler.removeCallbacks(it) }

                if (query.length < 2) {
                    tvStatus.text = "Type to search for any stock or ETF"
                    searchResults.clear()
                    listAdapter.notifyDataSetChanged()
                    return
                }

                tvStatus.text = "Searching…"
                searchDebounce = Runnable {
                    doSearch(query, searchResults, listAdapter, tvStatus)
                }
                mainHandler.postDelayed(searchDebounce!!, SEARCH_DELAY)
            }
        })

        dialog.show()
        searchBox.requestFocus()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Finnhub symbol search
    // ──────────────────────────────────────────────────────────────────────────

    private fun doSearch(
        query: String,
        results: MutableList<SearchResult>,
        adapter: ArrayAdapter<SearchResult>,
        tvStatus: TextView
    ) {
        val url = "$FINNHUB_SEARCH?q=${query.replace(" ", "%20")}&token=$FINNHUB_KEY"
        Log.d(TAG, "Search: $url")

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { tvStatus.text = "⚠ Search failed — check connection" }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.use { it.body?.string() } ?: return
                try {
                    val arr   = JSONObject(body).getJSONArray("result")
                    val found = mutableListOf<SearchResult>()

                    for (i in 0 until minOf(arr.length(), 15)) {
                        val obj    = arr.getJSONObject(i)
                        val symbol = obj.optString("symbol", "").trim()
                        val name   = obj.optString("description", symbol).capitalizeWords()
                        val type   = obj.optString("type", "")
                        if (symbol.isBlank() || symbol.contains(".")) continue
                        found.add(SearchResult(symbol, name, type))
                    }

                    mainHandler.post {
                        results.clear()
                        results.addAll(found)
                        adapter.notifyDataSetChanged()
                        tvStatus.text = when {
                            found.isEmpty() -> "No results for \"$query\""
                            else            -> "Tap a result to add it to your watchlist"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search parse error: ${e.message}")
                    mainHandler.post { tvStatus.text = "⚠ Could not read results" }
                }
            }
        })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Network — Finnhub quotes + profile
    // ──────────────────────────────────────────────────────────────────────────

    private fun startAutoRefresh() {
        mainHandler.post(refreshRunnable)
    }

    private fun fetchAll() {
        val all = watchlistTickers + defaultIndices.map { it.ticker }
        all.forEachIndexed { i, symbol ->
            mainHandler.postDelayed({ fetchQuote(symbol) }, i * FETCH_DELAY)
        }
    }

    private fun fetchSingleStock(ticker: String) {
        fetchProfile(ticker)
        fetchQuote(ticker)
    }

    private fun fetchQuote(symbol: String) {
        val url = "$FINNHUB_QUOTE?symbol=${symbol.uppercase()}&token=$FINNHUB_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Quote failed $symbol: ${e.message}")
                mainHandler.post { tvLastUpdated.text = "⚠ No connection" }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.use { it.body?.string() } ?: return
                try {
                    val json      = JSONObject(body)
                    val price     = json.optDouble("c", 0.0)
                    val changePct = json.optDouble("dp", Double.NaN)
                    if (price == 0.0) return

                    val isIndex = defaultIndices.any { it.ticker.uppercase() == symbol.uppercase() }
                    mainHandler.post {
                        if (isIndex) {
                            val idx = defaultIndices.first { it.ticker.uppercase() == symbol.uppercase() }
                            indexDataMap[idx.ticker] = IndexItem(
                                ticker        = idx.ticker,
                                displayName   = idx.displayName,
                                price         = price,
                                changePercent = if (changePct.isNaN()) null else changePct
                            )
                            pushIndicesToAdapter()
                        } else {
                            val existing = stockDataMap[symbol.uppercase()]
                            stockDataMap[symbol.uppercase()] = StockItem(
                                ticker        = symbol.uppercase(),
                                companyName   = existing?.companyName ?: symbol.uppercase(),
                                price         = price,
                                changePercent = if (changePct.isNaN()) null else changePct
                            )
                            pushWatchlistToAdapter()
                        }
                        updateTimestamp()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Quote parse error $symbol: ${e.message}")
                }
            }
        })
    }

    private fun fetchProfile(symbol: String) {
        val url = "$FINNHUB_PROFILE?symbol=${symbol.uppercase()}&token=$FINNHUB_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.use { it.body?.string() } ?: return
                try {
                    val name = JSONObject(body).optString("name", "").ifBlank { symbol.uppercase() }
                    mainHandler.post {
                        val existing = stockDataMap[symbol.uppercase()]
                        stockDataMap[symbol.uppercase()] =
                            (existing ?: StockItem(symbol.uppercase())).copy(companyName = name)
                        pushWatchlistToAdapter()
                    }
                } catch (e: Exception) {}
            }
        })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI updates
    // ──────────────────────────────────────────────────────────────────────────

    private fun pushWatchlistToAdapter() {
        val items = watchlistTickers.map { t -> stockDataMap[t] ?: StockItem(ticker = t) }
        stockAdapter.submitList(items)
        tvStockCount.text = "${items.size} stock${if (items.size == 1) "" else "s"}"
        layoutEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rvWatchlist.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun pushIndicesToAdapter() {
        indexAdapter.submitList(defaultIndices.map { idx -> indexDataMap[idx.ticker] ?: idx })
    }

    private fun updateTimestamp() {
        tvLastUpdated.text = "Updated ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}"
    }

    private fun String.capitalizeWords() =
        split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
}