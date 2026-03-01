package com.example.pocketledgermd.data

import java.math.RoundingMode
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SyncPayload(
    val date: LocalDate?,
    val entries: List<LedgerEntry>,
)

sealed class SyncParseResult {
    data object NotFound : SyncParseResult()
    data class Invalid(val message: String) : SyncParseResult()
    data class Success(val payload: SyncPayload) : SyncParseResult()
}

object ShareSyncCodec {
    private const val version = "2"
    private const val lineVersion = "V"
    private const val lineDate = "D"
    private const val lineEntry = "E"
    const val syncStart = "---POCKET_LEDGER_SYNC_START---"
    const val syncEnd = "---POCKET_LEDGER_SYNC_END---"
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun appendSyncBlock(
        readableText: String,
        targetDate: LocalDate,
        entries: List<LedgerEntry>,
    ): String {
        val builder = StringBuilder(readableText.trimEnd())
        builder.appendLine()
        builder.appendLine()
        builder.append(buildSyncBlock(targetDate, entries))
        return builder.toString().trimEnd()
    }

    fun buildSyncBlock(targetDate: LocalDate, entries: List<LedgerEntry>): String {
        val builder = StringBuilder()
        builder.appendLine(syncStart)
        builder.appendLine("$lineVersion|$version")
        builder.appendLine("$lineDate|${targetDate.format(dateFormatter)}")
        entries.sortedBy { it.dateTime }.forEach { entry ->
            val typeText = when (entry.type) {
                EntryType.EXPENSE -> "expense"
                EntryType.INCOME -> "income"
            }
            val id = entry.id.orEmpty()
            val member = entry.member.code
            val amount = entry.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
            val category = encode(entry.category)
            val note = encode(entry.note)
            builder.appendLine(
                "$lineEntry|$id|${entry.dateTime.format(dateTimeFormatter)}|$typeText|$member|$amount|$category|$note"
            )
        }
        builder.append(syncEnd)
        return builder.toString()
    }

    fun parse(text: String): SyncParseResult {
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        val start = lines.indexOfFirst { it.trim() == syncStart }
        if (start == -1) return SyncParseResult.NotFound
        val end = (start + 1 until lines.size).firstOrNull { lines[it].trim() == syncEnd } ?: -1
        if (end == -1) return SyncParseResult.Invalid("同步数据格式不完整")

        var parsedDate: LocalDate? = null
        var parsedVersion = "1"
        val entries = mutableListOf<LedgerEntry>()
        for (i in (start + 1) until end) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val parts = line.split('|')
            if (parts.isEmpty()) continue
            when (parts[0]) {
                lineVersion -> {
                    if (parts.size < 2) {
                        return SyncParseResult.Invalid("同步版本格式错误")
                    }
                    parsedVersion = parts[1]
                    if (parsedVersion != "1" && parsedVersion != version) {
                        return SyncParseResult.Invalid("不支持的同步版本")
                    }
                }

                lineDate -> {
                    if (parts.size < 2) return SyncParseResult.Invalid("同步日期格式错误")
                    parsedDate = runCatching { LocalDate.parse(parts[1], dateFormatter) }
                        .getOrElse { return SyncParseResult.Invalid("同步日期格式错误") }
                }

                lineEntry -> {
                    if (parsedVersion == "1" && parts.size < 7) {
                        return SyncParseResult.Invalid("同步记录格式错误")
                    }
                    if (parsedVersion == version && parts.size < 8) {
                        return SyncParseResult.Invalid("同步记录格式错误")
                    }
                    val id = parts[1].ifBlank { null }
                    val dateTime = runCatching { LocalDateTime.parse(parts[2], dateTimeFormatter) }
                        .getOrElse { return SyncParseResult.Invalid("同步时间格式错误") }
                    val type = when (parts[3].lowercase()) {
                        "expense" -> EntryType.EXPENSE
                        "income" -> EntryType.INCOME
                        else -> return SyncParseResult.Invalid("同步类型格式错误")
                    }
                    val member = if (parsedVersion == version) {
                        MemberGroup.fromCode(parts[4])
                    } else {
                        MemberGroup.ALL
                    }
                    val amountIndex = if (parsedVersion == version) 5 else 4
                    val categoryIndex = if (parsedVersion == version) 6 else 5
                    val noteIndex = if (parsedVersion == version) 7 else 6
                    val amount = runCatching { parts[amountIndex].toBigDecimal() }
                        .getOrElse { return SyncParseResult.Invalid("同步金额格式错误") }
                    val category = decode(parts[categoryIndex])
                    val note = decode(parts[noteIndex])
                    entries.add(
                        LedgerEntry(
                            id = id,
                            dateTime = dateTime,
                            type = type,
                            amount = amount,
                            member = member,
                            category = category,
                            note = note,
                        )
                    )
                }
            }
        }
        return SyncParseResult.Success(SyncPayload(date = parsedDate, entries = entries))
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
