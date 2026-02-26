package com.example.pocketledgermd.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.input.KeyboardType

private fun DateFilter.displayLabel(): String = when (this) {
    DateFilter.TODAY -> "今天"
    DateFilter.WEEK -> "本周"
    DateFilter.MONTH -> "本月"
    DateFilter.YEAR -> "今年"
    DateFilter.ALL -> "全部"
}

@Composable
fun LedgerScreen(vm: LedgerViewModel) {
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    LazyColumn(
        modifier = Modifier
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(1) { SummaryCard(vm) }
        items(1) { EntryForm(vm) }
        items(1) { MonthNavigator(vm) }
        items(1) { FilterBar(vm) }
        if (vm.statusMessage.isNotBlank()) {
            items(1) { Text(vm.statusMessage, color = MaterialTheme.colorScheme.primary) }
        }
        items(1) { Text("记录", style = MaterialTheme.typography.titleMedium) }
        items(vm.entries) { e ->
            EntryCard(
                e = e,
                onEdit = { editingEntry = it },
                onDelete = { deletingEntry = it },
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
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("删除记录") },
            text = { Text("确认删除这条记录？删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteExistingEntry(deletingEntry!!)
                        deletingEntry = null
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SummaryCard(vm: LedgerViewModel) {
    val s = vm.summary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("当前筛选汇总", fontWeight = FontWeight.Bold)
            Text("收入: ${s.totalIncome.setScale(2, RoundingMode.HALF_UP)}")
            Text("支出: ${s.totalExpense.setScale(2, RoundingMode.HALF_UP)}")
            Text("结余: ${s.balance.setScale(2, RoundingMode.HALF_UP)}")
        }
    }
}

@Composable
private fun MonthNavigator(vm: LedgerViewModel) {
    val monthText = vm.selectedMonth.toString()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = { vm.previousMonth() }) {
                Text("上月")
            }
            Text(monthText, modifier = Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { vm.nextMonth() },
                enabled = vm.selectedMonth < YearMonth.now(),
            ) {
                Text("下月")
            }
        }
    }
}

@Composable
private fun FilterBar(vm: LedgerViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            val filters = listOf(
                DateFilter.TODAY,
                DateFilter.WEEK,
                DateFilter.MONTH,
                DateFilter.YEAR,
                DateFilter.ALL,
            )
            filters.forEachIndexed { index, filter ->
                OutlinedButton(
                    onClick = { vm.updateFilter(filter) },
                    enabled = vm.selectedFilter != filter,
                ) {
                    Text(filter.displayLabel())
                }
                if (index != filters.lastIndex) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EntryForm(vm: LedgerViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateTimeText = vm.selectedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("快速记账", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    RadioButton(
                        selected = vm.selectedType == EntryType.EXPENSE,
                        onClick = { vm.updateEntryType(EntryType.EXPENSE) },
                    )
                    Text("支出", modifier = Modifier.padding(top = 12.dp))
                }
                Row {
                    RadioButton(
                        selected = vm.selectedType == EntryType.INCOME,
                        onClick = { vm.updateEntryType(EntryType.INCOME) },
                    )
                    Text("收入", modifier = Modifier.padding(top = 12.dp))
                }
            }

            OutlinedTextField(
                value = vm.amountInput,
                onValueChange = { vm.updateAmountInput(it) },
                label = { Text("金额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Text("记账时间: $dateTimeText")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setEntryDateTimeToNow() }) {
                    Text("今天/现在")
                }
                OutlinedButton(
                    onClick = {
                        val current = vm.selectedDateTime
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                vm.updateEntryDate(year, month + 1, dayOfMonth)
                                val afterDate = vm.selectedDateTime
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        vm.updateEntryTime(hour, minute)
                                    },
                                    afterDate.hour,
                                    afterDate.minute,
                                    true,
                                ).show()
                            },
                            current.year,
                            current.monthValue - 1,
                            current.dayOfMonth,
                        ).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }
                ) {
                    Text("选择日期时间")
                }
            }

            OutlinedButton(onClick = { expanded = true }) {
                Text("分类: ${vm.selectedCategory}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                vm.availableCategories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c) },
                        onClick = {
                            vm.selectedCategory = c
                            expanded = false
                        }
                    )
                }
            }

            OutlinedTextField(
                value = vm.noteInput,
                onValueChange = { vm.noteInput = it },
                label = { Text("备注(可选)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = { vm.saveEntry() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}

private fun sanitizeAmountInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val normalized = if (filtered.startsWith(".")) "0$filtered" else filtered
    val parts = normalized.split('.', limit = 3)
    return when {
        parts.size == 1 -> parts[0]
        parts.size >= 2 -> "${parts[0]}.${parts[1].take(2)}"
        else -> ""
    }
}

@Composable
private fun EditEntryDialog(
    entry: LedgerEntry,
    vm: LedgerViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var editDateTime by remember(entry) { mutableStateOf(entry.dateTime) }
    var editType by remember(entry) { mutableStateOf(entry.type) }
    var editAmount by remember(entry) { mutableStateOf(entry.amount.toPlainString()) }
    var editNote by remember(entry) { mutableStateOf(entry.note) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var editCategory by remember(entry) { mutableStateOf(entry.category) }

    val categories = vm.categoriesForType(editType)
    if (editCategory !in categories) {
        editCategory = categories.first()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("时间: ${editDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    editDateTime = LocalDateTime.of(
                                        year,
                                        month + 1,
                                        dayOfMonth,
                                        editDateTime.hour,
                                        editDateTime.minute,
                                    )
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            editDateTime = LocalDateTime.of(
                                                editDateTime.year,
                                                editDateTime.monthValue,
                                                editDateTime.dayOfMonth,
                                                hour,
                                                minute,
                                            )
                                        },
                                        editDateTime.hour,
                                        editDateTime.minute,
                                        true,
                                    ).show()
                                },
                                editDateTime.year,
                                editDateTime.monthValue - 1,
                                editDateTime.dayOfMonth,
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }
                    ) {
                        Text("选择日期时间")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        RadioButton(
                            selected = editType == EntryType.EXPENSE,
                            onClick = { editType = EntryType.EXPENSE },
                        )
                        Text("支出", modifier = Modifier.padding(top = 12.dp))
                    }
                    Row {
                        RadioButton(
                            selected = editType == EntryType.INCOME,
                            onClick = { editType = EntryType.INCOME },
                        )
                        Text("收入", modifier = Modifier.padding(top = 12.dp))
                    }
                }

                OutlinedTextField(
                    value = editAmount,
                    onValueChange = { editAmount = sanitizeAmountInput(it) },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                OutlinedButton(onClick = { categoryExpanded = true }) {
                    Text("分类: $editCategory")
                }
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c) },
                            onClick = {
                                editCategory = c
                                categoryExpanded = false
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = { Text("备注(可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.updateExistingEntry(
                        original = entry,
                        newDateTime = editDateTime,
                        newType = editType,
                        newAmountInput = editAmount,
                        newCategory = editCategory,
                        newNote = editNote,
                    )
                    onSaved()
                }
            ) {
                Text("保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun EntryCard(
    e: LedgerEntry,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${e.dateTime.format(dt)}  ${if (e.type == EntryType.EXPENSE) "支出" else "收入"}")
                Text("金额: ${e.amount.setScale(2, RoundingMode.HALF_UP)}")
                Text("分类: ${e.category}")
                if (e.note.isNotBlank()) {
                    Text("备注: ${e.note}")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEdit(e) }) {
                    Text("编辑")
                }
                OutlinedButton(onClick = { onDelete(e) }) {
                    Text("删除")
                }
            }
        }
    }
}
