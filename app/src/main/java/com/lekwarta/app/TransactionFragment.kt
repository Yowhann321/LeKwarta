// app/TransactionsFragment.kt
package com.lekwarta.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.lekwarta.app.adapters.TransactionAdapter
import com.lekwarta.app.models.Account
import com.lekwarta.app.models.Transaction
import com.lekwarta.app.models.TransactionCategory
import com.lekwarta.app.models.TransactionType
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionsFragment : Fragment() {

    private lateinit var rvRecentTransactions: RecyclerView
    private lateinit var rvPastTransactions: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpense: TextView
    private lateinit var tvNetBalance: TextView
    private lateinit var tvDateFilter: TextView
    private lateinit var layoutDateFilter: LinearLayout
    private lateinit var tvPastHeader: TextView
    private lateinit var pbTransactions: ProgressBar
    private lateinit var tvEmpty: TextView

    // Filter chips
    private lateinit var chipAll: TextView
    private lateinit var chipExpense: TextView
    private lateinit var chipIncome: TextView
    private lateinit var chipTransfer: TextView

    private var currentFilter = "all"
    private var selectedDate: Date? = null // null means "All Time"
    private var selectedDateString = "All Time"

    private val allTransactions = mutableListOf<Transaction>()
    private val recentTransactions = mutableListOf<Transaction>()
    private val pastTransactions = mutableListOf<Transaction>()

    private val recentTransactionAdapter = TransactionAdapter(
        onItemClick = { transaction: Transaction ->
            showTransactionDetails(transaction)
        },
        onItemLongClick = { transaction: Transaction ->
            showDeleteDialog(transaction)
        }
    )

    private val pastTransactionAdapter = TransactionAdapter(
        onItemClick = { transaction: Transaction ->
            showTransactionDetails(transaction)
        },
        onItemLongClick = { transaction: Transaction ->
            showDeleteDialog(transaction)
        }
    )

    // Hardcoded accounts for presentation
    private val accounts = listOf(
        Account(1, "Cash", "cash", 0.0),
        Account(2, "Bank Account", "bank", 0.0),
        Account(3, "GCash", "ewallet", 0.0),
        Account(4, "PayMaya", "ewallet", 0.0)
    )

    // API Endpoints
    private val API_GET_TRANSACTIONS = "${ApiClient.BASE_URL}/get_transactions.php"
    private val API_ADD_TRANSACTION = "${ApiClient.BASE_URL}/add_transaction.php"
    private val API_DELETE_TRANSACTION = "${ApiClient.BASE_URL}/delete_transaction.php"

    private val pesoFmt = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_transactions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupRecyclerViews()
        setupFilters()
        setupDateFilter()
        setupFab()

        fetchTransactionsFromDatabase()
    }

    private fun bindViews(view: View) {
        rvRecentTransactions = view.findViewById(R.id.rvRecentTransactions)
        rvPastTransactions = view.findViewById(R.id.rvPastTransactions)
        fabAdd = view.findViewById(R.id.fabAdd)
        tvTotalIncome = view.findViewById(R.id.tvTotalIncome)
        tvTotalExpense = view.findViewById(R.id.tvTotalExpense)
        tvNetBalance = view.findViewById(R.id.tvNetBalance)
        tvDateFilter = view.findViewById(R.id.tvDateFilter)
        layoutDateFilter = view.findViewById(R.id.layoutDateFilter)
        tvPastHeader = view.findViewById(R.id.tvPastHeader)
        pbTransactions = view.findViewById(R.id.pbTransactions)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        chipAll = view.findViewById(R.id.chipAll)
        chipExpense = view.findViewById(R.id.chipExpense)
        chipIncome = view.findViewById(R.id.chipIncome)
        chipTransfer = view.findViewById(R.id.chipTransfer)
    }

    private fun setupRecyclerViews() {
        rvRecentTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvRecentTransactions.adapter = recentTransactionAdapter
        rvRecentTransactions.setNestedScrollingEnabled(false)

        rvPastTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvPastTransactions.adapter = pastTransactionAdapter
        rvPastTransactions.setNestedScrollingEnabled(false)
    }

    private fun setupFilters() {
        chipAll.setOnClickListener { setFilter("all") }
        chipExpense.setOnClickListener { setFilter("expense") }
        chipIncome.setOnClickListener { setFilter("income") }
        chipTransfer.setOnClickListener { setFilter("transfer") }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter

        val chips = listOf(
            chipAll to "all",
            chipExpense to "expense",
            chipIncome to "income",
            chipTransfer to "transfer"
        )

        chips.forEach { (chip, value) ->
            if (value == filter) {
                chip.setTextColor(resources.getColor(R.color.bg_dark, null))
                chip.background = resources.getDrawable(R.drawable.bg_chip_active, null)
            } else {
                chip.setTextColor(resources.getColor(R.color.text_primary, null))
                chip.background = resources.getDrawable(R.drawable.bg_chip, null)
            }
        }

        filterAndDisplayTransactions()
    }

    private fun setupDateFilter() {
        layoutDateFilter.setOnClickListener { showDatePickerDialog() }
        updateDateFilterDisplay()
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        if (selectedDate != null) {
            calendar.time = selectedDate!!
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, year, month, dayOfMonth ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }.time
                selectedDateString = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate!!)
                updateDateFilterDisplay()
                filterAndDisplayTransactions()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Add "All Time" option
        datePickerDialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "All Time") { _, _ ->
            selectedDate = null
            selectedDateString = "All Time"
            updateDateFilterDisplay()
            filterAndDisplayTransactions()
        }

        datePickerDialog.setOnShowListener {
            datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.green_primary, null))
            datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.text_hint, null))
            datePickerDialog.getButton(DatePickerDialog.BUTTON_NEUTRAL).setTextColor(resources.getColor(R.color.amber, null))
        }

        datePickerDialog.show()
    }

    private fun updateDateFilterDisplay() {
        tvDateFilter.text = selectedDateString
    }

    private fun setupFab() {
        fabAdd.setOnClickListener { showAddTransactionDialog() }
    }

    private fun fetchTransactionsFromDatabase() {
        pbTransactions.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val token = SessionManager.getToken(requireContext())

        if (token == null) {
            pbTransactions.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Please log in to view transactions"
            return
        }

        val userId = SessionManager.getUserId(requireContext())

        // If date is selected, filter by that date, otherwise get all
        val startTimestamp = if (selectedDate != null) selectedDate!!.time.div(1000) else 0
        val endTimestamp = if (selectedDate != null) startTimestamp else 9999999999L

        val url = "$API_GET_TRANSACTIONS?user_id=$userId&start_date=$startTimestamp&end_date=$endTimestamp&type=all"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    pbTransactions.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Network error: ${e.message}"
                    Toast.makeText(requireContext(), "Failed to load transactions", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    activity?.runOnUiThread {
                        pbTransactions.visibility = View.GONE
                        tvEmpty.visibility = View.VISIBLE
                        tvEmpty.text = "Empty response from server"
                    }
                    return
                }

                activity?.runOnUiThread {
                    pbTransactions.visibility = View.GONE
                    try {
                        val json = JSONObject(body)
                        if (json.getBoolean("success")) {
                            parseTransactionsFromDatabase(json.getJSONArray("transactions"))
                        } else {
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = json.getString("message")
                        }
                    } catch (e: Exception) {
                        tvEmpty.visibility = View.VISIBLE
                        tvEmpty.text = "Error parsing data"
                        Toast.makeText(requireContext(), "Error parsing data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun parseTransactionsFromDatabase(arr: JSONArray) {
        allTransactions.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val date = Date(obj.getLong("date") * 1000)
            allTransactions.add(
                Transaction(
                    id = obj.getInt("id"),
                    userId = obj.getInt("user_id"),
                    type = obj.getString("type"),
                    amount = obj.getDouble("amount"),
                    category = obj.getString("category"),
                    description = obj.getString("description"),
                    accountId = obj.getInt("account_id"),
                    accountName = obj.getString("account_name"),
                    date = date
                )
            )
        }
        filterAndDisplayTransactions()
    }

    private fun filterAndDisplayTransactions() {
        // Filter by type
        val filteredByType = allTransactions.filter { transaction ->
            when (currentFilter) {
                "all" -> true
                else -> transaction.type == currentFilter
            }
        }

        // Filter by date if selected
        val filteredByDate = if (selectedDate != null) {
            filteredByType.filter { transaction ->
                val calendar = Calendar.getInstance()
                calendar.time = transaction.date
                val selectedCal = Calendar.getInstance().apply { time = selectedDate!! }

                calendar.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                        calendar.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH) &&
                        calendar.get(Calendar.DAY_OF_MONTH) == selectedCal.get(Calendar.DAY_OF_MONTH)
            }
        } else {
            filteredByType
        }

        // Split into recent (last 5) and past
        val today = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // Transactions from today or recent (last 5)
        val recentList = filteredByDate.filter { it.date >= todayStart }.take(5)
        val remainingList = filteredByDate.filter { it.date < todayStart || (it.date >= todayStart && !recentList.contains(it)) }

        recentTransactions.clear()
        recentTransactions.addAll(recentList.sortedByDescending { it.date })

        pastTransactions.clear()
        pastTransactions.addAll(remainingList.sortedByDescending { it.date })

        recentTransactionAdapter.submitList(recentTransactions)
        pastTransactionAdapter.submitList(pastTransactions)

        // Show/hide past header
        tvPastHeader.visibility = if (pastTransactions.isNotEmpty()) View.VISIBLE else View.GONE

        // Update summary
        updateSummary(filteredByDate)

        // Show empty state
        if (filteredByDate.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvRecentTransactions.visibility = View.GONE
            rvPastTransactions.visibility = View.GONE
            tvPastHeader.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvRecentTransactions.visibility = if (recentTransactions.isNotEmpty()) View.VISIBLE else View.GONE
            rvPastTransactions.visibility = if (pastTransactions.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateSummary(transactions: List<Transaction>) {
        val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        tvTotalIncome.text = "₱${pesoFmt.format(totalIncome)}"
        tvTotalExpense.text = "₱${pesoFmt.format(totalExpense)}"
        tvNetBalance.text = "₱${pesoFmt.format(netBalance)}"

        tvNetBalance.setTextColor(
            if (netBalance >= 0) {
                resources.getColor(R.color.green_primary, null)
            } else {
                resources.getColor(R.color.red_soft, null)
            }
        )
    }

    // ==================== ADD TRANSACTION DIALOG ====================

    private fun showAddTransactionDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_transaction, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Type selection
        val typeExpense = dialogView.findViewById<TextView>(R.id.typeExpense)
        val typeIncome = dialogView.findViewById<TextView>(R.id.typeIncome)
        val typeTransfer = dialogView.findViewById<TextView>(R.id.typeTransfer)

        // Dynamic layouts
        val layoutCategory = dialogView.findViewById<LinearLayout>(R.id.layoutCategory)
        val layoutTransfer = dialogView.findViewById<LinearLayout>(R.id.layoutTransfer)
        val layoutAccount = dialogView.findViewById<LinearLayout>(R.id.layoutAccount)

        // Form fields
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerAccount = dialogView.findViewById<Spinner>(R.id.spinnerAccount)
        val spinnerFromAccount = dialogView.findViewById<Spinner>(R.id.spinnerFromAccount)
        val spinnerToAccount = dialogView.findViewById<Spinner>(R.id.spinnerToAccount)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDate)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave)
        val pbSave = dialogView.findViewById<ProgressBar>(R.id.pbSave)

        var selectedType = TransactionType.EXPENSE
        var selectedTransactionDate = Date()

        // Setup category spinner
        val categories = listOf(
            TransactionCategory.FOOD,
            TransactionCategory.TRANSPORT,
            TransactionCategory.SHOPPING,
            TransactionCategory.BILLS,
            TransactionCategory.HEALTH,
            TransactionCategory.ENTERTAINMENT,
            TransactionCategory.OTHER
        )

        val categoryNames = categories.map { "${it.icon} ${it.displayName}" }.toTypedArray()
        val categoryAdapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categoryNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    setPadding(12, 12, 12, 12)
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setPadding(12, 12, 12, 12)
                }
                return view
            }
        }
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        // Setup account spinners
        val accountNames = accounts.map { it.name }.toTypedArray()

        val textColorAdapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            accountNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    setPadding(12, 12, 12, 12)
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    setBackgroundColor(resources.getColor(R.color.card_bg, null))
                    setPadding(12, 12, 12, 12)
                }
                return view
            }
        }
        textColorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerAccount.adapter = textColorAdapter
        spinnerFromAccount.adapter = textColorAdapter
        spinnerToAccount.adapter = textColorAdapter

        // Setup type selection
        val updateTypeUI = { type: TransactionType ->
            selectedType = type

            typeExpense.background = resources.getDrawable(R.drawable.bg_type, null)
            typeExpense.setTextColor(resources.getColor(R.color.text_secondary, null))
            typeIncome.background = resources.getDrawable(R.drawable.bg_type, null)
            typeIncome.setTextColor(resources.getColor(R.color.text_secondary, null))
            typeTransfer.background = resources.getDrawable(R.drawable.bg_type, null)
            typeTransfer.setTextColor(resources.getColor(R.color.text_secondary, null))

            when (type) {
                TransactionType.EXPENSE -> {
                    typeExpense.background = resources.getDrawable(R.drawable.bg_type_selected, null)
                    typeExpense.setTextColor(resources.getColor(R.color.bg_dark, null))
                    layoutCategory.visibility = View.VISIBLE
                    layoutTransfer.visibility = View.GONE
                    layoutAccount.visibility = View.VISIBLE
                }
                TransactionType.INCOME -> {
                    typeIncome.background = resources.getDrawable(R.drawable.bg_type_selected, null)
                    typeIncome.setTextColor(resources.getColor(R.color.bg_dark, null))
                    layoutCategory.visibility = View.GONE
                    layoutTransfer.visibility = View.GONE
                    layoutAccount.visibility = View.VISIBLE
                }
                TransactionType.TRANSFER -> {
                    typeTransfer.background = resources.getDrawable(R.drawable.bg_type_selected, null)
                    typeTransfer.setTextColor(resources.getColor(R.color.bg_dark, null))
                    layoutCategory.visibility = View.GONE
                    layoutTransfer.visibility = View.VISIBLE
                    layoutAccount.visibility = View.GONE
                }
            }
        }

        typeExpense.setOnClickListener { updateTypeUI(TransactionType.EXPENSE) }
        typeIncome.setOnClickListener { updateTypeUI(TransactionType.INCOME) }
        typeTransfer.setOnClickListener { updateTypeUI(TransactionType.TRANSFER) }

        // Setup date picker
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        tvDate.text = dateFormat.format(selectedTransactionDate)
        tvDate.setTextColor(resources.getColor(R.color.green_primary, null))

        tvDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.time = selectedTransactionDate

            val datePickerDialog = DatePickerDialog(
                requireContext(),
                android.R.style.Theme_DeviceDefault_Dialog_Alert,
                { _, year, month, day ->
                    selectedTransactionDate = Calendar.getInstance().apply {
                        set(year, month, day)
                    }.time
                    tvDate.text = dateFormat.format(selectedTransactionDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.setOnShowListener {
                datePickerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.green_primary, null))
                datePickerDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.text_hint, null))
            }

            datePickerDialog.show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            val description = etDescription.text.toString().trim()

            if (amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(requireContext(), "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            pbSave.visibility = View.VISIBLE

            when (selectedType) {
                TransactionType.EXPENSE -> {
                    val selectedAccount = accounts[spinnerAccount.selectedItemPosition]
                    val selectedCategory = categories[spinnerCategory.selectedItemPosition]

                    addTransactionToDatabase(
                        type = "expense",
                        amount = amount,
                        description = description,
                        category = selectedCategory.value,
                        accountId = selectedAccount.id,
                        date = selectedTransactionDate,
                        dialog = dialog,
                        pbSave = pbSave,
                        btnSave = btnSave
                    )
                }

                TransactionType.INCOME -> {
                    val selectedAccount = accounts[spinnerAccount.selectedItemPosition]

                    addTransactionToDatabase(
                        type = "income",
                        amount = amount,
                        description = description,
                        category = "income",
                        accountId = selectedAccount.id,
                        date = selectedTransactionDate,
                        dialog = dialog,
                        pbSave = pbSave,
                        btnSave = btnSave
                    )
                }

                TransactionType.TRANSFER -> {
                    val fromAccount = accounts[spinnerFromAccount.selectedItemPosition]
                    val toAccount = accounts[spinnerToAccount.selectedItemPosition]

                    if (fromAccount.id == toAccount.id) {
                        pbSave.visibility = View.GONE
                        btnSave.isEnabled = true
                        Toast.makeText(requireContext(), "Cannot transfer to the same account", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    addTransferToDatabase(
                        amount = amount,
                        description = description,
                        fromAccountId = fromAccount.id,
                        toAccountId = toAccount.id,
                        date = selectedTransactionDate,
                        dialog = dialog,
                        pbSave = pbSave,
                        btnSave = btnSave
                    )
                }
            }
        }

        dialog.show()
    }

    private fun addTransactionToDatabase(
        type: String,
        amount: Double,
        description: String,
        category: String,
        accountId: Int,
        date: Date,
        dialog: AlertDialog,
        pbSave: ProgressBar,
        btnSave: MaterialButton
    ) {
        val token = SessionManager.getToken(requireContext())

        if (token == null) {
            pbSave.visibility = View.GONE
            btnSave.isEnabled = true
            Toast.makeText(requireContext(), "Please log in", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = SessionManager.getUserId(requireContext())

        val json = JSONObject().apply {
            put("user_id", userId)
            put("type", type)
            put("amount", amount)
            put("description", description)
            put("category", category)
            put("account_id", accountId)
            put("date", date.time / 1000)
        }

        val JSON = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url(API_ADD_TRANSACTION)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    pbSave.visibility = View.GONE
                    btnSave.isEnabled = true
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    activity?.runOnUiThread {
                        pbSave.visibility = View.GONE
                        btnSave.isEnabled = true
                        Toast.makeText(requireContext(), "Empty response", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                activity?.runOnUiThread {
                    pbSave.visibility = View.GONE
                    btnSave.isEnabled = true
                    try {
                        val jsonResp = JSONObject(body)
                        if (jsonResp.getBoolean("success")) {
                            dialog.dismiss()
                            Toast.makeText(requireContext(), "${type.capitalize()} added!", Toast.LENGTH_SHORT).show()
                            fetchTransactionsFromDatabase()
                        } else {
                            Toast.makeText(requireContext(), jsonResp.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error saving transaction", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun addTransferToDatabase(
        amount: Double,
        description: String,
        fromAccountId: Int,
        toAccountId: Int,
        date: Date,
        dialog: AlertDialog,
        pbSave: ProgressBar,
        btnSave: MaterialButton
    ) {
        // First add withdrawal transaction (expense)
        addTransactionToDatabase(
            type = "expense",
            amount = amount,
            description = "Transfer out: $description",
            category = "transfer",
            accountId = fromAccountId,
            date = date,
            dialog = dialog,
            pbSave = pbSave,
            btnSave = btnSave
        )

        // Then add deposit transaction (income)
        addTransactionToDatabase(
            type = "income",
            amount = amount,
            description = "Transfer in: $description",
            category = "transfer",
            accountId = toAccountId,
            date = date,
            dialog = dialog,
            pbSave = pbSave,
            btnSave = btnSave
        )
    }

    // ==================== TRANSACTION ACTIONS ====================

    private fun showTransactionDetails(transaction: Transaction) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        AlertDialog.Builder(requireContext())
            .setTitle("Transaction Details")
            .setMessage("""
                Amount: ₱${pesoFmt.format(transaction.amount)}
                Type: ${transaction.type.uppercase()}
                Category: ${transaction.category}
                Description: ${transaction.description}
                Account: ${transaction.accountName}
                Date: ${dateFormat.format(transaction.date)}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteDialog(transaction: Transaction) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Delete \"${transaction.description}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTransactionFromDatabase(transaction.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTransactionFromDatabase(transactionId: Int) {
        val token = SessionManager.getToken(requireContext())

        if (token == null) {
            Toast.makeText(requireContext(), "Please log in", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = SessionManager.getUserId(requireContext())

        val request = Request.Builder()
            .url("$API_DELETE_TRANSACTION?id=$transactionId&user_id=$userId")
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                activity?.runOnUiThread {
                    try {
                        val json = JSONObject(body)
                        if (json.getBoolean("success")) {
                            Toast.makeText(requireContext(), "Transaction deleted", Toast.LENGTH_SHORT).show()
                            fetchTransactionsFromDatabase()
                        } else {
                            Toast.makeText(requireContext(), json.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error deleting transaction", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}