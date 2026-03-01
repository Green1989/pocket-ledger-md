package com.example.pocketledgermd.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import com.example.pocketledgermd.data.MarkdownRepository
import com.example.pocketledgermd.data.MemberGroup
import com.example.pocketledgermd.data.MonthSummary
import com.example.pocketledgermd.data.ShareSyncCodec
import com.example.pocketledgermd.data.SyncParseResult
import com.example.pocketledgermd.data.displayCategoryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class DateFilter {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
}

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MarkdownRepository(app.applicationContext)
    val memberGroups = listOf(
        MemberGroup.XIAOXIN,
        MemberGroup.JIELI,
        MemberGroup.TONGTONG,
        MemberGroup.ELDER,
        MemberGroup.ALL,
    )

    private val expenseCategories = listOf("餐饮", "交通", "购物", "日用", "娱乐", "医疗", "教育", "住房", "其他支出")
    private val incomeCategories = listOf("工资", "奖金", "报销", "投资收益", "退款", "其他收入")
    val availableCategories: List<String>
        get() = if (selectedType == EntryType.EXPENSE) expenseCategories else incomeCategories
    fun categoriesForType(type: EntryType): List<String> {
        return if (type == EntryType.EXPENSE) expenseCategories else incomeCategories
    }

    var amountInput by mutableStateOf("")
    var noteInput by mutableStateOf("")
    var selectedType by mutableStateOf(EntryType.EXPENSE)
    var selectedMember by mutableStateOf(MemberGroup.ALL)
    var selectedMemberFilter by mutableStateOf(MemberGroup.ALL)
    var selectedCategory by mutableStateOf(expenseCategories.first())
    var statusMessage by mutableStateOf("")
    var selectedDateTime by mutableStateOf(LocalDateTime.now())
    var selectedMonth by mutableStateOf(YearMonth.now())
    var selectedFilter by mutableStateOf(DateFilter.MONTH)
    private var noteEditedManually = false

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
        refreshVisibleEntries()
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

    fun updateMemberFilter(member: MemberGroup) {
        selectedMemberFilter = member
        refreshVisibleEntries()
        refreshSummary()
    }

    fun updateEntryType(type: EntryType) {
        selectedType = type
        if (selectedCategory !in availableCategories) {
            selectedCategory = availableCategories.first()
        }
        applyAutoMealNoteIfNeeded()
    }

    fun updateEntryCategory(category: String) {
        selectedCategory = category
        applyAutoMealNoteIfNeeded()
    }

    fun updateEntryMember(member: MemberGroup) {
        selectedMember = member
    }

    fun updateNoteInput(note: String) {
        noteInput = note
        noteEditedManually = true
    }

    fun setEntryDateTimeToNow() {
        selectedDateTime = LocalDateTime.now()
        applyAutoMealNoteIfNeeded()
    }

    fun updateEntryDate(year: Int, month: Int, day: Int) {
        selectedDateTime = LocalDateTime.of(
            LocalDate.of(year, month, day),
            selectedDateTime.toLocalTime(),
        )
        applyAutoMealNoteIfNeeded()
    }

    fun updateEntryTime(hour: Int, minute: Int) {
        selectedDateTime = LocalDateTime.of(
            selectedDateTime.toLocalDate(),
            LocalTime.of(hour, minute),
        )
        applyAutoMealNoteIfNeeded()
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

    private fun refreshVisibleEntries() {
        val filtered = monthEntries.filter { matchesMemberFilter(it) }
        entries.clear()
        entries.addAll(filtered)
    }

    private fun matchesMemberFilter(entry: LedgerEntry): Boolean {
        return selectedMemberFilter == MemberGroup.ALL ||
            entry.member == selectedMemberFilter ||
            entry.member == MemberGroup.ALL
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
        val memberFiltered = baseEntries.filter { matchesMemberFilter(it) }

        val filtered = when (selectedFilter) {
            DateFilter.TODAY -> memberFiltered.filter { it.dateTime.toLocalDate() == today }
            DateFilter.WEEK -> memberFiltered.filter {
                val day = it.dateTime.toLocalDate()
                !day.isBefore(weekStart) && !day.isAfter(weekEnd)
            }
            DateFilter.MONTH, DateFilter.YEAR, DateFilter.ALL -> memberFiltered
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
            member = selectedMember,
            category = selectedCategory,
            note = noteInput.trim(),
        )

        repository.saveEntry(entry)
        amountInput = ""
        noteInput = ""
        noteEditedManually = false
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
        newMember: MemberGroup,
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
            member = newMember,
            category = newCategory,
            note = newNote.trim(),
        )

        repository.updateEntry(original, updated)
        statusMessage = "已更新"
        selectedMonth = YearMonth.from(newDateTime)
        reloadSelectedMonth()
    }

    fun deleteExistingEntry(entry: LedgerEntry) {
        repository.deleteEntry(entry)
        statusMessage = "已删除"
        selectedMonth = YearMonth.from(entry.dateTime)
        reloadSelectedMonth()
    }

    fun buildTodayShareText(): String {
        val targetDate = selectedDateTime.toLocalDate()
        val todayEntries = repository.loadAll()
            .filter { it.dateTime.toLocalDate() == targetDate }
            .filter { matchesMemberFilter(it) }
            .sortedBy { it.dateTime }

        val dateText = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val readableText = if (todayEntries.isEmpty()) {
            """
【$dateText 记账汇总】
今日无记账记录
            """.trimIndent()
        } else {
            val summary = repository.summarize(todayEntries)
            val expenseEntries = todayEntries.filter { it.type == EntryType.EXPENSE }
            val incomeEntries = todayEntries.filter { it.type == EntryType.INCOME }
            val lineFormatter = DateTimeFormatter.ofPattern("HH:mm")

            val builder = StringBuilder()
            builder.appendLine("【$dateText 记账汇总】")
            builder.appendLine("收入：${summary.totalIncome}")
            builder.appendLine("支出：${summary.totalExpense}")
            builder.appendLine("结余：${summary.balance}")

            if (expenseEntries.isNotEmpty()) {
                builder.appendLine()
                builder.appendLine("支出明细：")
                expenseEntries.forEach { entry ->
                    val note = if (entry.note.isBlank()) "" else " ${entry.note}"
                    builder.appendLine("${entry.dateTime.format(lineFormatter)} ${entry.displayCategoryText()} ${entry.amount}$note")
                }
            }

            if (incomeEntries.isNotEmpty()) {
                builder.appendLine()
                builder.appendLine("收入明细：")
                incomeEntries.forEach { entry ->
                    val note = if (entry.note.isBlank()) "" else " ${entry.note}"
                    builder.appendLine("${entry.dateTime.format(lineFormatter)} ${entry.displayCategoryText()} ${entry.amount}$note")
                }
            }

            builder.toString().trimEnd()
        }

        return ShareSyncCodec.appendSyncBlock(readableText, targetDate, todayEntries)
    }

    fun syncFromClipboardText(rawText: String) {
        when (val parsed = ShareSyncCodec.parse(rawText)) {
            is SyncParseResult.NotFound -> {
                statusMessage = "未检测到可同步记账内容"
            }

            is SyncParseResult.Invalid -> {
                statusMessage = "同步失败：${parsed.message}"
            }

            is SyncParseResult.Success -> {
                val result = repository.importEntries(parsed.payload.entries)
                statusMessage = "${result.message}: 新增 ${result.importedCount} 条，跳过 ${result.skippedCount} 条"
                reloadSelectedMonth()
            }
        }
    }

    fun backupLedgerToDirectory(treeUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.backupToExternalTree(treeUri)
            }
            statusMessage = if (result.success) {
                "${result.message}: 成功 ${result.validFiles} 个，跳过 ${result.skippedFiles} 个"
            } else {
                result.message
            }
        }
    }

    fun restoreLedgerFromDirectory(treeUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.restoreFromExternalTree(treeUri)
            }
            statusMessage = if (result.success) {
                "${result.message}: 成功 ${result.validFiles} 个，跳过 ${result.skippedFiles} 个"
            } else {
                result.message
            }
            reloadSelectedMonth()
        }
    }

    private fun applyAutoMealNoteIfNeeded() {
        if (selectedType != EntryType.EXPENSE) return
        if (selectedCategory != "餐饮") return
        if (noteEditedManually) return

        val hour = selectedDateTime.hour
        val autoNote = when (hour) {
            in 6..10 -> "早餐"
            in 11..12 -> "午餐"
            in 13..15 -> "下午茶"
            in 16..20 -> "晚餐"
            in 21..23 -> "宵夜"
            else -> null
        }
        if (autoNote != null) {
            noteInput = autoNote
        }
    }
}
