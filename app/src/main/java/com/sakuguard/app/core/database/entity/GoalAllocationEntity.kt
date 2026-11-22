package com.sakuguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.WithdrawalTarget

@Entity(
    tableName = "goal_allocations",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedExpenseTransactionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("goalId"),
        Index("walletId"),
        Index("transactionId"),
        Index("linkedExpenseTransactionId"),
    ],
)
data class GoalAllocationEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val walletId: String,
    val transactionId: String,
    val linkedExpenseTransactionId: String?,
    val entryType: GoalLedgerType,
    val amount: Long,
    val reason: String?,
    val withdrawalTarget: WithdrawalTarget?,
    val createdAt: Long,
)
