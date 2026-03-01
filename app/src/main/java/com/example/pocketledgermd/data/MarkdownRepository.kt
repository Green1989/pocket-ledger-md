package com.example.pocketledgermd.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

data class FileTransferResult(
    val success: Boolean,
    val message: String,
    val totalFiles: Int = 0,
    val validFiles: Int = 0,
    val skippedFiles: Int = 0,
)

data class SyncImportResult(
    val success: Boolean,
    val message: String,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
)

class MarkdownRepository(private val context: Context) {
    private val ledgerDir: File by lazy {
        File(context.filesDir, "ledger").apply { mkdirs() }
    }
    private val monthFilePattern = Regex("""^\d{4}-\d{2}\.md$""")

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
                    val day = currentDay
                    val parsed = MarkdownCodec.parseEntryLine(day, line) ?: continue
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

    fun deleteEntry(entry: LedgerEntry) {
        val target = ensureEntryHasId(entry)
        val month = YearMonth.from(target.dateTime)
        removeEntry(month, target)
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
                    val day = currentDay
                    val parsed = MarkdownCodec.parseEntryLine(day, line) ?: continue
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
            a.member == b.member &&
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
        return ledgerDir
            .listFiles()
            ?.filter { it.isFile && monthFilePattern.matches(it.name) }
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

    fun importEntries(entries: List<LedgerEntry>): SyncImportResult {
        if (entries.isEmpty()) {
            return SyncImportResult(
                success = true,
                message = "同步完成",
                importedCount = 0,
                skippedCount = 0,
            )
        }

        val existingIds = loadAll()
            .mapNotNull { it.id }
            .toMutableSet()

        var imported = 0
        var skipped = 0

        entries.sortedBy { it.dateTime }.forEach { raw ->
            val id = raw.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            if (id in existingIds) {
                skipped++
                return@forEach
            }
            saveEntry(raw.copy(id = id))
            existingIds.add(id)
            imported++
        }

        return SyncImportResult(
            success = true,
            message = "同步完成",
            importedCount = imported,
            skippedCount = skipped,
        )
    }

    fun backupToExternalTree(treeUri: Uri): FileTransferResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return FileTransferResult(false, "无法打开目标目录")
        val targetLedgerDir = root.findFile("ledger")
            ?: root.createDirectory("ledger")
            ?: return FileTransferResult(false, "无法创建 ledger 目录")

        val sourceFiles = listMonthFiles()
        var successCount = 0
        var skippedCount = 0

        for (file in sourceFiles) {
            try {
                val existing = targetLedgerDir.findFile(file.name)
                if (existing != null && existing.isFile) {
                    existing.delete()
                }
                val target = targetLedgerDir.createFile("text/markdown", file.name)
                if (target == null) {
                    skippedCount++
                    continue
                }

                context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                    if (output == null) {
                        skippedCount++
                        return@use
                    }
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                    successCount++
                }
            } catch (_: Exception) {
                skippedCount++
            }
        }

        return FileTransferResult(
            success = true,
            message = "备份完成",
            totalFiles = sourceFiles.size,
            validFiles = successCount,
            skippedFiles = skippedCount,
        )
    }

    fun restoreFromExternalTree(treeUri: Uri): FileTransferResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return FileTransferResult(false, "无法打开源目录")

        val sourceRoot = when {
            root.findFile("ledger")?.isDirectory == true -> root.findFile("ledger")!!
            else -> root
        }

        val sourceFiles = sourceRoot.listFiles().filter { it.isFile }
        val validSourceFiles = sourceFiles.filter { monthFilePattern.matches(it.name.orEmpty()) }
        if (validSourceFiles.isEmpty()) {
            return FileTransferResult(false, "未找到可还原的月度 markdown 文件")
        }

        try {
            createLocalSnapshot()
            clearLocalMonthFiles()

            var restoredCount = 0
            var skippedCount = 0
            for (doc in validSourceFiles) {
                try {
                    val name = doc.name
                    if (name == null) {
                        skippedCount++
                        continue
                    }
                    val target = monthFile(YearMonth.parse(name.removeSuffix(".md")))
                    context.contentResolver.openInputStream(doc.uri).use { input ->
                        if (input == null) {
                            skippedCount++
                            return@use
                        }
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        restoredCount++
                    }
                } catch (_: Exception) {
                    skippedCount++
                }
            }

            return FileTransferResult(
                success = true,
                message = "还原完成",
                totalFiles = sourceFiles.size,
                validFiles = restoredCount,
                skippedFiles = skippedCount + (sourceFiles.size - validSourceFiles.size),
            )
        } catch (e: Exception) {
            return FileTransferResult(false, "还原失败: ${e.message}")
        }
    }

    private fun createLocalSnapshot() {
        val backupName = "ledger_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupDir = File(context.filesDir, backupName)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IOException("无法创建本地快照目录")
        }

        for (file in listMonthFiles()) {
            val target = File(backupDir, file.name)
            file.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun clearLocalMonthFiles() {
        listMonthFiles().forEach { it.delete() }
    }

    private fun monthFile(ym: YearMonth): File = File(ledgerDir, "${ym}.md")
}
