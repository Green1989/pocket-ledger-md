package com.example.pocketledgermd.data

import java.math.BigDecimal
import java.time.LocalDateTime

enum class EntryType {
    EXPENSE,
    INCOME,
}

data class LedgerEntry(
    val dateTime: LocalDateTime,
    val type: EntryType,
    val amount: BigDecimal,
    val category: String,
    val note: String,
)

data class MonthSummary(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val balance: BigDecimal,
)
