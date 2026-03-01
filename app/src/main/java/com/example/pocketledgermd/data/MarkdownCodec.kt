package com.example.pocketledgermd.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.YearMonth

object MarkdownCodec {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val memberMarker = "@member="
    private const val idMarker = "@id="
    private const val legacyIdMarker = "id="

    fun monthTitle(yearMonth: String): String = "# $yearMonth Ledger"

    fun dayHeader(day: LocalDate): String = "## $day"

    fun formatEntryLine(entry: LedgerEntry): String {
        val type = when (entry.type) {
            EntryType.EXPENSE -> "expense"
            EntryType.INCOME -> "income"
        }
        val amount = entry.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val base = "- ${entry.dateTime.format(timeFormatter)} | $type | $amount | ${entry.category} | ${entry.note}"
        val metaParts = mutableListOf<String>()
        if (entry.member != MemberGroup.ALL) {
            metaParts.add(memberMarker + entry.member.code)
        }
        if (!entry.id.isNullOrBlank()) {
            metaParts.add(idMarker + entry.id)
        }
        if (metaParts.isEmpty()) return base
        return "$base | ${metaParts.joinToString(" | ")}"
    }

    fun parseEntryLine(day: LocalDate, line: String): LedgerEntry? {
        if (!line.startsWith("- ")) return null

        val payload = line.removePrefix("- ")
        val tokens = payload.split('|').map { it.trim() }
        if (tokens.size < 4) return null

        var id: String? = null
        var member = MemberGroup.ALL
        var payloadEnd = tokens.size
        while (payloadEnd > 0) {
            val token = tokens[payloadEnd - 1]
            when {
                token.startsWith(idMarker) -> {
                    id = token.removePrefix(idMarker).trim().ifBlank { null }
                    payloadEnd--
                }
                token.startsWith(legacyIdMarker) -> {
                    if (id == null) {
                        id = token.removePrefix(legacyIdMarker).trim().ifBlank { null }
                    }
                    payloadEnd--
                }
                token.startsWith(memberMarker) -> {
                    member = MemberGroup.fromCode(token.removePrefix(memberMarker).trim())
                    payloadEnd--
                }
                else -> break
            }
        }

        val parts = tokens.subList(0, payloadEnd)
        if (parts.size < 4) return null

        return try {
            val time = LocalTime.parse(parts[0], timeFormatter)
            val type = when (parts[1].trim().lowercase()) {
                "expense" -> EntryType.EXPENSE
                "income" -> EntryType.INCOME
                else -> return null
            }
            val amount = parts[2].trim().toBigDecimal()
            val category = parts[3].trim()
            val note = if (parts.size >= 5) parts.subList(4, parts.size).joinToString("|").trim() else ""

            LedgerEntry(
                id = id,
                dateTime = LocalDateTime.of(day, time),
                type = type,
                amount = amount,
                member = member,
                category = category,
                note = note,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun upsertMonthlyContent(
        yearMonth: YearMonth,
        currentContent: String,
        entry: LedgerEntry,
    ): String {
        val lines = if (currentContent.isBlank()) {
            mutableListOf(monthTitle(yearMonth.toString()))
        } else {
            currentContent.replace("\r\n", "\n").split("\n").toMutableList()
        }

        if (lines.isEmpty() || !lines.first().trim().startsWith("# ")) {
            lines.add(0, monthTitle(yearMonth.toString()))
        }

        val dayHeader = dayHeader(entry.dateTime.toLocalDate())
        val entryLine = formatEntryLine(entry)

        val dayIndex = lines.indexOfFirst { it.trim() == dayHeader }
        if (dayIndex == -1) {
            while (lines.isNotEmpty() && lines.last().isBlank()) {
                lines.removeAt(lines.lastIndex)
            }
            lines.add("")
            lines.add(dayHeader)
            lines.add(entryLine)
        } else {
            var insertIndex = dayIndex + 1
            while (insertIndex < lines.size && !lines[insertIndex].trim().startsWith("## ")) {
                insertIndex++
            }
            lines.add(insertIndex, entryLine)
        }

        return lines.joinToString("\n").trimEnd() + "\n"
    }
}
