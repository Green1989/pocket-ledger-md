package com.example.pocketledgermd.data

import android.content.Context
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.util.UUID

class MarkdownRepository(private val context: Context) {
    private val ledgerDir: File by lazy {
        File(context.filesDir, "ledger").apply { mkdirs() }
    }

    fun saveEntry(entry: LedgerEntry) {
        val savedEntry = if (entry.id.isNullOrBlank()) {
            entry.copy(id = UUID.randomUUID().toString())
        } else {
            entry
        }
        val ym = YearMonth.from(savedEntry.dateTime)
        val monthFile = monthFile(ym)
        val current = if (monthFile.exists()) monthFile.readText() else ""
        val updated = MarkdownCodec.upsertMonthlyContent(ym, current, savedEntry)
        monthFile.writeText(updated)
    }

    fun ensureEntryHasId(entry: LedgerEntry): LedgerEntry {
        if (!entry.id.isNullOrBlank()) return entry

        val ym = YearMonth.from(entry.dateTime)
        val file = monthFile(ym)
        if (!file.exists()) {
            return entry.copy(id = UUID.randomUUID().toString())
        }

        val lines = file.readText().replace("\r\n", "\n").split("\n").toMutableList()
        var currentDay: LocalDate? = null
        var matchedIndex = -1
        for (i in lines.indices) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    currentDay = runCatching { LocalDate.parse(line.removePrefix("## ").trim()) }.getOrNull()
                }
                line.startsWith("- ") && currentDay != null -> {
                    val parsed = MarkdownCodec.parseEntryLine(currentDay!!, line) ?: continue
                    if (parsed.id == null && sameEntryContent(parsed, entry)) {
                        matchedIndex = i
                        break
                    }
                }
            }
        }

        if (matchedIndex == -1) {
            return entry.copy(id = UUID.randomUUID().toString())
        }

        val newId = UUID.randomUUID().toString()
        val updatedEntry = entry.copy(id = newId)
        lines[matchedIndex] = MarkdownCodec.formatEntryLine(updatedEntry)
        file.writeText(lines.joinToString("\n").trimEnd() + "\n")
        return updatedEntry
    }

    fun updateEntry(original: LedgerEntry, updated: LedgerEntry) {
        val originalWithId = ensureEntryHasId(original)
        val updatedWithId = updated.copy(id = originalWithId.id)
        val oldMonth = YearMonth.from(originalWithId.dateTime)
        val newMonth = YearMonth.from(updatedWithId.dateTime)

        removeEntry(oldMonth, originalWithId)
        upsertEntry(newMonth, updatedWithId)
    }

    private fun upsertEntry(month: YearMonth, entry: LedgerEntry) {
        val file = monthFile(month)
        val current = if (file.exists()) file.readText() else ""
        val updated = MarkdownCodec.upsertMonthlyContent(month, current, entry)
        file.writeText(updated)
    }

    private fun removeEntry(month: YearMonth, entry: LedgerEntry) {
        val file = monthFile(month)
        if (!file.exists()) return

        val lines = file.readText().replace("\r\n", "\n").split("\n").toMutableList()
        var currentDay: LocalDate? = null
        var removed = false

        for (i in lines.indices) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    currentDay = runCatching { LocalDate.parse(line.removePrefix("## ").trim()) }.getOrNull()
                }
                line.startsWith("- ") && currentDay != null -> {
                    val parsed = MarkdownCodec.parseEntryLine(currentDay!!, line) ?: continue
                    val idMatched = !entry.id.isNullOrBlank() && parsed.id == entry.id
                    val legacyMatched = entry.id.isNullOrBlank() && sameEntryContent(parsed, entry)
                    if (idMatched || legacyMatched) {
                        lines.removeAt(i)
                        removed = true
                        break
                    }
                }
            }
        }

        if (!removed) return
        file.writeText(lines.joinToString("\n").trimEnd() + "\n")
    }

    private fun sameEntryContent(a: LedgerEntry, b: LedgerEntry): Boolean {
        return a.dateTime == b.dateTime &&
            a.type == b.type &&
            a.amount.compareTo(b.amount) == 0 &&
            a.category == b.category &&
            a.note == b.note
    }

    fun loadMonth(ym: YearMonth): List<LedgerEntry> {
        val file = monthFile(ym)
        if (!file.exists()) return emptyList()

        return parseMonthFile(file)
    }

    fun loadYear(year: Year): List<LedgerEntry> {
        return listMonthFiles()
            .filter { it.name.startsWith("${year.value}-") }
            .flatMap { parseMonthFile(it) }
            .sortedByDescending { it.dateTime }
    }

    fun loadAll(): List<LedgerEntry> {
        return listMonthFiles()
            .flatMap { parseMonthFile(it) }
            .sortedByDescending { it.dateTime }
    }

    private fun parseMonthFile(file: File): List<LedgerEntry> {
        if (!file.exists()) return emptyList()

        var currentDay: LocalDate? = null
        val result = mutableListOf<LedgerEntry>()

        file.readLines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    currentDay = runCatching { LocalDate.parse(line.removePrefix("## ").trim()) }.getOrNull()
                }
                line.startsWith("- ") && currentDay != null -> {
                    val parsed = MarkdownCodec.parseEntryLine(currentDay!!, line)
                    if (parsed != null) {
                        result.add(parsed)
                    }
                }
            }
        }

        return result.sortedByDescending { it.dateTime }
    }

    private fun listMonthFiles(): List<File> {
        val pattern = Regex("""^\d{4}-\d{2}\.md$""")
        return ledgerDir
            .listFiles()
            ?.filter { it.isFile && pattern.matches(it.name) }
            ?: emptyList()
    }

    fun summarize(entries: List<LedgerEntry>): MonthSummary {
        val income = entries
            .filter { it.type == EntryType.INCOME }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
        val expense = entries
            .filter { it.type == EntryType.EXPENSE }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
        return MonthSummary(
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
        )
    }

    private fun monthFile(ym: YearMonth): File = File(ledgerDir, "${ym}.md")
}
