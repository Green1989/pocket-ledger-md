package com.example.pocketledgermd.ui

import com.example.pocketledgermd.data.MarkdownRepository
import com.example.pocketledgermd.data.ShareSyncCodec
import com.example.pocketledgermd.data.SyncDiagnosticsLogger
import com.example.pocketledgermd.data.SyncParseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ClipboardSyncOutcome(
    val statusMessage: String,
    val shouldReloadSelectedMonth: Boolean,
    val hasNewDiagnostics: Boolean,
)

internal class ClipboardSyncUseCase(
    private val repository: MarkdownRepository,
    private val diagnosticsLogger: SyncDiagnosticsLogger,
) {
    suspend fun execute(rawText: String): ClipboardSyncOutcome {
        return try {
            val parsed = withContext(Dispatchers.Default) {
                ShareSyncCodec.parse(rawText)
            }
            when (parsed) {
                is SyncParseResult.NotFound -> {
                    val logName = recordFailure(
                        stage = "parse",
                        reason = "未检测到同步数据块",
                        rawText = rawText,
                    )
                    ClipboardSyncOutcome(
                        statusMessage = "未检测到可同步记账内容（已记录 $logName）",
                        shouldReloadSelectedMonth = false,
                        hasNewDiagnostics = true,
                    )
                }

                is SyncParseResult.Invalid -> {
                    val logName = recordFailure(
                        stage = "parse",
                        reason = parsed.message,
                        rawText = rawText,
                    )
                    ClipboardSyncOutcome(
                        statusMessage = "同步失败：${parsed.message}（已记录 $logName）",
                        shouldReloadSelectedMonth = false,
                        hasNewDiagnostics = true,
                    )
                }

                is SyncParseResult.Success -> {
                    val result = withContext(Dispatchers.IO) {
                        repository.importEntries(parsed.payload.entries)
                    }
                    ClipboardSyncOutcome(
                        statusMessage = "${result.message}: 新增 ${result.importedCount} 条，跳过 ${result.skippedCount} 条",
                        shouldReloadSelectedMonth = true,
                        hasNewDiagnostics = false,
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val logName = recordFailure(
                stage = "import",
                reason = t.message ?: "未知错误",
                rawText = rawText,
                throwable = t,
            )
            ClipboardSyncOutcome(
                statusMessage = "同步失败：${t.message ?: "未知错误"}（已记录 $logName）",
                shouldReloadSelectedMonth = false,
                hasNewDiagnostics = true,
            )
        }
    }

    private suspend fun recordFailure(
        stage: String,
        reason: String,
        rawText: String,
        throwable: Throwable? = null,
    ): String {
        val file = withContext(Dispatchers.IO) {
            diagnosticsLogger.writeFailureLog(
                stage = stage,
                reason = reason,
                rawPayload = rawText,
                throwable = throwable,
            )
        }
        return file.name
    }
}
