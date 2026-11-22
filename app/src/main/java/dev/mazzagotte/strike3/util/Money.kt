package dev.mazzagotte.strike3.util

import java.text.NumberFormat
import java.util.Locale

/** "$45", "$1,200", "$12.50" */
fun money(v: Double): String {
    val f = NumberFormat.getCurrencyInstance(Locale.US)
    if (v % 1.0 == 0.0) f.maximumFractionDigits = 0
    return f.format(v)
}
