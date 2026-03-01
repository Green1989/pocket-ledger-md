package com.example.pocketledgermd.data

import java.math.BigDecimal
import java.time.LocalDateTime

enum class EntryType {
    EXPENSE,
    INCOME,
}

enum class MemberGroup(val code: String, val label: String) {
    XIAOXIN("xiaoxin", "少鑫"),
    JIELI("jieli", "洁丽"),
    TONGTONG("tongtong", "童童"),
    ELDER("elder", "老人"),
    ALL("all", "所有人");

    companion object {
        fun fromCode(code: String?): MemberGroup {
            if (code.isNullOrBlank()) return ALL
            return values().firstOrNull { it.code == code } ?: ALL
        }
    }
}

data class LedgerEntry(
    val id: String? = null,
    val dateTime: LocalDateTime,
    val type: EntryType,
    val amount: BigDecimal,
    val member: MemberGroup = MemberGroup.ALL,
    val category: String,
    val note: String,
)

data class MonthSummary(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val balance: BigDecimal,
)

fun LedgerEntry.displayCategoryText(): String {
    return if (member == MemberGroup.ALL) category else "${member.label}$category"
}
