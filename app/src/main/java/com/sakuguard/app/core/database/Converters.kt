package com.sakuguard.app.core.database

import androidx.room.TypeConverter
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget

class Converters {
    @TypeConverter fun fromWalletType(v: WalletType): String = v.name
    @TypeConverter fun toWalletType(v: String): WalletType = WalletType.valueOf(v)

    @TypeConverter fun fromCategoryType(v: CategoryType): String = v.name
    @TypeConverter fun toCategoryType(v: String): CategoryType = CategoryType.valueOf(v)

    @TypeConverter fun fromTransactionType(v: TransactionType): String = v.name
    @TypeConverter fun toTransactionType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun fromGoalStatus(v: GoalStatus): String = v.name
    @TypeConverter fun toGoalStatus(v: String): GoalStatus = GoalStatus.valueOf(v)

    @TypeConverter fun fromProtectionMode(v: ProtectionMode): String = v.name
    @TypeConverter fun toProtectionMode(v: String): ProtectionMode = ProtectionMode.valueOf(v)

    @TypeConverter fun fromGoalLedgerType(v: GoalLedgerType): String = v.name
    @TypeConverter fun toGoalLedgerType(v: String): GoalLedgerType = GoalLedgerType.valueOf(v)

    @TypeConverter fun fromWithdrawalTarget(v: WithdrawalTarget?): String? = v?.name
    @TypeConverter fun toWithdrawalTarget(v: String?): WithdrawalTarget? = v?.let(WithdrawalTarget::valueOf)

    @TypeConverter fun fromBudgetPeriodType(v: BudgetPeriodType): String = v.name
    @TypeConverter fun toBudgetPeriodType(v: String): BudgetPeriodType = BudgetPeriodType.valueOf(v)
}
