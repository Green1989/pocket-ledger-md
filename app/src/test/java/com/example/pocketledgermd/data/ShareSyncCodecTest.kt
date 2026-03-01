package com.example.pocketledgermd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ShareSyncCodecTest {
    @Test
    fun buildAndParse_shouldRoundTripEntries() {
        val date = LocalDate.of(2026, 3, 1)
        val entries = listOf(
            LedgerEntry(
                id = "id-expense-1",
                dateTime = LocalDateTime.of(2026, 3, 1, 8, 30),
                type = EntryType.EXPENSE,
                amount = "12.50".toBigDecimal(),
                member = MemberGroup.XIAOXIN,
                category = "餐饮",
                note = "早餐|豆浆油条",
            ),
            LedgerEntry(
                id = "id-income-1",
                dateTime = LocalDateTime.of(2026, 3, 1, 21, 45),
                type = EntryType.INCOME,
                amount = "200.00".toBigDecimal(),
                category = "奖金",
                note = "项目奖",
            ),
        )

        val text = ShareSyncCodec.appendSyncBlock("测试文本", date, entries)
        val parsed = ShareSyncCodec.parse(text)
        assertTrue(parsed is SyncParseResult.Success)
        val payload = (parsed as SyncParseResult.Success).payload

        assertEquals(date, payload.date)
        assertEquals(2, payload.entries.size)
        assertEquals("id-expense-1", payload.entries[0].id)
        assertEquals(MemberGroup.XIAOXIN, payload.entries[0].member)
        assertEquals("早餐|豆浆油条", payload.entries[0].note)
        assertEquals("id-income-1", payload.entries[1].id)
        assertEquals(MemberGroup.ALL, payload.entries[1].member)
    }

    @Test
    fun parse_shouldReturnNotFound_whenNoSyncBlock() {
        val parsed = ShareSyncCodec.parse("普通聊天文本")
        assertTrue(parsed is SyncParseResult.NotFound)
    }

    @Test
    fun parse_shouldSupportLegacyV1Payload() {
        val legacyText = """
文本
---POCKET_LEDGER_SYNC_START---
V|1
D|2026-03-01
E|legacy-id|2026-03-01T09:30:00|expense|10.00|%E9%A4%90%E9%A5%AE|%E6%97%A9%E9%A4%90
---POCKET_LEDGER_SYNC_END---
        """.trimIndent()

        val parsed = ShareSyncCodec.parse(legacyText)
        assertTrue(parsed is SyncParseResult.Success)
        val payload = (parsed as SyncParseResult.Success).payload
        assertEquals(1, payload.entries.size)
        assertEquals(MemberGroup.ALL, payload.entries[0].member)
    }
}
