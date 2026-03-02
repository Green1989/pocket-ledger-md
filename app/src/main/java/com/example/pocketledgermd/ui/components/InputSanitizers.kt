package com.example.pocketledgermd.ui

internal fun sanitizeAmountInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val normalized = if (filtered.startsWith(".")) "0$filtered" else filtered
    val parts = normalized.split('.', limit = 3)
    return when {
        parts.size == 1 -> parts[0]
        parts.size >= 2 -> "${parts[0]}.${parts[1].take(2)}"
        else -> ""
    }
}
