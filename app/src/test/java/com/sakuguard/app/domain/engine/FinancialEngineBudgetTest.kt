package com.sakuguard.app.domain.engine

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetHealthStatus
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.repository.WalletLedgerTotals
import org.junit.Test

class FinancialEngineBudgetTest {

    private val food = Category("c1", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)

    private fun sampleBudget(limit: Long = 1_000_000L) = Budget(
        id = "b1",
        name = "Food Budget",
        categoryId = food.id,
        limitAmount = limit,
        periodType = BudgetPeriodType.MONTHLY,
        startDate = 0L,
        endDateExclusive = 0L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `summarizeBudget returns ON_TRACK below 80 percent`() {
        val summary = FinancialEngine.summarizeBudget(
            budget = sampleBudget(1_000_000L),
            category = food,
            spentAmount = 799_000L,
            effectiveStart = 100L,
            effectiveEndExclusive = 200L,
        )

        assertThat(summary.healthStatus).isEqualTo(BudgetHealthStatus.ON_TRACK)
        assertThat(summary.remainingAmount).isEqualTo(201_000L)
        assertThat(summary.overspentAmount).isEqualTo(0L)
        assertThat(summary.utilizationPermille).isEqualTo(799)
    }

    @Test
    fun `summarizeBudget returns WARNING at 80 percent through 100 percent`() {
        val at80 = FinancialEngine.summarizeBudget(
            budget = sampleBudget(1_000_000L),
            category = food,
            spentAmount = 800_000L,
            effectiveStart = 100L,
            effectiveEndExclusive = 200L,
        )
        val at100 = FinancialEngine.summarizeBudget(
            budget = sampleBudget(1_000_000L),
            category = food,
            spentAmount = 1_000_000L,
            effectiveStart = 100L,
            effectiveEndExclusive = 200L,
        )

        assertThat(at80.healthStatus).isEqualTo(BudgetHealthStatus.WARNING)
        assertThat(at80.remainingAmount).isEqualTo(200_000L)
        assertThat(at100.healthStatus).isEqualTo(BudgetHealthStatus.WARNING)
        assertThat(at100.remainingAmount).isEqualTo(0L)
        assertThat(at100.overspentAmount).isEqualTo(0L)
    }

    @Test
    fun `summarizeBudget returns EXCEEDED above limit with positive overspent`() {
        val summary = FinancialEngine.summarizeBudget(
            budget = sampleBudget(1_000_000L),
            category = food,
            spentAmount = 1_250_000L,
            effectiveStart = 100L,
            effectiveEndExclusive = 200L,
        )

        assertThat(summary.healthStatus).isEqualTo(BudgetHealthStatus.EXCEEDED)
        assertThat(summary.remainingAmount).isEqualTo(0L)
        assertThat(summary.overspentAmount).isEqualTo(250_000L)
        assertThat(summary.utilizationPermille).isEqualTo(1250)
    }

    @Test
    fun `calculateWalletBalanceFromTotals matches full ledger arithmetic`() {
        val wallet = Wallet(
            id = "w1",
            name = "BCA",
            type = WalletType.BANK_ACCOUNT,
            initialBalance = 1_000_000L,
            trackingStartDate = 0L,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val totals = WalletLedgerTotals(
            walletId = "w1",
            incomeSum = 500_000L,
            expenseSum = 200_000L,
            incomingTransferSum = 100_000L,
            outgoingTransferSum = 50_000L,
            allocatedSum = 300_000L,
        )

        val summary = FinancialEngine.summarizeWalletFromTotals(wallet, totals)
        assertThat(summary.currentBalance).isEqualTo(1_350_000L)
        assertThat(summary.allocatedBalance).isEqualTo(300_000L)
        assertThat(summary.freeBalance).isEqualTo(1_050_000L)
    }
}
