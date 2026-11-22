package com.sakuguard.app.domain.model

enum class WalletType {
    BANK_ACCOUNT,
    CASH,
    E_WALLET,
    OTHER,
}

enum class CategoryType {
    INCOME,
    EXPENSE,
}

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
    GOAL_ALLOCATION,
    GOAL_WITHDRAWAL,
}

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

enum class ProtectionMode {
    STANDARD,
    PROTECTED,
    STRICT,
}

enum class GoalLedgerType {
    ALLOCATION,
    WITHDRAWAL,
}

enum class WithdrawalTarget {
    RETURN_TO_FREE_MONEY,
    DIRECT_EXPENSE,
}

enum class BudgetPeriodType {
    MONTHLY,
    WEEKLY,
    CUSTOM,
}

enum class BudgetHealthStatus {
    ON_TRACK,
    WARNING,
    EXCEEDED,
}

data class Wallet(
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

data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
)

data class Goal(
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

data class Transaction(
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

data class GoalAllocationEntry(
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

data class WalletSummary(
    val wallet: Wallet,
    val currentBalance: Long,
    val allocatedFunds: Long,
    val freeMoney: Long,
)

data class GoalSummary(
    val goal: Goal,
    val allocatedAmount: Long,
    val remainingAmount: Long,
    val walletBreakdown: Map<String, Long>,
)

data class FinancialOverview(
    val totalAssets: Long,
    val allocatedFunds: Long,
    val protectedFunds: Long,
    val freeMoney: Long,
)

data class Budget(
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

data class BudgetSummary(
    val budget: Budget,
    val category: Category?,
    val spentAmount: Long,
    val remainingAmount: Long,
    val overspentAmount: Long,
    val utilizationPermille: Int,
    val healthStatus: BudgetHealthStatus,
    val effectiveStart: Long,
    val effectiveEndExclusive: Long,
)

data class BudgetAlertEvent(
    val budgetId: String,
    val budgetName: String,
    val periodKey: String,
    val level: Int,
)
