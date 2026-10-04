package com.lekwarta.app

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.lekwarta.app.data.Money

class CalculatorFragment : Fragment() {

    private lateinit var tabSimple: TextView
    private lateinit var tabCompound: TextView
    private lateinit var tabLoan: TextView
    private lateinit var tvCalcHint: TextView
    private lateinit var etPrincipal: EditText
    private lateinit var etRate: EditText
    private lateinit var etTime: EditText
    private lateinit var layoutTime: View
    private lateinit var etExtraLabel: TextView
    private lateinit var etExtra: EditText
    private lateinit var layoutExtra: View
    private lateinit var btnCalculate: MaterialButton
    private lateinit var tvResultLabel: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvResultSub: TextView

    private var currentTab = "simple"

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_calculator, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabSimple     = view.findViewById(R.id.tabSimple)
        tabCompound   = view.findViewById(R.id.tabCompound)
        tabLoan       = view.findViewById(R.id.tabLoan)
        tvCalcHint    = view.findViewById(R.id.tvCalcHint)
        etPrincipal   = view.findViewById(R.id.etPrincipal)
        etRate        = view.findViewById(R.id.etRate)
        etTime        = view.findViewById(R.id.etTime)
        layoutTime    = view.findViewById(R.id.layoutTime)
        etExtraLabel  = view.findViewById(R.id.tvExtraLabel)
        etExtra       = view.findViewById(R.id.etExtra)
        layoutExtra   = view.findViewById(R.id.layoutExtra)
        btnCalculate  = view.findViewById(R.id.btnCalculate)
        tvResultLabel = view.findViewById(R.id.tvResultLabel)
        tvResult      = view.findViewById(R.id.tvResult)
        tvResultSub   = view.findViewById(R.id.tvResultSub)

        tabSimple.setOnClickListener   { switchTab("simple") }
        tabCompound.setOnClickListener { switchTab("compound") }
        tabLoan.setOnClickListener     { switchTab("loan") }
        btnCalculate.setOnClickListener { calculate() }

        // Sensible starting numbers so one tap shows a result
        etPrincipal.setText("10000")
        etRate.setText("5")
        etTime.setText("2")
        etExtra.setText("12")

        switchTab("simple")
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        listOf(tabSimple to "simple", tabCompound to "compound", tabLoan to "loan")
            .forEach { (tv, t) ->
                val on = t == tab
                tv.setBackgroundResource(if (on) R.drawable.bg_type_selected else R.drawable.bg_type)
                tv.setTextColor(resources.getColor(if (on) R.color.gold else R.color.text_secondary, null))
            }

        when (tab) {
            "simple" -> {
                tvCalcHint.text = "Interest on the principal only: P × r × t"
                tvResultLabel.text = "Total interest"
            }
            "compound" -> {
                tvCalcHint.text = "Interest compounded monthly: P(1 + r/12)^(12t)"
                tvResultLabel.text = "Total amount"
            }
            "loan" -> {
                tvCalcHint.text = "Fixed monthly payment that pays off the loan"
                etExtraLabel.text = "Months"
                tvResultLabel.text = "Monthly payment"
            }
        }
        // Loans are measured in months, so the years field is hidden.
        layoutTime.visibility = if (tab == "loan") View.GONE else View.VISIBLE
        layoutExtra.visibility = if (tab == "loan") View.VISIBLE else View.GONE
        tvResult.text    = "₱0.00"
        tvResultSub.text = ""
    }

    private fun calculate() {
        val principal = etPrincipal.text.toString().replace(",", "").toDoubleOrNull()
        val rate      = etRate.text.toString().toDoubleOrNull()
        val time      = etTime.text.toString().toDoubleOrNull()

        if (principal == null || rate == null || (currentTab != "loan" && time == null)) {
            Toast.makeText(requireContext(), "Fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        when (currentTab) {
            "simple" -> {
                val interest = principal * (rate / 100) * time!!
                val total    = principal + interest
                tvResult.text    = Money.pesoCents(interest)
                tvResultSub.text = "Total amount: ${Money.pesoCents(total)}"
            }
            "compound" -> {
                val n     = 12.0
                val total = principal * Math.pow(1 + (rate / 100) / n, n * time!!)
                val interest = total - principal
                tvResult.text    = Money.pesoCents(total)
                tvResultSub.text = "Interest earned: ${Money.pesoCents(interest)}"
            }
            "loan" -> {
                val months = etExtra.text.toString().toDoubleOrNull()
                if (months == null || months <= 0) {
                    Toast.makeText(requireContext(), "Enter the number of months", Toast.LENGTH_SHORT).show()
                    return
                }
                val monthlyRate = (rate / 100) / 12
                val payment = if (monthlyRate == 0.0) principal / months
                else principal * monthlyRate * Math.pow(1 + monthlyRate, months) /
                        (Math.pow(1 + monthlyRate, months) - 1)
                val totalPaid = payment * months
                tvResult.text    = Money.pesoCents(payment)
                tvResultSub.text = "Total paid: ${Money.pesoCents(totalPaid)}\nTotal interest: ${Money.pesoCents(totalPaid - principal)}"
            }
        }
    }
}
