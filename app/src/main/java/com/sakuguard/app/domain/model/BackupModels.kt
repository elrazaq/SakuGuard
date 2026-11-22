package com.sakuguard.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupRecordCounts(
    val wallets: Int,
    val categories: Int,
    val goals: Int,
    val transactions: Int,
    val goalAllocations: Int,
    val budgets: Int,
)

@Serializable
data class BackupManifest(
    val backupFormatVersion: Int,
    val appVersionCode: Int,
    val appVersionName: String,
    val databaseSchemaVersion: Int,
    val createdAtEpochMillis: Long,
    val encrypted: Boolean,
    val kdfAlgorithm: String? = null,
    val kdfIterations: Int? = null,
    val saltBase64: String? = null,
    val ivBase64: String? = null,
    val cipherAlgorithm: String? = null,
    val payloadSha256: String,
    val recordCounts: BackupRecordCounts,
)

@Serializable
data class SerializableWallet(
    val id: String,
    val name: String,
    val type: WalletType,
    val openingBalance: Long,
    val trackingStartDate: Long,
    val description: String = "",
    val colorHex: Long? = null,
    val iconKey: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SerializableCategory(
    val id: String,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class SerializableGoal(
    val id: String,
    val name: String,
    val targetAmount: Long,
    val deadlineEpochMillis: Long? = null,
    val description: String = "",
    val iconKey: String? = null,
    val colorHex: Long? = null,
    val priority: Int = 0,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val protectionMode: ProtectionMode = ProtectionMode.STANDARD,
    val createdAt: Long,
    val completedAt: Long? = null,
    val updatedAt: Long,
)

@Serializable
data class SerializableTransaction(
    val id: String,
    val type: TransactionType,
    val amount: Long,
    val walletId: String,
    val destinationWalletId: String? = null,
    val categoryId: String? = null,
    val goalId: String? = null,
    val description: String = "",
    val notes: String = "",
    val transactionDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SerializableGoalAllocation(
    val id: String,
    val goalId: String,
    val walletId: String,
    val transactionId: String,
    val linkedExpenseTransactionId: String? = null,
    val entryType: GoalLedgerType,
    val amount: Long,
    val reason: String? = null,
    val withdrawalTarget: WithdrawalTarget? = null,
    val createdAt: Long,
)

@Serializable
data class SerializableBudget(
    val id: String,
    val name: String,
    val categoryId: String,
    val limitAmount: Long,
    val periodType: BudgetPeriodType,
    val startDate: Long,
    val endDateExclusive: Long,
    val alert80Enabled: Boolean = true,
    val alert100Enabled: Boolean = true,
    val lastAlertedPeriodKey: String? = null,
    val lastAlertedLevel: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupPayload(
    val wallets: List<SerializableWallet>,
    val categories: List<SerializableCategory>,
    val goals: List<SerializableGoal>,
    val transactions: List<SerializableTransaction>,
    val goalAllocations: List<SerializableGoalAllocation>,
    val budgets: List<SerializableBudget>,
)

fun Wallet.toSerializable() = SerializableWallet(
    id, name, type, openingBalance, trackingStartDate, description, colorHex, iconKey, isArchived, createdAt, updatedAt,
)

fun SerializableWallet.toDomain() = Wallet(
    id, name, type, openingBalance, trackingStartDate, description, colorHex, iconKey, isArchived, createdAt, updatedAt,
)

fun Category.toSerializable() = SerializableCategory(
    id, name, type, iconKey, colorHex, isDefault, isArchived, createdAt,
)

fun SerializableCategory.toDomain() = Category(
    id, name, type, iconKey, colorHex, isDefault, isArchived, createdAt,
)

fun Goal.toSerializable() = SerializableGoal(
    id, name, targetAmount, deadlineEpochMillis, description, iconKey, colorHex, priority, status, protectionMode, createdAt, completedAt, updatedAt,
)

fun SerializableGoal.toDomain() = Goal(
    id, name, targetAmount, deadlineEpochMillis, description, iconKey, colorHex, priority, status, protectionMode, createdAt, completedAt, updatedAt,
)

fun Transaction.toSerializable() = SerializableTransaction(
    id, type, amount, walletId, destinationWalletId, categoryId, goalId, description, notes, transactionDate, createdAt, updatedAt,
)

fun SerializableTransaction.toDomain() = Transaction(
    id, type, amount, walletId, destinationWalletId, categoryId, goalId, description, notes, transactionDate, createdAt, updatedAt,
)

fun GoalAllocationEntry.toSerializable() = SerializableGoalAllocation(
    id, goalId, walletId, transactionId, linkedExpenseTransactionId, entryType, amount, reason, withdrawalTarget, createdAt,
)

fun SerializableGoalAllocation.toDomain() = GoalAllocationEntry(
    id, goalId, walletId, transactionId, linkedExpenseTransactionId, entryType, amount, reason, withdrawalTarget, createdAt,
)

fun Budget.toSerializable() = SerializableBudget(
    id, name, categoryId, limitAmount, periodType, startDate, endDateExclusive, alert80Enabled, alert100Enabled, lastAlertedPeriodKey, lastAlertedLevel, isArchived, createdAt, updatedAt,
)

fun SerializableBudget.toDomain() = Budget(
    id, name, categoryId, limitAmount, periodType, startDate, endDateExclusive, alert80Enabled, alert100Enabled, lastAlertedPeriodKey, lastAlertedLevel, isArchived, createdAt, updatedAt,
)

data class BackupInspectionResult(
    val manifest: BackupManifest,
)

data class BackupExportResult(
    val createdAtEpochMillis: Long,
    val recordCounts: BackupRecordCounts,
    val encrypted: Boolean,
)

data class BackupRestoreResult(
    val recordCounts: BackupRecordCounts,
    val createdAtEpochMillis: Long,
)

sealed class BackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class InvalidArchiveException(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class SizeLimitExceededException(message: String) : BackupException(message)
    class UnsupportedVersionException(message: String) : BackupException(message)
    class ChecksumMismatchException(message: String) : BackupException(message)
    class PasswordRequiredException(message: String) : BackupException(message)
    class WrongPasswordOrCorruptedException(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class IntegrityValidationException(message: String) : BackupException(message)
    class IoFailureException(message: String, cause: Throwable? = null) : BackupException(message, cause)
}
