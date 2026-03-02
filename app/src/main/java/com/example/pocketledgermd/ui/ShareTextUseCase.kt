package com.example.pocketledgermd.ui

import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import com.example.pocketledgermd.data.MarkdownRepository
import com.example.pocketledgermd.data.MemberGroup
import com.example.pocketledgermd.data.ShareSyncCodec
import com.example.pocketledgermd.data.displayCategoryText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class ShareTextUseCase(
    private val repository: MarkdownRepository,
) {
    fun build(
        selectedDateTime: LocalDateTime,
        shareDays: Int,
        memberFilter: MemberGroup?,
    ): String {
        val startDate = selectedDateTime.toLocalDate()
        val safeShareDays = shareDays.coerceAtLeast(1)
        val endDate = startDate.plusDays((safeShareDays - 1).toLong())
        val rangeEntries = repository.loadAll()
            .filter {
                val day = it.dateTime.toLocalDate()
                !day.isBefore(startDate) && !day.isAfter(endDate)
            }
            .filter { matchesMemberFilter(it, memberFilter) }
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

    private fun matchesMemberFilter(
        entry: LedgerEntry,
        memberFilter: MemberGroup?,
    ): Boolean {
        return memberFilter == null || entry.member == memberFilter
    }
}
