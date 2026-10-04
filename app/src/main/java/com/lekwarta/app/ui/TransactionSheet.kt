package com.lekwarta.app.ui

import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lekwarta.app.R
import com.lekwarta.app.data.LeKwartaDb
import com.lekwarta.app.data.Money
import com.lekwarta.app.models.Account
import com.lekwarta.app.models.Transaction
import com.lekwarta.app.models.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Bottom sheet for logging an expense, income, or transfer.
 * Every entry is tied to an account, and saving updates that account's balance.
 */
class TransactionSheet(
    private val fragment: Fragment,
    private val userId: Int,
    private val initialDate: Calendar,
    private val onSaved: () -> Unit
) {
    private val ctx = fragment.requireContext()
    private val db = LeKwartaDb.get(ctx)
    private val dialog = BottomSheetDialog(ctx)
    private val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_transaction, null)

    private val typeViews = mapOf(
        Transaction.EXPENSE to view.findViewById<TextView>(R.id.typeExpense),
        Transaction.INCOME to view.findViewById<TextView>(R.id.typeIncome),
        Transaction.TRANSFER to view.findViewById<TextView>(R.id.typeTransfer)
    )
    private val etAmount: EditText = view.findViewById(R.id.etAmount)
    private val etDescription: EditText = view.findViewById(R.id.etDescription)
    private val layoutCategory: View = view.findViewById(R.id.layoutCategory)
    private val categoryRow: LinearLayout = view.findViewById(R.id.categoryRow)
    private val tvAccountLabel: TextView = view.findViewById(R.id.tvAccountLabel)
    private val spAccount: Spinner = view.findViewById(R.id.spAccount)
    private val layoutTransferTo: View = view.findViewById(R.id.layoutTransferTo)
    private val spToAccount: Spinner = view.findViewById(R.id.spToAccount)
    private val tvDate: TextView = view.findViewById(R.id.tvDate)
    private val tvError: TextView = view.findViewById(R.id.tvError)

    private var type = Transaction.EXPENSE
    private var category = TransactionCategory.FOOD
    private val date = (initialDate.clone() as Calendar).apply {
        // Keep the chosen day, but use the current time so the entry sorts naturally.
        val now = Calendar.getInstance()
        set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, now.get(Calendar.MINUTE))
    }
    private val accounts: List<Account> = db.getAccounts(userId)

    fun show() {
        typeViews.forEach { (t, tv) -> tv.setOnClickListener { setType(t) } }
        setType(type)
        buildCategories()

        val label: (Account) -> String = { "${it.name}  ·  ${Money.peso(it.balance)}" }
        spAccount.adapter = Ui.darkSpinnerAdapter(ctx, accounts, label)
        spToAccount.adapter = Ui.darkSpinnerAdapter(ctx, accounts, label)
        if (accounts.size > 1) spToAccount.setSelection(1)

        updateDateLabel()
        tvDate.setOnClickListener { pickDate() }
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        dialog.setContentView(view)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.setBackgroundResource(android.R.color.transparent)
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun setType(t: String) {
        type = t
        typeViews.forEach { (key, tv) ->
            val on = key == t
            tv.setBackgroundResource(if (on) R.drawable.bg_type_selected else R.drawable.bg_type)
            tv.setTextColor(ContextCompat.getColor(ctx, if (on) R.color.gold else R.color.text_secondary))
        }
        layoutCategory.visibility = if (t == Transaction.EXPENSE) View.VISIBLE else View.GONE
        layoutTransferTo.visibility = if (t == Transaction.TRANSFER) View.VISIBLE else View.GONE
        tvAccountLabel.text = when (t) {
            Transaction.TRANSFER -> "From account"
            Transaction.INCOME -> "Deposit to"
            else -> "Pay from"
        }
        tvError.visibility = View.GONE
    }

    private fun buildCategories() {
        categoryRow.removeAllViews()
        TransactionCategory.entries.forEach { cat ->
            val chip = TextView(ctx).apply {
                text = "${cat.icon} ${cat.displayName}"
                textSize = 13f
                val h = Ui.dp(ctx, 14); val v = Ui.dp(ctx, 7)
                setPadding(h, v, h, v)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = Ui.dp(ctx, 8) }
                setOnClickListener { category = cat; buildCategories() }
            }
            val on = cat == category
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chip.setTextColor(ContextCompat.getColor(ctx, if (on) R.color.bg_dark else R.color.text_primary))
            categoryRow.addView(chip)
        }
    }

    private fun pickDate() {
        DatePickerDialog(ctx, { _, y, m, d ->
            date.set(y, m, d)
            updateDateLabel()
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun updateDateLabel() {
        val today = Calendar.getInstance()
        val sameDay = today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
        tvDate.text = if (sameDay) "Today" else SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(date.time)
    }

    private fun save() {
        val amount = etAmount.text.toString().toDoubleOrNull()
        val description = etDescription.text.toString().trim()
        val from = accounts.getOrNull(spAccount.selectedItemPosition)
        val to = accounts.getOrNull(spToAccount.selectedItemPosition)

        val error = when {
            accounts.isEmpty() -> "Add an account first in the Accounts tab"
            amount == null || amount <= 0 -> "Enter an amount greater than ₱0"
            description.isEmpty() && type != Transaction.TRANSFER -> "Add a short description"
            from == null -> "Pick an account"
            type == Transaction.TRANSFER && (to == null || to.id == from.id) -> "Pick two different accounts"
            type != Transaction.INCOME && amount > from.balance ->
                "Not enough in ${from.name} (${Money.peso(from.balance)} left)"
            else -> null
        }
        if (error != null) {
            tvError.text = error
            tvError.visibility = View.VISIBLE
            return
        }

        db.addTransaction(Transaction(
            userId = userId,
            type = type,
            amount = amount!!,
            category = when (type) {
                Transaction.EXPENSE -> category.value
                Transaction.INCOME -> "income"
                else -> "transfer"
            },
            description = description.ifEmpty { "Transfer to ${to?.name}" },
            accountId = from!!.id,
            accountName = from.name,
            toAccountId = if (type == Transaction.TRANSFER) to?.id else null,
            date = date.time
        ))
        dialog.dismiss()
        onSaved()
    }
}
