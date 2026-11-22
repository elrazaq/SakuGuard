package com.sakuguard.app.domain.engine

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget
import org.junit.Assert.assertThrows
import org.junit.Test

class FinancialEngineTest {

    private val bca = Wallet("w-bca", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 100L, createdAt = 100L, updatedAt = 100L)
    private val cash = Wallet("w-cash", "Cash", WalletType.CASH, 1_000_000L, 100L, createdAt = 100L, updatedAt = 100L)

    @Test
    fun `complex multi-wallet sequence preserves portfolio conservation and protected funds`() {
        val vacation = Goal("g-vac", "Vacation", 4_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 100L, updatedAt = 100L)
        val laptop = Goal("g-lap", "Laptop", 10_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 100L, updatedAt = 100L)

        val transactions = listOf(
            // Income +2M to BCA -> BCA 7M
            Transaction("t1", TransactionType.INCOME, 2_000_000L, "w-bca", categoryId = "c-sal", transactionDate = 200L, createdAt = 200L, updatedAt = 200L),
            // Transfer 1.5M BCA -> Cash -> BCA 5.5M, Cash 2.5M
            Transaction("t2", TransactionType.TRANSFER, 1_500_000L, "w-bca", destinationWalletId = "w-cash", transactionDate = 300L, createdAt = 300L, updatedAt = 300L),
            // Expense 500k on Cash -> Cash 2.0M
            Transaction("t3", TransactionType.EXPENSE, 500_000L, "w-cash", categoryId = "c-food", transactionDate = 400L, createdAt = 400L, updatedAt = 400L),
            // Goal allocation marker (must NOT change wallet balance)
            Transaction("t4", TransactionType.GOAL_ALLOCATION, 3_000_000L, "w-bca", goalId = "g-vac", transactionDate = 500L, createdAt = 500L, updatedAt = 500L),
            Transaction("t5", TransactionType.GOAL_ALLOCATION, 1_000_000L, "w-cash", goalId = "g-vac", transactionDate = 501L, createdAt = 501L, updatedAt = 501L),
            Transaction("t6", TransactionType.GOAL_ALLOCATION, 1_000_000L, "w-bca", goalId = "g-lap", transactionDate = 502L, createdAt = 502L, updatedAt = 502L),
            // Direct expense withdrawal of 500k from Vacation (BCA): GOAL_WITHDRAWAL + linked EXPENSE
            Transaction("t7", TransactionType.GOAL_WITHDRAWAL, 500_000L, "w-bca", goalId = "g-vac", transactionDate = 600L, createdAt = 600L, updatedAt = 600L),
            Transaction("t8", TransactionType.EXPENSE, 500_000L, "w-bca", categoryId = "c-travel", goalId = "g-vac", transactionDate = 600L, createdAt = 600L, updatedAt = 600L),
        )

        val allocations = listOf(
            GoalAllocationEntry("a1", "g-vac", "w-bca", "t4", entryType = GoalLedgerType.ALLOCATION, amount = 3_000_000L, createdAt = 500L),
            GoalAllocationEntry("a2", "g-vac", "w-cash", "t5", entryType = GoalLedgerType.ALLOCATION, amount = 1_000_000L, createdAt = 501L),
            GoalAllocationEntry("a3", "g-lap", "w-bca", "t6", entryType = GoalLedgerType.ALLOCATION, amount = 1_000_000L, createdAt = 502L),
            GoalAllocationEntry("a4", "g-vac", "w-bca", "t7", linkedExpenseTransactionId = "t8", entryType = GoalLedgerType.WITHDRAWAL, amount = 500_000L, withdrawalTarget = WithdrawalTarget.DIRECT_EXPENSE, createdAt = 600L),
        )

        // BCA: 5M + 2M - 1.5M - 0.5M (direct expense) = 5.0M
        assertThat(FinancialEngine.calculateWalletBalance(bca, transactions)).isEqualTo(5_000_000L)
        // Cash: 1M + 1.5M - 0.5M = 2.0M
        assertThat(FinancialEngine.calculateWalletBalance(cash, transactions)).isEqualTo(2_000_000L)

        val overview = FinancialEngine.calculateFinancialOverview(
            wallets = listOf(bca, cash),
            transactions = transactions,
            goals = listOf(vacation, laptop),
            allocations = allocations,
        )

        assertThat(overview.totalAssets).isEqualTo(7_000_000L)
        // Vacation: 3M + 1M - 0.5M = 3.5M (PROTECTED); Laptop: 1M (STANDARD) => Allocated = 4.5M
        assertThat(overview.allocatedFunds).isEqualTo(4_500_000L)
        assertThat(overview.protectedFunds).isEqualTo(3_500_000L)
        assertThat(overview.freeMoney).isEqualTo(2_500_000L)
        assertThat(overview.totalAssets).isEqualTo(overview.freeMoney + overview.allocatedFunds)
        assertThat(overview.protectedFunds).isAtMost(overview.allocatedFunds)

        // Protection mode upgrade reclassifies existing allocations without changing totalAssets/freeMoney
        val upgradedLaptop = laptop.copy(protectionMode = ProtectionMode.STRICT)
        val upgradedOverview = FinancialEngine.calculateFinancialOverview(
            wallets = listOf(bca, cash),
            transactions = transactions,
            goals = listOf(vacation, upgradedLaptop),
            allocations = allocations,
        )
        assertThat(upgradedOverview.totalAssets).isEqualTo(7_000_000L)
        assertThat(upgradedOverview.freeMoney).isEqualTo(2_500_000L)
        assertThat(upgradedOverview.protectedFunds).isEqualTo(4_500_000L)
    }

    @Test
    fun `wallet balance calculation throws ArithmeticOverflowException on Long overflow`() {
        val maxWallet = bca.copy(openingBalance = Long.MAX_VALUE)
        val tx = Transaction("t-ovf", TransactionType.INCOME, 1L, maxWallet.id, categoryId = "c-sal", transactionDate = 200L, createdAt = 200L, updatedAt = 200L)
        assertThrows(DomainException.ArithmeticOverflowException::class.java) {
            FinancialEngine.calculateWalletBalance(maxWallet, listOf(tx))
        }
    }
}
