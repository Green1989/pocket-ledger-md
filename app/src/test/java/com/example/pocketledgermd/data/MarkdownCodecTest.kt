package com.example.pocketledgermd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MarkdownCodecTest {
    @Test
    fun formatAndParse_shouldRoundTrip() {
        val entry = LedgerEntry(
            dateTime = LocalDateTime.of(2026, 2, 26, 9, 30),
            type = EntryType.EXPENSE,
            amount = "25.50".toBigDecimal(),
            category = "餐饮",
            note = "早餐",
        )

        val line = MarkdownCodec.formatEntryLine(entry)
        val parsed = MarkdownCodec.parseEntryLine(LocalDate.of(2026, 2, 26), line)

        assertNotNull(parsed)
        assertEquals(entry.type, parsed!!.type)
        assertEquals(entry.amount, parsed.amount)
        assertEquals(entry.category, parsed.category)
        assertEquals(entry.note, parsed.note)
        assertEquals(entry.dateTime, parsed.dateTime)
    }

    @Test
    fun parse_shouldReturnNull_whenInvalidLine() {
        val parsed = MarkdownCodec.parseEntryLine(LocalDate.of(2026, 2, 26), "not-a-valid-line")
        assertNull(parsed)
    }

    @Test
    fun parse_shouldKeepEmptyNote_whenNoteIsBlank() {
        val line = "- 09:30 | expense | 25.50 | 餐饮 | "
        val parsed = MarkdownCodec.parseEntryLine(LocalDate.of(2026, 2, 26), line)
        assertNotNull(parsed)
        assertEquals("", parsed!!.note)
    }

    @Test
    fun parse_shouldKeepEmptyNote_whenLineTrimmed() {
        val line = "- 09:30 | expense | 25.50 | 餐饮 |"
        val parsed = MarkdownCodec.parseEntryLine(LocalDate.of(2026, 2, 26), line)
        assertNotNull(parsed)
        assertEquals("", parsed!!.note)
    }

    @Test
    fun upsertMonthlyContent_shouldCreateTitleAndDaySection_whenEmpty() {
        val entry = LedgerEntry(
            dateTime = LocalDateTime.of(2026, 2, 26, 9, 30),
            type = EntryType.EXPENSE,
            amount = "25.50".toBigDecimal(),
            category = "餐饮",
            note = "早餐",
        )

        val content = MarkdownCodec.upsertMonthlyContent(YearMonth.of(2026, 2), "", entry)

        assertTrue(content.contains("# 2026-02 Ledger"))
        assertTrue(content.contains("## 2026-02-26"))
        assertTrue(content.contains("- 09:30 | expense | 25.50 | 餐饮 | 早餐"))
    }

    @Test
    fun upsertMonthlyContent_shouldInsertIntoMatchedDaySection() {
        val existing = """
# 2026-02 Ledger

## 2026-02-26
- 08:00 | expense | 10.00 | 餐饮 | 豆浆

## 2026-02-25
- 18:00 | expense | 50.00 | 交通 | 地铁
""".trimIndent()

        val entry = LedgerEntry(
            dateTime = LocalDateTime.of(2026, 2, 26, 9, 30),
            type = EntryType.EXPENSE,
            amount = "25.50".toBigDecimal(),
            category = "餐饮",
            note = "早餐",
        )

        val updated = MarkdownCodec.upsertMonthlyContent(YearMonth.of(2026, 2), existing, entry)
        val expectedLine = "- 09:30 | expense | 25.50 | 餐饮 | 早餐"

        val indexNew = updated.indexOf(expectedLine)
        val indexNextDay = updated.indexOf("## 2026-02-25")
        assertTrue(indexNew > 0)
        assertTrue(indexNew < indexNextDay)
    }
}
