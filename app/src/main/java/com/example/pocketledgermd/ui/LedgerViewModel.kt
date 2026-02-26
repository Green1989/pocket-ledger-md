package com.example.pocketledgermd.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import com.example.pocketledgermd.data.MarkdownRepository
import com.example.pocketledgermd.data.MonthSummary
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class DateFilter {
    MONTH,
    WEEK,
    TODAY,
}

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MarkdownRepository(app.applicationContext)

    private val expenseCategories = listOf("餐饮", "交通", "购物", "日用", "娱乐", "医疗", "住房", "其他支出")
    private val incomeCategories = listOf("工资", "奖金", "报销", "投资收益", "退款", "其他收入")
    val availableCategories: List<String>
        get() = if (selectedType == EntryType.EXPENSE) expenseCategories else incomeCategories

    var amountInput by mutableStateOf("")
    var noteInput by mutableStateOf("")
    var selectedType by mutableStateOf(EntryType.EXPENSE)
    var selectedCategory by mutableStateOf(expenseCategories.first())
    var statusMessage by mutableStateOf("")
    var selectedMonth by mutableStateOf(YearMonth.now())
    var selectedFilter by mutableStateOf(DateFilter.MONTH)

    private val monthEntries = mutableStateListOf<LedgerEntry>()
    val entries = mutableStateListOf<LedgerEntry>()

    var summary by mutableStateOf(
        MonthSummary(
            totalIncome = BigDecimal.ZERO,
            totalExpense = BigDecimal.ZERO,
            balance = BigDecimal.ZERO,
        )
    )

    init {
        reloadSelectedMonth()
    }

    fun reloadSelectedMonth() {
        val data = repository.loadMonth(selectedMonth)
        monthEntries.clear()
        monthEntries.addAll(data)
        applyFilter()
    }

    fun previousMonth() {
        selectedMonth = selectedMonth.minusMonths(1)
        reloadSelectedMonth()
    }

    fun nextMonth() {
        selectedMonth = selectedMonth.plusMonths(1)
        reloadSelectedMonth()
    }

    fun updateFilter(filter: DateFilter) {
        selectedFilter = filter
        applyFilter()
    }

    fun updateEntryType(type: EntryType) {
        selectedType = type
        if (selectedCategory !in availableCategories) {
            selectedCategory = availableCategories.first()
        }
    }

    fun updateAmountInput(raw: String) {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        val normalized = if (filtered.startsWith(".")) {
            "0$filtered"
        } else {
            filtered
        }
        val parts = normalized.split('.', limit = 3)
        val sanitized = when {
            parts.size == 1 -> parts[0]
            parts.size >= 2 -> {
                val intPart = parts[0]
                val decimalPart = parts[1].take(2)
                "$intPart.$decimalPart"
            }
            else -> ""
        }
        amountInput = sanitized
    }

    private fun applyFilter() {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        val filtered = monthEntries.filter { entry ->
            val day = entry.dateTime.toLocalDate()
            when (selectedFilter) {
                DateFilter.MONTH -> true
                DateFilter.WEEK -> !day.isBefore(weekStart) && !day.isAfter(weekEnd)
                DateFilter.TODAY -> day == today
            }
        }

        entries.clear()
        entries.addAll(filtered)
        summary = repository.summarize(filtered)
    }

    fun saveEntry() {
        val amount = amountInput.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            statusMessage = "金额必须大于 0"
            return
        }

        val entry = LedgerEntry(
            dateTime = LocalDateTime.now(),
            type = selectedType,
            amount = amount,
            category = selectedCategory,
            note = noteInput.trim(),
        )

        repository.saveEntry(entry)
        amountInput = ""
        noteInput = ""
        statusMessage = "已保存"
        selectedMonth = YearMonth.now()
        selectedFilter = DateFilter.MONTH
        reloadSelectedMonth()
    }
}
