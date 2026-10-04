package com.lekwarta.app

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.util.Locale

class CalculatorFragment : Fragment() {

    private lateinit var tabSimple: TextView
    private lateinit var tabCompound: TextView
    private lateinit var tabLoan: TextView
    private lateinit var etPrincipal: TextInputEditText
    private lateinit var etRate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var etExtraLabel: TextView
    private lateinit var etExtra: TextInputEditText
    private lateinit var layoutExtra: View
    private lateinit var btnCalculate: MaterialButton
    private lateinit var tvResultLabel: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvResultSub: TextView

    private var currentTab = "simple"
    private val pesoFmt = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_calculator, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabSimple    = view.findViewById(R.id.tabSimple)
        tabCompound  = view.findViewById(R.id.tabCompound)
        tabLoan      = view.findViewById(R.id.tabLoan)
        etPrincipal  = view.findViewById(R.id.etPrincipal)
        etRate       = view.findViewById(R.id.etRate)
        etTime       = view.findViewById(R.id.etTime)
        etExtraLabel = view.findViewById(R.id.tvExtraLabel)
        etExtra      = view.findViewById(R.id.etExtra)
        layoutExtra  = view.findViewById(R.id.layoutExtra)
        btnCalculate = view.findViewById(R.id.btnCalculate)
        tvResultLabel = view.findViewById(R.id.tvResultLabel)
        tvResult     = view.findViewById(R.id.tvResult)
        tvResultSub  = view.findViewById(R.id.tvResultSub)

        tabSimple.setOnClickListener   { switchTab("simple") }
        tabCompound.setOnClickListener { switchTab("compound") }
        tabLoan.setOnClickListener     { switchTab("loan") }
        btnCalculate.setOnClickListener { calculate() }

        switchTab("simple")
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        val active   = resources.getColor(R.color.green_primary, null)
        val inactive = resources.getColor(R.color.text_hint, null)
        val activeBg   = resources.getColor(R.color.green_dark, null)
        val inactiveBg = resources.getColor(R.color.card_bg, null)

        listOf(tabSimple to "simple", tabCompound to "compound", tabLoan to "loan")
            .forEach { (tv, t) ->
                tv.setTextColor(if (t == tab) active else inactive)
                tv.setBackgroundColor(if (t == tab) activeBg else inactiveBg)
            }

        when (tab) {
            "simple", "compound" -> {
                layoutExtra.visibility = View.GONE
                tvResultLabel.text = if (tab == "simple") "Total Interest" else "Total Amount"
            }
            "loan" -> {
                layoutExtra.visibility = View.VISIBLE
                etExtraLabel.text = "Months"
                tvResultLabel.text = "Monthly Payment"
            }
        }
        tvResult.text    = "₱0.00"
        tvResultSub.text = ""
    }

    private fun calculate() {
        val principal = etPrincipal.text.toString().toDoubleOrNull()
        val rate      = etRate.text.toString().toDoubleOrNull()
        val time      = etTime.text.toString().toDoubleOrNull()

        if (principal == null || rate == null || time == null) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        when (currentTab) {
            "simple" -> {
                val interest = principal * (rate / 100) * time
                val total    = principal + interest
                tvResult.text    = "₱${pesoFmt.format(interest)}"
                tvResultSub.text = "Total amount: ₱${pesoFmt.format(total)}"
            }
            "compound" -> {
                val n     = 12.0
                val total = principal * Math.pow(1 + (rate / 100) / n, n * time)
                val interest = total - principal
                tvResult.text    = "₱${pesoFmt.format(total)}"
                tvResultSub.text = "Interest earned: ₱${pesoFmt.format(interest)}"
            }
            "loan" -> {
                val months      = etExtra.text.toString().toDoubleOrNull()
                if (months == null) {
                    Toast.makeText(requireContext(), "Please enter number of months", Toast.LENGTH_SHORT).show()
                    return
                }
                val monthlyRate = (rate / 100) / 12
                val payment = if (monthlyRate == 0.0) principal / months
                else principal * monthlyRate * Math.pow(1 + monthlyRate, months) /
                        (Math.pow(1 + monthlyRate, months) - 1)
                val totalPaid = payment * months
                tvResult.text    = "₱${pesoFmt.format(payment)}"
                tvResultSub.text = "Total paid: ₱${pesoFmt.format(totalPaid)}"
            }
        }
    }
}