package com.lekwarta.app.ui

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lekwarta.app.R

/** Small UI helpers shared by the fragments. */
object Ui {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    /** Spinner adapter with light text on the dark card background. */
    fun <T> darkSpinnerAdapter(context: Context, items: List<T>, label: (T) -> String): ArrayAdapter<T> =
        object : ArrayAdapter<T>(context, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getView(position, convertView, parent) as TextView, getItem(position)!!, false)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getDropDownView(position, convertView, parent) as TextView, getItem(position)!!, true)

            private fun style(tv: TextView, item: T, dropDown: Boolean): TextView {
                tv.text = label(item)
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                tv.textSize = 15f
                val pad = dp(context, if (dropDown) 14 else 8)
                tv.setPadding(pad, pad, pad, pad)
                return tv
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    fun confirm(context: Context, title: String, message: String, action: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Asks for a peso amount, e.g. the daily budget. */
    fun askAmount(context: Context, title: String, current: Double, onSave: (Double) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (current % 1.0 == 0.0) current.toLong().toString() else current.toString())
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(context, R.color.gold))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_hint))
            hint = "₱0"
        }
        val box = FrameLayout(context).apply {
            val pad = dp(context, 22)
            setPadding(pad, dp(context, 8), pad, 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().toDoubleOrNull()?.takeIf { it >= 0 }?.let(onSave)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
