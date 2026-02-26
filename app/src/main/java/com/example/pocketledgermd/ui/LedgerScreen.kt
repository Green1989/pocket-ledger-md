package com.example.pocketledgermd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.input.KeyboardType

private fun DateFilter.displayLabel(): String = when (this) {
    DateFilter.MONTH -> "本月"
    DateFilter.WEEK -> "本周"
    DateFilter.TODAY -> "今天"
}

@Composable
fun LedgerScreen(vm: LedgerViewModel) {
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
            EntryCard(e)
        }
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
        Row(modifier = Modifier.padding(12.dp)) {
            listOf(DateFilter.MONTH, DateFilter.WEEK, DateFilter.TODAY).forEachIndexed { index, filter ->
                OutlinedButton(
                    onClick = { vm.updateFilter(filter) },
                    enabled = vm.selectedFilter != filter,
                ) {
                    Text(filter.displayLabel())
                }
                if (index != 2) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EntryForm(vm: LedgerViewModel) {
    var expanded by remember { mutableStateOf(false) }

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

@Composable
private fun EntryCard(e: LedgerEntry) {
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${e.dateTime.format(dt)}  ${if (e.type == EntryType.EXPENSE) "支出" else "收入"}")
            Text("金额: ${e.amount.setScale(2, RoundingMode.HALF_UP)}")
            Text("分类: ${e.category}")
            if (e.note.isNotBlank()) {
                Text("备注: ${e.note}")
            }
        }
    }
}
