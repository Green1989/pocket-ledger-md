package com.example.pocketledgermd.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
class MarkdownRepositoryTest {
    private lateinit var testRoot: File
    private lateinit var repository: MarkdownRepository

    @Before
    fun setUp() {
        testRoot = File(System.getProperty("java.io.tmpdir"), "markdown-repo-test-${UUID.randomUUID()}")
        testRoot.mkdirs()
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        repository = MarkdownRepository(FilesOnlyContext(appContext, testRoot))
    }

    @Test
    fun updateEntry_shouldUpdateInSameMonth_whenDateInSameMonth() {
        val original = entry(
            dateTime = LocalDateTime.of(2026, 3, 2, 9, 30),
            type = EntryType.EXPENSE,
            amount = "10.00".toBigDecimal(),
            category = "餐饮",
            note = "早餐",
        )
        repository.saveEntry(original)
        val saved = repository.loadMonth(YearMonth.of(2026, 3)).first()

        val updated = saved.copy(
            amount = "25.50".toBigDecimal(),
            note = "早午餐",
        )
        repository.updateEntry(saved, updated)

        val monthEntries = repository.loadMonth(YearMonth.of(2026, 3))
        assertEquals(1, monthEntries.size)
        assertEquals(0, monthEntries.first().amount.compareTo("25.50".toBigDecimal()))
        assertEquals("早午餐", monthEntries.first().note)
        assertEquals(saved.id, monthEntries.first().id)
    }

    @Test
    fun updateEntry_shouldMoveAcrossMonths_whenDateChangesMonth() {
        val original = entry(
            dateTime = LocalDateTime.of(2026, 1, 15, 19, 0),
            type = EntryType.EXPENSE,
            amount = "30.00".toBigDecimal(),
            category = "交通",
            note = "打车",
        )
        repository.saveEntry(original)
        val saved = repository.loadMonth(YearMonth.of(2026, 1)).first()

        val moved = saved.copy(
            dateTime = LocalDateTime.of(2026, 2, 2, 8, 45),
            note = "地铁",
        )
        repository.updateEntry(saved, moved)

        assertTrue(repository.loadMonth(YearMonth.of(2026, 1)).isEmpty())
        val feb = repository.loadMonth(YearMonth.of(2026, 2))
        assertEquals(1, feb.size)
        assertEquals("地铁", feb.first().note)
        assertEquals(saved.id, feb.first().id)
    }

    @Test
    fun deleteEntry_shouldDeleteById_whenIdExists() {
        val e = entry(
            id = "id-to-delete",
            dateTime = LocalDateTime.of(2026, 3, 10, 12, 0),
            type = EntryType.EXPENSE,
            amount = "18.00".toBigDecimal(),
            category = "餐饮",
            note = "午餐",
        )
        repository.saveEntry(e)

        val before = repository.loadMonth(YearMonth.of(2026, 3))
        assertEquals(1, before.size)

        repository.deleteEntry(before.first())
        assertTrue(repository.loadMonth(YearMonth.of(2026, 3)).isEmpty())
    }

    @Test
    fun deleteEntry_shouldDeleteLegacyLine_whenEntryHasNoId() {
        val month = YearMonth.of(2026, 4)
        val file = monthFile(month)
        file.parentFile?.mkdirs()
        file.writeText(
            """
# 2026-04 Ledger

## 2026-04-20
- 07:30 | expense | 12.50 | 餐饮 | 早餐
            """.trimIndent() + "\n"
        )

        val target = entry(
            id = null,
            dateTime = LocalDateTime.of(2026, 4, 20, 7, 30),
            type = EntryType.EXPENSE,
            amount = "12.50".toBigDecimal(),
            category = "餐饮",
            note = "早餐",
        )
        repository.deleteEntry(target)

        assertTrue(repository.loadMonth(month).isEmpty())
    }

    @Test
    fun importEntries_shouldSkipDuplicateIds() {
        val e1 = entry(
            id = "dup-id-1",
            dateTime = LocalDateTime.of(2026, 5, 1, 8, 0),
            type = EntryType.EXPENSE,
            amount = "10.00".toBigDecimal(),
            category = "餐饮",
            note = "早餐",
        )
        val e2 = entry(
            id = "dup-id-1",
            dateTime = LocalDateTime.of(2026, 5, 1, 9, 0),
            type = EntryType.EXPENSE,
            amount = "20.00".toBigDecimal(),
            category = "餐饮",
            note = "加餐",
        )
        val e3 = entry(
            id = "unique-id-2",
            dateTime = LocalDateTime.of(2026, 5, 1, 10, 0),
            type = EntryType.INCOME,
            amount = "100.00".toBigDecimal(),
            category = "工资",
            note = "",
        )

        val result = repository.importEntries(listOf(e1, e2, e3))

        assertEquals(2, result.importedCount)
        assertEquals(1, result.skippedCount)
        val all = repository.loadAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == "dup-id-1" })
        assertTrue(all.any { it.id == "unique-id-2" })
    }

    @Test
    fun createLocalSnapshot_shouldCreateBackupDirectoryWithMonthFiles() {
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 6, 1, 9, 0),
                type = EntryType.EXPENSE,
                amount = "11.00".toBigDecimal(),
                category = "餐饮",
                note = "早餐",
            )
        )
        repository.saveEntry(
            entry(
                dateTime = LocalDateTime.of(2026, 7, 1, 9, 0),
                type = EntryType.INCOME,
                amount = "500.00".toBigDecimal(),
                category = "工资",
                note = "",
            )
        )

        val beforeNames = testRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
        val method = MarkdownRepository::class.java.getDeclaredMethod("createLocalSnapshot")
        method.isAccessible = true
        method.invoke(repository)

        val afterFiles = testRoot.listFiles().orEmpty().toList()
        val backupDirs = afterFiles
            .filter { it.isDirectory && it.name.startsWith("ledger_backup_") && it.name !in beforeNames }
        assertEquals(1, backupDirs.size)

        val backed = backupDirs.first().listFiles().orEmpty().map { it.name }.toSet()
        assertTrue(backed.contains("2026-06.md"))
        assertTrue(backed.contains("2026-07.md"))
    }

    private fun monthFile(ym: YearMonth): File {
        return File(File(testRoot, "ledger"), "${ym}.md")
    }

    private fun entry(
        id: String? = null,
        dateTime: LocalDateTime,
        type: EntryType,
        amount: BigDecimal,
        category: String,
        note: String,
    ): LedgerEntry {
        return LedgerEntry(
            id = id,
            dateTime = dateTime,
            type = type,
            amount = amount,
            category = category,
            note = note,
        )
    }

    private class FilesOnlyContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getFilesDir(): File = root
    }
}
