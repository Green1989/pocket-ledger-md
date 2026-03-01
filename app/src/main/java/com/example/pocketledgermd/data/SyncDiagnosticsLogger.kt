package com.example.pocketledgermd.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SyncDiagnosticsLogger(context: Context) {
    private val logsDir: File = File(context.filesDir, "sync_diagnostics").apply { mkdirs() }
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Synchronized
    fun writeFailureLog(
        stage: String,
        reason: String,
        rawPayload: String,
        throwable: Throwable? = null,
        extra: String? = null,
    ): File {
        val day = LocalDate.now().toString()
        val file = File(logsDir, "sync-$day.log")
        val now = LocalDateTime.now().format(timestampFormatter)
        val payloadPreview = rawPayload.take(MAX_PAYLOAD_PREVIEW_CHARS)
        val payloadHash = sha256(rawPayload)
        val stack = throwable?.let { throwableToString(it) }

        val text = buildString {
            appendLine("===== SYNC FAILURE =====")
            appendLine("time: $now")
            appendLine("stage: $stage")
            appendLine("reason: $reason")
            appendLine("payload_length: ${rawPayload.length}")
            appendLine("payload_sha256: $payloadHash")
            if (!extra.isNullOrBlank()) {
                appendLine("extra: $extra")
            }
            appendLine("payload_preview_begin")
            appendLine(payloadPreview)
            if (rawPayload.length > MAX_PAYLOAD_PREVIEW_CHARS) {
                appendLine("...(truncated)")
            }
            appendLine("payload_preview_end")
            if (stack != null) {
                appendLine("stacktrace_begin")
                appendLine(stack)
                appendLine("stacktrace_end")
            }
            appendLine()
        }

        file.appendText(text, Charsets.UTF_8)
        return file
    }

    @Synchronized
    fun hasAnyLogs(): Boolean {
        return logsDir.listFiles()?.any { it.isFile } == true
    }

    @Synchronized
    fun readLatestLog(): String? {
        val latest = logsDir
            .listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { latest.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun throwableToString(throwable: Throwable): String {
        val writer = StringWriter()
        val printer = PrintWriter(writer)
        throwable.printStackTrace(printer)
        printer.flush()
        return writer.toString()
    }

    companion object {
        private const val MAX_PAYLOAD_PREVIEW_CHARS = 4000
    }
}
