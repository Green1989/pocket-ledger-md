package com.example.pocketledgermd.ui

import android.app.Application
import android.content.Context
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
import com.example.pocketledgermd.data.SyncDiagnosticsLogger
import com.example.pocketledgermd.data.SyncParseResult
import com.example.pocketledgermd.data.displayCategoryText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.math.BigDecimal
import java.math.RoundingMode
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

data class CategoryAggregationSummary(
    val categoryDisplay: String,
    val totalAmount: BigDecimal,
    val percentOfTypeTotal: BigDecimal,
    val itemCount: Int,
)

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MarkdownRepository(app.applicationContext)
    private val preferences = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val syncDiagnosticsLogger = SyncDiagnosticsLogger(app.applicationContext)
    val memberGroups = listOf(
        MemberGroup.XIAOXIN,
        MemberGroup.JIELI,
        MemberGroup.TONGTONG,
        MemberGroup.ELDER,
        MemberGroup.ALL,
    )

    private val expenseCategories = listOf("餐饮", "交通", "购物", "日用", "娱乐", "医疗", "教育", "住房", "其他支出")
    private val incomeCategories = listOf("工资", "奖金", "报销", "投资收益", "退款", "其他收入")
    private val customExpenseCategories = mutableStateListOf<String>()
    private val customIncomeCategories = mutableStateListOf<String>()
    val availableCategories: List<String>
        get() = allCategoriesForType(selectedType)
    fun categoriesForType(type: EntryType): List<String> {
        return allCategoriesForType(type)
    }

    var amountInput by mutableStateOf("")
    var noteInput by mutableStateOf("")
    var selectedType by mutableStateOf(EntryType.EXPENSE)
    var selectedMember by mutableStateOf(loadPersistedMember())
    var selectedMemberFilter by mutableStateOf(MemberGroup.ALL)
    var selectedCategory by mutableStateOf(expenseCategories.first())
    var statusMessage by mutableStateOf("")
    var hasSyncDiagnostics by mutableStateOf(syncDiagnosticsLogger.hasAnyLogs())
    var selectedDateTime by mutableStateOf(LocalDateTime.now())
    var selectedMonth by mutableStateOf(YearMonth.now())
    var selectedFilter by mutableStateOf(DateFilter.MONTH)
    var selectedAggregationType by mutableStateOf(EntryType.EXPENSE)
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
        loadCustomCategories()
        if (selectedCategory !in availableCategories) {
            selectedCategory = availableCategories.first()
        }
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
        refreshVisibleEntries()
        refreshSummary()
    }

    fun updateAggregationType(type: EntryType) {
        selectedAggregationType = type
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

    fun tryAddCustomCategory(rawName: String): Boolean {
        val name = rawName.trim()
        if (name.isBlank()) {
            statusMessage = "分类不能为空"
            return false
        }
        if (allCategoriesForType(selectedType).any { it == name }) {
            statusMessage = "分类已存在"
            return false
        }

        val customList = if (selectedType == EntryType.EXPENSE) {
            customExpenseCategories
        } else {
            customIncomeCategories
        }
        customList.add(name)
        persistCustomCategories(selectedType)
        selectedCategory = name
        statusMessage = "已新增分类"
        return true
    }

    fun updateEntryMember(member: MemberGroup) {
        selectedMember = member
        preferences.edit().putString(KEY_LAST_MEMBER, member.code).apply()
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
        val filtered = currentFilteredEntries()
        entries.clear()
        entries.addAll(filtered.sortedByDescending { it.dateTime })
    }

    private fun allCategoriesForType(type: EntryType): List<String> {
        return if (type == EntryType.EXPENSE) {
            expenseCategories + customExpenseCategories
        } else {
            incomeCategories + customIncomeCategories
        }
    }

    private fun loadCustomCategories() {
        customExpenseCategories.clear()
        customExpenseCategories.addAll(readCustomCategoryList(KEY_CUSTOM_EXPENSE_CATEGORIES))
        customIncomeCategories.clear()
        customIncomeCategories.addAll(readCustomCategoryList(KEY_CUSTOM_INCOME_CATEGORIES))
    }

    private fun readCustomCategoryList(key: String): List<String> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistCustomCategories(type: EntryType) {
        val key = if (type == EntryType.EXPENSE) {
            KEY_CUSTOM_EXPENSE_CATEGORIES
        } else {
            KEY_CUSTOM_INCOME_CATEGORIES
        }
        val source = if (type == EntryType.EXPENSE) {
            customExpenseCategories
        } else {
            customIncomeCategories
        }
        val array = JSONArray()
        source.forEach { array.put(it) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun matchesMemberFilter(entry: LedgerEntry): Boolean {
        return selectedMemberFilter == MemberGroup.ALL ||
            entry.member == selectedMemberFilter
    }

    private fun entriesForSelectedFilterWithoutMember(): List<LedgerEntry> {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        val baseEntries = when (selectedFilter) {
            DateFilter.MONTH -> monthEntries.toList()
            DateFilter.YEAR -> repository.loadYear(Year.now())
            DateFilter.ALL -> repository.loadAll()
            DateFilter.TODAY, DateFilter.WEEK -> repository.loadAll()
        }

        return when (selectedFilter) {
            DateFilter.TODAY -> baseEntries.filter { it.dateTime.toLocalDate() == today }
            DateFilter.WEEK -> baseEntries.filter {
                val day = it.dateTime.toLocalDate()
                !day.isBefore(weekStart) && !day.isAfter(weekEnd)
            }
            DateFilter.MONTH, DateFilter.YEAR, DateFilter.ALL -> baseEntries
        }
    }

    private fun currentFilteredEntries(): List<LedgerEntry> {
        return entriesForSelectedFilterWithoutMember().filter { matchesMemberFilter(it) }
    }

    private fun refreshSummary() {
        summary = repository.summarize(entries.toList())
    }

    fun categoryAggregationSummaries(): List<CategoryAggregationSummary> {
        val targetEntries = entries.filter { it.type == selectedAggregationType }
        if (targetEntries.isEmpty()) return emptyList()

        val totalAmount = targetEntries.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
        if (totalAmount <= BigDecimal.ZERO) return emptyList()

        return targetEntries
            .groupBy { it.displayCategoryText() }
            .map { (categoryDisplay, groupedEntries) ->
                val categoryTotal = groupedEntries.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
                val ratio = if (totalAmount == BigDecimal.ZERO) {
                    BigDecimal.ZERO
                } else {
                    categoryTotal
                        .multiply(BigDecimal("100"))
                        .divide(totalAmount, 2, RoundingMode.HALF_UP)
                }
                CategoryAggregationSummary(
                    categoryDisplay = categoryDisplay,
                    totalAmount = categoryTotal,
                    percentOfTypeTotal = ratio,
                    itemCount = groupedEntries.size,
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    fun entriesByCategory(categoryDisplay: String): List<LedgerEntry> {
        return entries
            .filter { it.type == selectedAggregationType && it.displayCategoryText() == categoryDisplay }
            .sortedByDescending { it.dateTime }
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

    fun buildTodayShareText(shareDays: Int = 1): String {
        val startDate = selectedDateTime.toLocalDate()
        val safeShareDays = shareDays.coerceAtLeast(1)
        val endDate = startDate.plusDays((safeShareDays - 1).toLong())
        val rangeEntries = repository.loadAll()
            .filter {
                val day = it.dateTime.toLocalDate()
                !day.isBefore(startDate) && !day.isAfter(endDate)
            }
            .filter { matchesMemberFilter(it) }
            .sortedBy { it.dateTime }
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val lineFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val startText = startDate.format(dateFormatter)
        val endText = endDate.format(dateFormatter)

        val summary = repository.summarize(rangeEntries)
        val builder = StringBuilder()
        if (safeShareDays == 1) {
            builder.appendLine("【$startText 记账汇总】")
        } else {
            builder.appendLine("【$startText 至 $endText 记账汇总】")
        }
        builder.appendLine("收入：${summary.totalIncome}")
        builder.appendLine("支出：${summary.totalExpense}")
        builder.appendLine("结余：${summary.balance}")
        builder.appendLine()
        builder.appendLine("每日明细：")

        for (offset in 0 until safeShareDays) {
            val day = startDate.plusDays(offset.toLong())
            val dayText = day.format(dateFormatter)
            builder.appendLine("$dayText：")
            val dayEntries = rangeEntries
                .filter { it.dateTime.toLocalDate() == day }
                .sortedBy { it.dateTime }
            if (dayEntries.isEmpty()) {
                builder.appendLine("无记账记录")
                continue
            }
            dayEntries.forEach { entry ->
                val typeText = if (entry.type == EntryType.EXPENSE) "支出" else "收入"
                val note = if (entry.note.isBlank()) "" else " ${entry.note}"
                builder.appendLine(
                    "${entry.dateTime.format(lineFormatter)} $typeText ${entry.displayCategoryText()} ${entry.amount}$note"
                )
            }
        }
        val readableText = builder.toString().trimEnd()

        return ShareSyncCodec.appendSyncBlock(readableText, startDate, rangeEntries)
    }

    fun getLastCustomShareDays(): Int {
        return preferences.getInt(KEY_LAST_SHARE_DAYS, 1).coerceAtLeast(1)
    }

    fun persistLastCustomShareDays(days: Int) {
        preferences.edit().putInt(KEY_LAST_SHARE_DAYS, days.coerceAtLeast(1)).apply()
    }

    fun readLatestSyncDiagnostics(): String? {
        return syncDiagnosticsLogger.readLatestLog()
    }

    fun syncFromClipboardText(rawText: String) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    ShareSyncCodec.parse(rawText)
                }
                when (parsed) {
                    is SyncParseResult.NotFound -> {
                        val logName = recordSyncFailure(
                            stage = "parse",
                            reason = "未检测到同步数据块",
                            rawText = rawText,
                        )
                        statusMessage = "未检测到可同步记账内容（已记录 $logName）"
                    }

                    is SyncParseResult.Invalid -> {
                        val logName = recordSyncFailure(
                            stage = "parse",
                            reason = parsed.message,
                            rawText = rawText,
                        )
                        statusMessage = "同步失败：${parsed.message}（已记录 $logName）"
                    }

                    is SyncParseResult.Success -> {
                        val result = withContext(Dispatchers.IO) {
                            repository.importEntries(parsed.payload.entries)
                        }
                        statusMessage = "${result.message}: 新增 ${result.importedCount} 条，跳过 ${result.skippedCount} 条"
                        reloadSelectedMonth()
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val logName = recordSyncFailure(
                    stage = "import",
                    reason = t.message ?: "未知错误",
                    rawText = rawText,
                    throwable = t,
                )
                statusMessage = "同步失败：${t.message ?: "未知错误"}（已记录 $logName）"
            }
        }
    }

    private suspend fun recordSyncFailure(
        stage: String,
        reason: String,
        rawText: String,
        throwable: Throwable? = null,
    ): String {
        val file = withContext(Dispatchers.IO) {
            syncDiagnosticsLogger.writeFailureLog(
                stage = stage,
                reason = reason,
                rawPayload = rawText,
                throwable = throwable,
            )
        }
        hasSyncDiagnostics = true
        return file.name
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

    private fun loadPersistedMember(): MemberGroup {
        val code = preferences.getString(KEY_LAST_MEMBER, MemberGroup.ALL.code)
        return MemberGroup.fromCode(code)
    }

    companion object {
        private const val PREFS_NAME = "pocket_ledger_settings"
        private const val KEY_LAST_MEMBER = "last_selected_member"
        private const val KEY_CUSTOM_EXPENSE_CATEGORIES = "custom_expense_categories"
        private const val KEY_CUSTOM_INCOME_CATEGORIES = "custom_income_categories"
        private const val KEY_LAST_SHARE_DAYS = "last_share_days"
    }
}
