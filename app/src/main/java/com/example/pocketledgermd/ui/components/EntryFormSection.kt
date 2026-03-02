package com.example.pocketledgermd.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.EntryType
import java.time.format.DateTimeFormatter

@Composable
internal fun EntryForm(vm: LedgerViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var memberExpanded by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var showShareDaysDialog by remember { mutableStateOf(false) }
    var shareDaysInput by remember { mutableStateOf("1") }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                OutlinedButton(
                    onClick = {
                        val clipboardText = clipboardManager.getText()?.text?.toString().orEmpty()
                        vm.syncFromClipboardText(clipboardText)
                    }
                ) {
                    Text("同步")
                }
            }
            if (vm.hasSyncDiagnostics) {
                OutlinedButton(
                    onClick = {
                        val diagnosticsText = vm.readLatestSyncDiagnostics()
                        if (diagnosticsText.isNullOrBlank()) {
                            vm.statusMessage = "暂无同步诊断日志"
                        } else {
                            clipboardManager.setText(AnnotatedString(diagnosticsText))
                            vm.statusMessage = "已复制同步诊断日志"
                        }
                    }
                ) {
                    Text("复制诊断日志")
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { memberExpanded = true }) {
                    Text("对象: ${vm.selectedMember.label}")
                }
                OutlinedButton(onClick = { expanded = true }) {
                    Text("分类: ${vm.selectedCategory}")
                }
                ShareActionButton(
                    onClick = {
                        val shareText = vm.buildTodayShareText(1)
                        clipboardManager.setText(AnnotatedString(shareText))
                        vm.statusMessage = "已复制当天记账内容"
                    },
                    onLongClick = {
                        shareDaysInput = vm.getLastCustomShareDays().toString()
                        showShareDaysDialog = true
                    },
                )
            }
            DropdownMenu(expanded = memberExpanded, onDismissRequest = { memberExpanded = false }) {
                vm.memberGroups.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(member.label) },
                        onClick = {
                            vm.updateEntryMember(member)
                            memberExpanded = false
                        }
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                vm.availableCategories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c) },
                        onClick = {
                            vm.updateEntryCategory(c)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ 新增分类") },
                    onClick = {
                        expanded = false
                        newCategoryInput = ""
                        showAddCategoryDialog = true
                    }
                )
            }

            OutlinedTextField(
                value = vm.noteInput,
                onValueChange = { vm.updateNoteInput(it) },
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

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("新增分类") },
            text = {
                OutlinedTextField(
                    value = newCategoryInput,
                    onValueChange = { newCategoryInput = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (vm.tryAddCustomCategory(newCategoryInput)) {
                            showAddCategoryDialog = false
                            newCategoryInput = ""
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showShareDaysDialog) {
        AlertDialog(
            onDismissRequest = { showShareDaysDialog = false },
            title = { Text("分享天数") },
            text = {
                OutlinedTextField(
                    value = shareDaysInput,
                    onValueChange = { shareDaysInput = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("天数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val days = shareDaysInput.toIntOrNull()
                        if (days == null || days <= 0) {
                            vm.statusMessage = "分享天数必须大于 0"
                            return@TextButton
                        }
                        vm.persistLastCustomShareDays(days)
                        val shareText = vm.buildTodayShareText(days)
                        clipboardManager.setText(AnnotatedString(shareText))
                        vm.statusMessage = "已复制${days}天记账内容"
                        showShareDaysDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDaysDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShareActionButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text("分享")
    }
}
