package com.example.pocketledgermd.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun EditEntryDialog(
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
    var memberExpanded by remember { mutableStateOf(false) }
    var editMember by remember(entry) { mutableStateOf(entry.member) }
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

                OutlinedButton(onClick = { memberExpanded = true }) {
                    Text("对象: ${editMember.label}")
                }
                DropdownMenu(expanded = memberExpanded, onDismissRequest = { memberExpanded = false }) {
                    vm.memberGroups.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.label) },
                            onClick = {
                                editMember = member
                                memberExpanded = false
                            }
                        )
                    }
                }

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
                        newMember = editMember,
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
