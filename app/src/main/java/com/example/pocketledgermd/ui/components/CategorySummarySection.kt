package com.example.pocketledgermd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.EntryType
import java.math.RoundingMode

@Composable
internal fun CategoryAggregationTypeBar(
    selectedType: EntryType,
    onTypeSelected: (EntryType) -> Unit,
) {
    val title = if (selectedType == EntryType.EXPENSE) "分类支出" else "分类收入"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onTypeSelected(EntryType.EXPENSE) },
                    enabled = selectedType != EntryType.EXPENSE,
                ) {
                    Text("支出聚合")
                }
                OutlinedButton(
                    onClick = { onTypeSelected(EntryType.INCOME) },
                    enabled = selectedType != EntryType.INCOME,
                ) {
                    Text("收入聚合")
                }
            }
        }
    }
}

@Composable
internal fun ExpenseCategoryCard(
    summary: CategoryAggregationSummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary.categoryDisplay, fontWeight = FontWeight.Bold)
                Text("占比: ${summary.percentOfTypeTotal}%")
                Text("笔数: ${summary.itemCount}")
            }
            Text(
                "¥${summary.totalAmount.setScale(2, RoundingMode.HALF_UP)}",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun CategoryDetailHeader(
    categoryDisplay: String,
    onBack: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Text(categoryDisplay, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
