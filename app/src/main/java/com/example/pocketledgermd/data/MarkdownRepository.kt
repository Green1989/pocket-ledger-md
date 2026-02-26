package com.example.pocketledgermd.data

import android.content.Context
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class MarkdownRepository(private val context: Context) {
    private val ledgerDir: File by lazy {
        File(context.filesDir, "ledger").apply { mkdirs() }
    }

    fun saveEntry(entry: LedgerEntry) {
        val ym = YearMonth.from(entry.dateTime)
        val monthFile = monthFile(ym)
        val current = if (monthFile.exists()) monthFile.readText() else ""
        val updated = MarkdownCodec.upsertMonthlyContent(ym, current, entry)
        monthFile.writeText(updated)
    }

    fun loadMonth(ym: YearMonth): List<LedgerEntry> {
        val file = monthFile(ym)
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
