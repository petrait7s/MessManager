package com.messmanager.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All money values are Long poisha (1 Tk = 100 poisha). Dates are epoch days. Month key = "yyyy-MM". */

@Entity(tableName = "mess")
data class Mess(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val address: String = "",
    val managerName: String,
    val createdAt: Long,
)

@Entity(tableName = "member")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val joinDay: Long,
    /** month key from which the member no longer participates; null = active */
    val inactiveFrom: String? = null,
    val shareWeight: Int = 1,
    val createdAt: Long = 0,
) {
    fun isActive(): Boolean = inactiveFrom == null
}

@Entity(
    tableName = "expense",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["payerId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("month"), Index("payerId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val day: Long,
    val category: String,
    val amount: Long,
    val payerId: Long?,
    val description: String,
    val note: String = "",
    val splitMode: String = "EQUAL", // EQUAL | CUSTOM | SHARES
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "expense_split",
    foreignKeys = [
        ForeignKey(entity = Expense::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"]),
    ],
    indices = [Index("expenseId"), Index("memberId")],
)
data class ExpenseSplit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val memberId: Long,
    val amount: Long,
    val shares: Int = 1,
)

/** Settlement payment from a member (kind PAYMENT) or refund to a member (kind REFUND). */
@Entity(
    tableName = "payment",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"])],
    indices = [Index("month"), Index("memberId")],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val day: Long,
    val memberId: Long,
    val amount: Long,
    val kind: String = "PAYMENT",
    val note: String = "",
    val createdAt: Long,
)

/** Money a member put into the mess fund. Not an expense. */
@Entity(
    tableName = "deposit",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"])],
    indices = [Index("month"), Index("memberId")],
)
data class Deposit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val day: Long,
    val memberId: Long,
    val amount: Long,
    val note: String = "",
    val createdAt: Long,
)

@Entity(
    tableName = "bazar_entry",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["purchasedById"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("month"), Index("day"), Index("purchasedById")],
)
data class BazarEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val day: Long,
    val purchasedById: Long?,
    val note: String = "",
    val receipt: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "bazar_item",
    foreignKeys = [ForeignKey(entity = BazarEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class BazarItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val name: String,
    val qtyMilli: Long,
    val unit: String,
    val unitPrice: Long,
    val total: Long,
)

@Entity(
    tableName = "bazar_duty",
    foreignKeys = [ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"])],
    indices = [Index("month"), Index(value = ["day"], unique = true), Index("memberId")],
)
data class BazarDuty(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val day: Long,
    val memberId: Long,
    val status: String = "PENDING", // PENDING | COMPLETED | SKIPPED
    val note: String = "",
)

@Entity(
    tableName = "note",
    foreignKeys = [
        ForeignKey(entity = Member::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Expense::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("memberId"), Index("expenseId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val day: Long,
    val memberId: Long? = null,
    val expenseId: Long? = null,
    val month: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "monthly_cycle")
data class MonthlyCycle(
    @PrimaryKey val month: String,
    val status: String = "OPEN", // OPEN | CLOSED
    val closedAt: Long? = null,
    val snapshotJson: String = "",
    val reopenCount: Int = 0,
)

@Entity(tableName = "audit_log", indices = [Index("month")])
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val action: String,
    val entity: String,
    val entityId: Long = 0,
    val detail: String = "",
    val month: String = "",
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val key: String,
    val value: String,
)
