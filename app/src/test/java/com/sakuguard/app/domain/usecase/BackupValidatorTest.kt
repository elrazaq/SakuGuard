package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupPayload
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.SerializableCategory
import com.sakuguard.app.domain.model.SerializableGoal
import com.sakuguard.app.domain.model.SerializableGoalAllocation
import com.sakuguard.app.domain.model.SerializableTransaction
import com.sakuguard.app.domain.model.SerializableWallet
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupValidatorTest {

    private val w1 = SerializableWallet("w1", "BCA", WalletType.BANK_ACCOUNT, 1_000_000L, 1_000L, createdAt = 0L, updatedAt = 0L)
    private val catExp = SerializableCategory("c1", "Food", CategoryType.EXPENSE, "restaurant", 0L, createdAt = 0L)
    private val catInc = SerializableCategory("c2", "Salary", CategoryType.INCOME, "payments", 0L, createdAt = 0L)
    private val goal = SerializableGoal("g1", "Emergency", 5_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 0L, updatedAt = 0L)

    private fun validPayload(): BackupPayload = BackupPayload(
        wallets = listOf(w1),
        categories = listOf(catExp, catInc),
        goals = listOf(goal),
        transactions = listOf(
            SerializableTransaction("t1", TransactionType.GOAL_ALLOCATION, 300_000L, "w1", goalId = "g1", transactionDate = 2_000L, createdAt = 2_000L, updatedAt = 2_000L),
            SerializableTransaction("t2", TransactionType.GOAL_WITHDRAWAL, 100_000L, "w1", goalId = "g1", transactionDate = 3_000L, createdAt = 3_000L, updatedAt = 3_000L),
            SerializableTransaction("t3", TransactionType.EXPENSE, 100_000L, "w1", categoryId = "c1", transactionDate = 3_000L, createdAt = 3_000L, updatedAt = 3_000L),
        ),
        goalAllocations = listOf(
            SerializableGoalAllocation("a1", "g1", "w1", "t1", null, GoalLedgerType.ALLOCATION, 300_000L, null, null, 2_000L),
            SerializableGoalAllocation("a2", "g1", "w1", "t2", "t3", GoalLedgerType.WITHDRAWAL, 100_000L, "meal", WithdrawalTarget.DIRECT_EXPENSE, 3_000L),
        ),
        budgets = emptyList(),
    )

    @Test
    fun `valid payload passes all financial and referential invariants`() {
        BackupValidator.validate(validPayload())
    }

    @Test
    fun `broken DIRECT_EXPENSE link or negative wallet balance or pre-tracking date fails`() {
        val brokenLink = validPayload().copy(
            goalAllocations = listOf(
                SerializableGoalAllocation("a1", "g1", "w1", "t1", null, GoalLedgerType.ALLOCATION, 300_000L, null, null, 2_000L),
                SerializableGoalAllocation("a2", "g1", "w1", "t2", "missing_tx", GoalLedgerType.WITHDRAWAL, 100_000L, "meal", WithdrawalTarget.DIRECT_EXPENSE, 3_000L),
            ),
        )
        assertThrows(BackupException.IntegrityValidationException::class.java) {
            BackupValidator.validate(brokenLink)
        }

        val overspentWallet = validPayload().copy(
            transactions = validPayload().transactions + SerializableTransaction(
                "t4", TransactionType.EXPENSE, 2_000_000L, "w1", categoryId = "c1", transactionDate = 4_000L, createdAt = 4_000L, updatedAt = 4_000L,
            ),
        )
        assertThrows(BackupException.IntegrityValidationException::class.java) {
            BackupValidator.validate(overspentWallet)
        }

        val preTracking = validPayload().copy(
            wallets = listOf(w1.copy(trackingStartDate = 1_000_000_000L)),
        )
        assertThrows(BackupException.IntegrityValidationException::class.java) {
            BackupValidator.validate(preTracking)
        }
    }
}
