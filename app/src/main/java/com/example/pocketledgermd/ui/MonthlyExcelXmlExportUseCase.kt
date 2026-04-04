package com.example.pocketledgermd.ui

import android.content.Context
import android.net.Uri
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import com.example.pocketledgermd.data.MarkdownRepository
import com.example.pocketledgermd.data.MemberGroup
import java.io.IOException
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal class MonthlyExcelXmlExportUseCase(
    private val context: Context,
    private val repository: MarkdownRepository,
) {
    fun export(
        selectedMonth: YearMonth,
        memberFilter: MemberGroup?,
        targetUri: Uri,
    ) {
        val content = buildSpreadsheetXml(selectedMonth, memberFilter)
        context.contentResolver.openOutputStream(targetUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(content)
        } ?: throw IOException("无法打开导出文件")
    }

    internal fun buildSpreadsheetXml(
        selectedMonth: YearMonth,
        memberFilter: MemberGroup?,
    ): String {
        val entries = repository.loadMonth(selectedMonth)
            .filter { memberFilter == null || it.member == memberFilter }
        val expenseEntries = entries.filter { it.type == EntryType.EXPENSE }.sortedBy { it.dateTime }
        val incomeEntries = entries.filter { it.type == EntryType.INCOME }.sortedBy { it.dateTime }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<?mso-application progid="Excel.Sheet"?>""")
            appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"""")
            appendLine(""" xmlns:o="urn:schemas-microsoft-com:office:office"""")
            appendLine(""" xmlns:x="urn:schemas-microsoft-com:office:excel"""")
            appendLine(""" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")
            appendWorksheet("支出", expenseEntries)
            appendWorksheet("收入", incomeEntries)
            appendLine("""</Workbook>""")
        }
    }

    private fun StringBuilder.appendWorksheet(
        sheetName: String,
        entries: List<LedgerEntry>,
    ) {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        appendLine("""  <Worksheet ss:Name="${escapeXml(sheetName)}">""")
        appendLine("""    <Table>""")
        appendHeaderRow()
        entries.forEach { entry ->
            appendLine("""      <Row>""")
            appendCell(entry.dateTime.toLocalDate().format(dateFormatter))
            appendCell(entry.dateTime.toLocalTime().format(timeFormatter))
            appendCell(entry.member.label)
            appendCell(entry.category)
            appendNumberCell(entry.amount.toPlainString())
            appendCell(entry.note)
            appendLine("""      </Row>""")
        }
        appendLine("""    </Table>""")
        appendLine("""  </Worksheet>""")
    }

    private fun StringBuilder.appendHeaderRow() {
        appendLine("""      <Row>""")
        listOf("日期", "时间", "成员", "分类", "金额", "备注").forEach { header ->
            appendCell(header)
        }
        appendLine("""      </Row>""")
    }

    private fun StringBuilder.appendCell(value: String) {
        appendLine(
            """        <Cell><Data ss:Type="String">${escapeXml(value)}</Data></Cell>"""
        )
    }

    private fun StringBuilder.appendNumberCell(value: String) {
        appendLine(
            """        <Cell><Data ss:Type="Number">${escapeXml(value)}</Data></Cell>"""
        )
    }

    private fun escapeXml(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
