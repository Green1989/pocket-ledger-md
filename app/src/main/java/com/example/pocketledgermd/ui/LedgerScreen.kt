package com.example.pocketledgermd.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.LedgerEntry

@Composable
fun LedgerScreen(vm: LedgerViewModel) {
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var selectedCategoryDetail by remember { mutableStateOf<String?>(null) }
    var restorePendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.backupLedgerToDirectory(uri)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            restorePendingUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(1) { FilterBar(vm) }
        items(1) { SummaryCard(vm) }
        items(1) { EntryForm(vm) }
        items(1) { MemberFilterBar(vm) }
        items(1) { MonthNavigator(vm) }
        if (vm.statusMessage.isNotBlank()) {
            items(1) { Text(vm.statusMessage, color = MaterialTheme.colorScheme.primary) }
        }
        if (selectedCategoryDetail == null) {
            items(1) { Text("分类支出", style = MaterialTheme.typography.titleMedium) }
            val categorySummaries = vm.categoryAggregationSummaries()
            if (categorySummaries.isEmpty()) {
                items(1) { Text("当前范围暂无支出分类") }
            } else {
                items(categorySummaries, key = { it.categoryDisplay }) { summary ->
                    ExpenseCategoryCard(
                        summary = summary,
                        onClick = { selectedCategoryDetail = summary.categoryDisplay },
                    )
                }
            }
        } else {
            val categoryDisplay = selectedCategoryDetail!!
            items(1) {
                CategoryDetailHeader(
                    categoryDisplay = categoryDisplay,
                    onBack = { selectedCategoryDetail = null },
                )
            }
            val details = vm.entriesByCategory(categoryDisplay)
            if (details.isEmpty()) {
                items(1) { Text("该分类暂无明细") }
            } else {
                items(details) { e ->
                    EntryCard(
                        e = e,
                        onEdit = { editingEntry = it },
                        onDelete = { deletingEntry = it },
                    )
                }
            }
        }
        items(1) {
            BackupRestoreBar(
                onBackup = { backupLauncher.launch(null) },
                onRestore = { restoreLauncher.launch(null) },
            )
        }
    }

    if (editingEntry != null) {
        EditEntryDialog(
            entry = editingEntry!!,
            vm = vm,
            onDismiss = { editingEntry = null },
            onSaved = { editingEntry = null },
        )
    }

    if (deletingEntry != null) {
        DeleteEntryDialog(
            onDismiss = { deletingEntry = null },
            onConfirm = {
                vm.deleteExistingEntry(deletingEntry!!)
                deletingEntry = null
            },
        )
    }

    if (restorePendingUri != null) {
        RestoreLedgerDialog(
            onDismiss = { restorePendingUri = null },
            onConfirm = {
                vm.restoreLedgerFromDirectory(restorePendingUri!!)
                restorePendingUri = null
            },
        )
    }
}
