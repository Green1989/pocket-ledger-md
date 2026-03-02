package com.example.pocketledgermd.ui

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
import androidx.compose.ui.unit.dp
import com.example.pocketledgermd.data.EntryType
import com.example.pocketledgermd.data.LedgerEntry
import com.example.pocketledgermd.data.displayCategoryText
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

@Composable
internal fun EntryCard(
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
                Text("分类: ${e.displayCategoryText()}")
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
