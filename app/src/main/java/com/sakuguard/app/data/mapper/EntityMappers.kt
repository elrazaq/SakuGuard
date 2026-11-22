package com.sakuguard.app.data.mapper

import com.sakuguard.app.core.database.entity.BudgetEntity
import com.sakuguard.app.core.database.entity.CategoryEntity
import com.sakuguard.app.core.database.entity.GoalAllocationEntity
import com.sakuguard.app.core.database.entity.GoalEntity
import com.sakuguard.app.core.database.entity.TransactionEntity
import com.sakuguard.app.core.database.entity.WalletEntity
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.Wallet

fun WalletEntity.toDomain(): Wallet = Wallet(
    id = id,
    name = name,
    type = type,
    openingBalance = openingBalance,
    trackingStartDate = trackingStartDate,
    description = description,
    colorHex = colorHex,
    iconKey = iconKey,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Wallet.toEntity(): WalletEntity = WalletEntity(
    id = id,
    name = name,
    type = type,
    openingBalance = openingBalance,
    trackingStartDate = trackingStartDate,
    description = description,
    colorHex = colorHex,
    iconKey = iconKey,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    iconKey = iconKey,
    colorHex = colorHex,
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = createdAt,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    iconKey = iconKey,
    colorHex = colorHex,
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = createdAt,
)

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    name = name,
    targetAmount = targetAmount,
    deadlineEpochMillis = deadlineEpochMillis,
    description = description,
    iconKey = iconKey,
    colorHex = colorHex,
    priority = priority,
    status = status,
    protectionMode = protectionMode,
    createdAt = createdAt,
    completedAt = completedAt,
    updatedAt = updatedAt,
)

fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    name = name,
    targetAmount = targetAmount,
    deadlineEpochMillis = deadlineEpochMillis,
    description = description,
    iconKey = iconKey,
    colorHex = colorHex,
    priority = priority,
    status = status,
    protectionMode = protectionMode,
    createdAt = createdAt,
    completedAt = completedAt,
    updatedAt = updatedAt,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    type = type,
    amount = amount,
    walletId = walletId,
    destinationWalletId = destinationWalletId,
    categoryId = categoryId,
    goalId = goalId,
    description = description,
    notes = notes,
    transactionDate = transactionDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type,
    amount = amount,
    walletId = walletId,
    destinationWalletId = destinationWalletId,
    categoryId = categoryId,
    goalId = goalId,
    description = description,
    notes = notes,
    transactionDate = transactionDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun GoalAllocationEntity.toDomain(): GoalAllocationEntry = GoalAllocationEntry(
    id = id,
    goalId = goalId,
    walletId = walletId,
    transactionId = transactionId,
    linkedExpenseTransactionId = linkedExpenseTransactionId,
    entryType = entryType,
    amount = amount,
    reason = reason,
    withdrawalTarget = withdrawalTarget,
    createdAt = createdAt,
)

fun GoalAllocationEntry.toEntity(): GoalAllocationEntity = GoalAllocationEntity(
    id = id,
    goalId = goalId,
    walletId = walletId,
    transactionId = transactionId,
    linkedExpenseTransactionId = linkedExpenseTransactionId,
    entryType = entryType,
    amount = amount,
    reason = reason,
    withdrawalTarget = withdrawalTarget,
    createdAt = createdAt,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    name = name,
    categoryId = categoryId,
    limitAmount = limitAmount,
    periodType = periodType,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    alert80Enabled = alert80Enabled,
    alert100Enabled = alert100Enabled,
    lastAlertedPeriodKey = lastAlertedPeriodKey,
    lastAlertedLevel = lastAlertedLevel,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    limitAmount = limitAmount,
    periodType = periodType,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    alert80Enabled = alert80Enabled,
    alert100Enabled = alert100Enabled,
    lastAlertedPeriodKey = lastAlertedPeriodKey,
    lastAlertedLevel = lastAlertedLevel,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
