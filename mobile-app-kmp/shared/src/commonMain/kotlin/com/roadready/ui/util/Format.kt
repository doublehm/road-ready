package com.roadready.ui.util

import kotlin.math.pow
import kotlin.math.roundToInt

/** KMP-safe double formatting to [decimals] decimal places. */
fun fmtDouble(value: Double, decimals: Int = 1): String {
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    val parts = rounded.toString().split(".")
    val intPart = parts[0]
    val fracPart = (parts.getOrElse(1) { "" }).take(decimals).padEnd(decimals, '0')
    return "$intPart.$fracPart"
}

/** Format as dollar amount: "$1234.56" */
fun fmtDollar(value: Double): String = "$${fmtDouble(value, 2)}"

/** Zero-padded two-digit number, e.g. 5 → "05" */
fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"
