package com.lekwarta.app

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.lekwarta.app.data.LeKwartaDb
import com.lekwarta.app.data.Money
import com.lekwarta.app.models.Account
import com.lekwarta.app.ui.Ui

/**
 * Accounts: the user's wallets and banks. Each one holds a balance that transactions
 * deduct from or add to. Balances can also be reset by hand.
 */
class AccountsFragment : Fragment() {

    private lateinit var tvNetWorth: TextView
    private lateinit var tvAccountCount: TextView
    private lateinit var rvAccounts: RecyclerView

    private val db by lazy { LeKwartaDb.get(requireContext()) }
    private val adapter = AccountAdapter()
    private var userId = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_accounts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvNetWorth = view.findViewById(R.id.tvNetWorth)
        tvAccountCount = view.findViewById(R.id.tvAccountCount)
        rvAccounts = view.findViewById(R.id.rvAccounts)
        rvAccounts.adapter = adapter
        userId = SessionManager.getUserId(requireContext())

        view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddAccount).setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val accounts = db.getAccounts(userId)
        adapter.submit(accounts)
        tvNetWorth.text = Money.peso(accounts.sumOf { it.balance })
        tvAccountCount.text = "${accounts.size} account${if (accounts.size == 1) "" else "s"}"
    }

    // ─── Add / edit sheet ─────────────────────────────────────────────────────
    private fun openEditor(existing: Account?) {
        val ctx = requireContext()
        val sheet = BottomSheetDialog(ctx)
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_account, null)

        val etName: EditText = v.findViewById(R.id.etAccountName)
        val etBalance: EditText = v.findViewById(R.id.etAccountBalance)
        val tvError: TextView = v.findViewById(R.id.tvAccountError)
        val colorRow: LinearLayout = v.findViewById(R.id.colorRow)
        val typeViews = mapOf(
            Account.TYPE_CASH to v.findViewById<TextView>(R.id.acctTypeCash),
            Account.TYPE_BANK to v.findViewById<TextView>(R.id.acctTypeBank),
            Account.TYPE_EWALLET to v.findViewById<TextView>(R.id.acctTypeEwallet)
        )

        var type = existing?.type ?: Account.TYPE_BANK
        var color = existing?.color ?: Account.COLORS[0]

        v.findViewById<TextView>(R.id.tvAccountSheetTitle).text = if (existing == null) "New account" else "Edit account"
        v.findViewById<TextView>(R.id.tvBalanceLabel).text = if (existing == null) "Starting balance" else "Reset balance to"
        existing?.let {
            etName.setText(it.name)
            etBalance.setText(if (it.balance % 1.0 == 0.0) it.balance.toLong().toString() else "%.2f".format(it.balance))
        }

        fun renderType() = typeViews.forEach { (key, tv) ->
            val on = key == type
            tv.setBackgroundResource(if (on) R.drawable.bg_type_selected else R.drawable.bg_type)
            tv.setTextColor(ContextCompat.getColor(ctx, if (on) R.color.gold else R.color.text_secondary))
        }
        typeViews.forEach { (key, tv) -> tv.setOnClickListener { type = key; renderType() } }
        renderType()

        fun renderColors() {
            colorRow.removeAllViews()
            Account.COLORS.forEach { c ->
                colorRow.addView(View(ctx).apply {
                    val size = Ui.dp(ctx, 38)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = Ui.dp(ctx, 10) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        if (c == color) setStroke(Ui.dp(ctx, 3), ContextCompat.getColor(ctx, R.color.gold))
                    }
                    contentDescription = "Card color"
                    setOnClickListener { color = c; renderColors() }
                })
            }
        }
        renderColors()

        v.findViewById<MaterialButton>(R.id.btnCancelAccount).setOnClickListener { sheet.dismiss() }
        v.findViewById<MaterialButton>(R.id.btnSaveAccount).setOnClickListener {
            val name = etName.text.toString().trim()
            val balance = etBalance.text.toString().ifBlank { "0" }.toDoubleOrNull()
            val error = when {
                name.isEmpty() -> "Give the account a name"
                balance == null || balance < 0 -> "Enter a balance of ₱0 or more"
                else -> null
            }
            if (error != null) {
                tvError.text = error
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (existing == null) {
                db.addAccount(userId, name, type, balance!!, color)
                toast("$name added")
            } else {
                db.updateAccount(existing.id, name, type, balance!!, color)
                toast("$name updated")
            }
            sheet.dismiss()
            refresh()
        }

        if (existing != null) {
            v.findViewById<MaterialButton>(R.id.btnDeleteAccount).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val count = db.countTransactionsForAccount(existing.id)
                    val msg = if (count == 0) "This can't be undone."
                    else "This also deletes its $count transaction${if (count == 1) "" else "s"}. This can't be undone."
                    Ui.confirm(ctx, "Delete ${existing.name}?", msg, "Delete") {
                        db.deleteAccount(existing.id)
                        toast("${existing.name} deleted")
                        sheet.dismiss()
                        refresh()
                    }
                }
            }
        }

        sheet.setContentView(v)
        sheet.setOnShowListener {
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.setBackgroundResource(android.R.color.transparent)
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        sheet.show()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    // ─── Wallet cards ─────────────────────────────────────────────────────────
    inner class AccountAdapter : RecyclerView.Adapter<AccountAdapter.VH>() {
        private var items: List<Account> = emptyList()

        fun submit(list: List<Account>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val root: View = view.findViewById(R.id.cardRoot)
            val circle: View = view.findViewById(R.id.decorCircle)
            val tvType: TextView = view.findViewById(R.id.tvAccountType)
            val tvIcon: TextView = view.findViewById(R.id.tvAccountIcon)
            val tvName: TextView = view.findViewById(R.id.tvAccountName)
            val tvBalance: TextView = view.findViewById(R.id.tvAccountBalance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_account_card, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val a = items[position]
            val ctx = holder.itemView.context
            holder.root.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(a.color, ColorUtils.blendARGB(a.color, 0xFF000000.toInt(), 0.35f))
            ).apply { cornerRadius = Ui.dp(ctx, 20).toFloat() }
            holder.circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(Ui.dp(ctx, 2), 0x33FFFFFF)
            }
            holder.tvType.text = Account.typeLabel(a.type).uppercase()
            holder.tvIcon.text = when (a.type) {
                Account.TYPE_CASH -> "💵"
                Account.TYPE_EWALLET -> "📱"
                else -> "🏦"
            }
            holder.tvName.text = a.name
            holder.tvBalance.text = Money.pesoCents(a.balance)
            holder.itemView.setOnClickListener { openEditor(a) }
        }
    }
}
