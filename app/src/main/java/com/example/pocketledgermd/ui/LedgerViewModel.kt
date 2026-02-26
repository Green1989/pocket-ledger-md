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
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth

enum class DateFilter {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
}

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MarkdownRepository(app.applicationContext)

    private val expenseCategories = listOf("餐饮", "交通", "购物", "日用", "娱乐", "医疗", "住房", "其他支出")
    private val incomeCategories = listOf("工资", "奖金", "报销", "投资收益", "退款", "其他收入")
    val availableCategories: List<String>
        get() = if (selectedType == EntryType.EXPENSE) expenseCategories else incomeCategories
    fun categoriesForType(type: EntryType): List<String> {
        return if (type == EntryType.EXPENSE) expenseCategories else incomeCategories
    }

    var amountInput by mutableStateOf("")
    var noteInput by mutableStateOf("")
    var selectedType by mutableStateOf(EntryType.EXPENSE)
    var selectedCategory by mutableStateOf(expenseCategories.first())
    var statusMessage by mutableStateOf("")
    var selectedDateTime by mutableStateOf(LocalDateTime.now())
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
        entries.clear()
        entries.addAll(data)
        refreshSummary()
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
        refreshSummary()
    }

    fun updateEntryType(type: EntryType) {
        selectedType = type
        if (selectedCategory !in availableCategories) {
            selectedCategory = availableCategories.first()
        }
    }

    fun setEntryDateTimeToNow() {
        selectedDateTime = LocalDateTime.now()
    }

    fun updateEntryDate(year: Int, month: Int, day: Int) {
        selectedDateTime = LocalDateTime.of(
            LocalDate.of(year, month, day),
            selectedDateTime.toLocalTime(),
        )
    }

    fun updateEntryTime(hour: Int, minute: Int) {
        selectedDateTime = LocalDateTime.of(
            selectedDateTime.toLocalDate(),
            LocalTime.of(hour, minute),
        )
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

    private fun refreshSummary() {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        val baseEntries = when (selectedFilter) {
            DateFilter.MONTH -> monthEntries.toList()
            DateFilter.YEAR -> repository.loadYear(Year.now())
            DateFilter.ALL -> repository.loadAll()
            DateFilter.TODAY, DateFilter.WEEK -> repository.loadAll()
        }

        val filtered = when (selectedFilter) {
            DateFilter.TODAY -> baseEntries.filter { it.dateTime.toLocalDate() == today }
            DateFilter.WEEK -> baseEntries.filter {
                val day = it.dateTime.toLocalDate()
                !day.isBefore(weekStart) && !day.isAfter(weekEnd)
            }
            DateFilter.MONTH, DateFilter.YEAR, DateFilter.ALL -> baseEntries
        }

        summary = repository.summarize(filtered)
    }

    fun saveEntry() {
        val amount = amountInput.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            statusMessage = "金额必须大于 0"
            return
        }

        val entry = LedgerEntry(
            dateTime = selectedDateTime,
            type = selectedType,
            amount = amount,
            category = selectedCategory,
            note = noteInput.trim(),
        )

        repository.saveEntry(entry)
        amountInput = ""
        noteInput = ""
        statusMessage = "已保存"
        selectedMonth = YearMonth.from(selectedDateTime)
        selectedFilter = DateFilter.MONTH
        reloadSelectedMonth()
    }

    fun updateExistingEntry(
        original: LedgerEntry,
        newDateTime: LocalDateTime,
        newType: EntryType,
        newAmountInput: String,
        newCategory: String,
        newNote: String,
    ) {
        val amount = newAmountInput.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            statusMessage = "金额必须大于 0"
            return
        }

        val updated = original.copy(
            dateTime = newDateTime,
            type = newType,
            amount = amount,
            category = newCategory,
            note = newNote.trim(),
        )

        repository.updateEntry(original, updated)
        statusMessage = "已更新"
        selectedMonth = YearMonth.from(newDateTime)
        reloadSelectedMonth()
    }
}
