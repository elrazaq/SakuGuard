package com.sakuguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sakuguard.app.domain.model.WalletType

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: WalletType,
    val openingBalance: Long,
    val trackingStartDate: Long,
    val description: String,
    val colorHex: Long?,
    val iconKey: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
