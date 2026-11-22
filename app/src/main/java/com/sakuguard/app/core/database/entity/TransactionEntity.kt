package com.sakuguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sakuguard.app.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationWalletId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("walletId"),
        Index("destinationWalletId"),
        Index("categoryId"),
        Index("goalId"),
        Index("transactionDate"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: TransactionType,
    val amount: Long,
    val walletId: String,
    val destinationWalletId: String?,
    val categoryId: String?,
    val goalId: String?,
    val description: String,
    val notes: String,
    val transactionDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
