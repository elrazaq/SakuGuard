package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.ReportPeriodPreset
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportUseCasesTest {

    private val zone = ZoneId.of("UTC")
    private lateinit var store: InMemoryLedgerStore
    private lateinit var observeReports: ObserveReportsUseCase

    private val bank = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 10_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
    private val cash = Wallet("w2", "Cash", WalletType.CASH, 2_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
    private val oldWallet = Wallet("w3", "Old Mandiri", WalletType.BANK_ACCOUNT, 500_000L, 0L, isArchived = true, createdAt = 0L, updatedAt = 0L)

    private val salary = Category("c_sal", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, createdAt = 0L)
    private val food = Category("c_food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
    private val archivedCat = Category("c_old", "Legacy Bills", CategoryType.EXPENSE, "receipt", 0xFF6B7280L, isArchived = true, createdAt = 0L)

    @Before
    fun setup() = runTest {
        store = InMemoryLedgerStore()
        store.createWallet(bank)
        store.createWallet(cash)
        store.createWallet(oldWallet)
        store.upsertCategory(salary)
        store.upsertCategory(food)
        store.upsertCategory(archivedCat)

        observeReports = ObserveReportsUseCase(
            transactionRepository = store,
            walletRepository = store,
            categoryRepository = store,
            goalRepository = store,
            observeBudgetsUseCase = ObserveBudgetsUseCase(store, store, store),
        )
    }

    private fun millis(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `reports separate transfers, count direct expense once, retain archived history, and join goal event date`() = runTest {
        val recordTx = RecordTransactionUseCase(store, store, store, store)
        val createGoal = CreateGoalUseCase(store)
        val allocateGoal = AllocateToGoalUseCase(store, store, store)
        val withdrawGoal = WithdrawFromGoalUseCase(store, store, store, store)

        val oct10 = millis(2026, 10, 10)
        val nov05 = millis(2026, 11, 5)
        val nov15 = millis(2026, 11, 15)
        val wallClockDec = millis(2026, 12, 1) // Deliberately different from event date

        // October baseline expense: 400k
        recordTx(Transaction("t_oct", TransactionType.EXPENSE, 400_000L, bank.id, categoryId = archivedCat.id, transactionDate = oct10, createdAt = oct10, updatedAt = oct10))

        // November income: 3_000_000, expense: 300_000, transfer: 1_000_000 (bank -> cash)
        recordTx(Transaction("t_inc", TransactionType.INCOME, 3_000_000L, bank.id, categoryId = salary.id, transactionDate = nov05, createdAt = nov05, updatedAt = nov05))
        recordTx(Transaction("t_exp", TransactionType.EXPENSE, 300_000L, cash.id, categoryId = food.id, transactionDate = nov15, createdAt = nov15, updatedAt = nov15))
        recordTx(Transaction("t_trf", TransactionType.TRANSFER, 1_000_000L, bank.id, destinationWalletId = cash.id, transactionDate = nov15, createdAt = nov15, updatedAt = nov15))

        // Goal allocation backdated to Oct 10 with createdAt in Dec -> must NOT appear in Nov period contributions
        // And Nov allocation + DIRECT_EXPENSE withdrawal (200k) -> counts as 200k expense once
        createGoal(Goal("g1", "Emergency", 5_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 0L, updatedAt = 0L))
        allocateGoal("g1", bank.id, 500_000L, date = oct10, now = wallClockDec)
        allocateGoal("g1", bank.id, 600_000L, date = nov05, now = wallClockDec)
        withdrawGoal(
            goalId = "g1",
            walletId = bank.id,
            amount = 200_000L,
            target = WithdrawalTarget.DIRECT_EXPENSE,
            date = nov15,
            reason = "medical meal",
            expenseCategoryId = food.id,
            now = wallClockDec,
        )

        // Active monthly budget
        CreateBudgetUseCase(store, store, store)(
            Budget(
                id = "b1",
                name = "Food Cap",
                categoryId = food.id,
                limitAmount = 1_000_000L,
                periodType = BudgetPeriodType.MONTHLY,
                startDate = millis(2026, 10, 1),
                endDateExclusive = millis(2026, 10, 1),
                createdAt = 0L,
                updatedAt = 0L,
            ),
            zone,
        )

        val novRange = ReportPeriodResolver.resolve(ReportPeriodPreset.THIS_MONTH, referenceDate = LocalDate.of(2026, 11, 22), zoneId = zone)
        val novSnapshot = observeReports(novRange, zone, LocalDate.of(2026, 11, 22)).first()

        // Income = 3M, Expense = 300k + 200k (DIRECT_EXPENSE) = 500k; Transfer (1M) excluded!
        assertThat(novSnapshot.totalIncome).isEqualTo(3_000_000L)
        assertThat(novSnapshot.totalExpense).isEqualTo(500_000L)
        assertThat(novSnapshot.netCashFlow).isEqualTo(2_500_000L)

        // Trend: Nov (500k) vs Oct (400k) => +100k, +25.0% (250 permille)
        assertThat(novSnapshot.spendingTrend.previousExpense).isEqualTo(400_000L)
        assertThat(novSnapshot.spendingTrend.absoluteChange).isEqualTo(100_000L)
        assertThat(novSnapshot.spendingTrend.percentageChangePermille).isEqualTo(250)

        // Wallet activity separates transferOut/transferIn
        val bankActivity = novSnapshot.walletActivities.first { it.wallet.id == bank.id }
        assertThat(bankActivity.income).isEqualTo(3_000_000L)
        assertThat(bankActivity.expense).isEqualTo(200_000L)
        assertThat(bankActivity.transferOut).isEqualTo(1_000_000L)
        assertThat(bankActivity.transferIn).isEqualTo(0L)

        val cashActivity = novSnapshot.walletActivities.first { it.wallet.id == cash.id }
        assertThat(cashActivity.expense).isEqualTo(300_000L)
        assertThat(cashActivity.transferIn).isEqualTo(1_000_000L)

        // Goal contributions filtered by transactionDate (Nov 600k allocated, 200k withdrawn; Oct 500k excluded)
        assertThat(novSnapshot.goalSummary.periodAllocated).isEqualTo(600_000L)
        assertThat(novSnapshot.goalSummary.periodWithdrawn).isEqualTo(200_000L)
        assertThat(novSnapshot.goalSummary.totalAllocated).isEqualTo(900_000L)
        assertThat(novSnapshot.goalSummary.protectedFunds).isEqualTo(900_000L)

        // Budget performance for Nov
        assertThat(novSnapshot.budgetPerformance.isHistoricalMonth).isFalse()
        assertThat(novSnapshot.budgetPerformance.budgets.single().spentAmount).isEqualTo(500_000L)

        // Historical October retains archived category & marks isHistoricalMonth = true
        val octRange = ReportPeriodResolver.resolve(ReportPeriodPreset.LAST_MONTH, referenceDate = LocalDate.of(2026, 11, 22), zoneId = zone)
        val octSnapshot = observeReports(octRange, zone, LocalDate.of(2026, 11, 22)).first()
        assertThat(octSnapshot.expenseBreakdown.single().isArchived).isTrue()
        assertThat(octSnapshot.expenseBreakdown.single().categoryName).isEqualTo("Legacy Bills")
        assertThat(octSnapshot.spendingTrend.percentageChangePermille).isNull() // Zero baseline in Sep
        assertThat(octSnapshot.budgetPerformance.isHistoricalMonth).isTrue()
    }
}
