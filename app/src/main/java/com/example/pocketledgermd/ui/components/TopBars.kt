package com.example.pocketledgermd.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.RoundingMode
import java.time.YearMonth

private fun DateFilter.displayLabel(): String = when (this) {
    DateFilter.TODAY -> "今天"
    DateFilter.WEEK -> "本周"
    DateFilter.MONTH -> "本月"
    DateFilter.YEAR -> "今年"
    DateFilter.ALL -> "全部"
}

@Composable
internal fun BackupRestoreBar(onBackup: () -> Unit, onRestore: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onBackup) {
                Text("备份数据")
            }
            OutlinedButton(onClick = onRestore) {
                Text("还原数据")
            }
        }
    }
}

@Composable
internal fun SummaryCard(vm: LedgerViewModel) {
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
internal fun MonthNavigator(vm: LedgerViewModel) {
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
internal fun FilterBar(vm: LedgerViewModel) {
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
internal fun MemberFilterBar(vm: LedgerViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("成员视图", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                vm.memberFilterOptions.forEachIndexed { index, filter ->
                    OutlinedButton(
                        onClick = { vm.updateMemberFilter(filter) },
                        enabled = vm.selectedMemberFilter != filter,
                    ) {
                        Text(filter.label)
                    }
                    if (index != vm.memberFilterOptions.lastIndex) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}
