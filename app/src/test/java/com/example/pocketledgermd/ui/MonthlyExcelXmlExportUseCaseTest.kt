package com.example.pocketledgermd.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import com.example.pocketledgermd.data.MarkdownRepository
import com.example.pocketledgermd.data.MemberGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class MonthlyExcelXmlExportUseCaseTest {
    private lateinit var testRoot: File
    private lateinit var repository: MarkdownRepository
    private lateinit var useCase: MonthlyExcelXmlExportUseCase

    @Before
    fun setUp() {
        testRoot = File(System.getProperty("java.io.tmpdir"), "excel-xml-export-test-${UUID.randomUUID()}")
        testRoot.mkdirs()
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val filesOnlyContext = FilesOnlyContext(appContext, testRoot)
        repository = MarkdownRepository(filesOnlyContext)
        useCase = MonthlyExcelXmlExportUseCase(filesOnlyContext, repository)
    }

    @Test
    fun buildSpreadsheetXml_shouldContainTwoWorksheetsAndHeaders() {
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 4, 1, 9, 0),
                type = EntryType.EXPENSE,
                member = MemberGroup.ALL,
                category = "餐饮",
                amount = "12.00".toBigDecimal(),
                note = "早餐",
            )
        )

        val xml = useCase.buildSpreadsheetXml(YearMonth.of(2026, 4), null)

        assertTrue(xml.contains("""<Worksheet ss:Name="支出">"""))
        assertTrue(xml.contains("""<Worksheet ss:Name="收入">"""))
        assertTrue(xml.contains("""<Data ss:Type="String">日期</Data>"""))
        assertTrue(xml.contains("""<Data ss:Type="String">金额</Data>"""))
    }

    @Test
    fun buildSpreadsheetXml_shouldApplyMemberFilterAndSortRowsAscending() {
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 4, 2, 12, 0),
                type = EntryType.EXPENSE,
                member = MemberGroup.XIAOXIN,
                category = "餐饮",
                amount = "20.00".toBigDecimal(),
                note = "午餐",
            )
        )
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 4, 1, 8, 0),
                type = EntryType.EXPENSE,
                member = MemberGroup.XIAOXIN,
                category = "交通",
                amount = "8.00".toBigDecimal(),
                note = "地铁",
            )
        )
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 4, 1, 18, 0),
                type = EntryType.INCOME,
                member = MemberGroup.XIAOXIN,
                category = "奖金",
                amount = "100.00".toBigDecimal(),
                note = "奖励",
            )
        )
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 4, 1, 9, 0),
                type = EntryType.EXPENSE,
                member = MemberGroup.JIELI,
                category = "购物",
                amount = "50.00".toBigDecimal(),
                note = "零食",
            )
        )

        val xml = useCase.buildSpreadsheetXml(YearMonth.of(2026, 4), MemberGroup.XIAOXIN)

        val firstExpenseIndex = xml.indexOf("2026-04-01")
        val secondExpenseIndex = xml.indexOf("2026-04-02")
        assertTrue(firstExpenseIndex in 0 until secondExpenseIndex)
        assertTrue(xml.contains(">XS<"))
        assertFalse(xml.contains(">JL<"))
        assertTrue(xml.contains(">奖金<"))
        assertTrue(xml.contains(">交通<"))
    }

    @Test
    fun buildSpreadsheetXml_shouldEscapeXmlCharacters() {
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 4, 1, 9, 0),
                type = EntryType.EXPENSE,
                member = MemberGroup.ALL,
                category = "餐饮&交通",
                amount = "12.00".toBigDecimal(),
                note = "<测试>\"'\"",
            )
        )

        val xml = useCase.buildSpreadsheetXml(YearMonth.of(2026, 4), null)

        assertTrue(xml.contains("餐饮&amp;交通"))
        assertTrue(xml.contains("&lt;测试&gt;&quot;&apos;&quot;"))
    }

    private fun entry(
        dateTime: LocalDateTime,
        type: EntryType,
        member: MemberGroup,
        category: String,
        amount: BigDecimal,
        note: String,
    ): LedgerEntry {
        return LedgerEntry(
            dateTime = dateTime,
            type = type,
            amount = amount,
            member = member,
            category = category,
            note = note,
        )
    }

    private class FilesOnlyContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getFilesDir(): File = root
    }
}
