package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ObserveDashboardUseCaseTest {

    private lateinit var store: InMemoryLedgerStore
    private lateinit var observeDashboard: ObserveDashboardUseCase
    private lateinit var recordTx: RecordTransactionUseCase
    private lateinit var allocateToGoal: AllocateToGoalUseCase
    private lateinit var withdrawFromGoal: WithdrawFromGoalUseCase
    private lateinit var archiveWallet: ArchiveWalletUseCase

    private val zone = ZoneId.of("Asia/Jakarta")
    private val nov2026 = YearMonth.of(2026, 11)
    private val octEnd = LocalDate.of(2026, 10, 31).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
    private val novStart = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val novMid = LocalDate.of(2026, 11, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val decStart = LocalDate.of(2026, 12, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private val bca = Wallet("w-bca", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, octEnd, createdAt = octEnd, updatedAt = octEnd)
    private val cash = Wallet("w-cash", "Cash", WalletType.CASH, 1_000_000L, octEnd, createdAt = octEnd, updatedAt = octEnd)
    private val catSal = Category("c-sal", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, createdAt = 0L)
    private val catFood = Category("c-food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)

    @Before
    fun setup() {
        store = InMemoryLedgerStore()
        observeDashboard = ObserveDashboardUseCase(store, store, store, store)
        recordTx = RecordTransactionUseCase(store, store, store, store)
        allocateToGoal = AllocateToGoalUseCase(store, store, store)
        withdrawFromGoal = WithdrawFromGoalUseCase(store, store, store, store)
        archiveWallet = ArchiveWalletUseCase(store, store, store)
    }

    @Test
    fun `empty database emits hasAnyData false and zero totals`() = runTest {
        val snap = observeDashboard(zoneId = zone, yearMonth = nov2026).first()
        assertThat(snap.hasAnyData).isFalse()
        assertThat(snap.overview.totalAssets).isEqualTo(0L)
        assertThat(snap.monthlyActivity.income).isEqualTo(0L)
        assertThat(snap.monthlyActivity.expenses).isEqualTo(0L)
        assertThat(snap.monthlyActivity.netCashFlow).isEqualTo(0L)
        assertThat(snap.activeWallets).isEmpty()
        assertThat(snap.activeGoals).isEmpty()
        assertThat(snap.recentTransactions).isEmpty()
    }

    @Test
    fun `monthly activity respects month boundaries and excludes transfers and goal allocations`() = runTest {
        store.createWallet(bca)
        store.createWallet(cash)
        store.upsertCategory(catSal)
        store.upsertCategory(catFood)
        val goal = Goal("g-1", "Emergency", 3_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = novStart, updatedAt = novStart)
        store.createGoal(goal)

        // Oct & Dec transactions must be excluded from Nov 2026
        recordTx(Transaction("t-oct", TransactionType.INCOME, 9_000_000L, bca.id, categoryId = catSal.id, transactionDate = octEnd, createdAt = octEnd, updatedAt = octEnd))
        recordTx(Transaction("t-dec", TransactionType.EXPENSE, 800_000L, bca.id, categoryId = catFood.id, transactionDate = decStart, createdAt = decStart, updatedAt = decStart))

        // Nov transactions
        recordTx(Transaction("t-nov-inc", TransactionType.INCOME, 2_000_000L, bca.id, categoryId = catSal.id, transactionDate = novStart, createdAt = novStart, updatedAt = novStart))
        recordTx(Transaction("t-nov-exp", TransactionType.EXPENSE, 500_000L, cash.id, categoryId = catFood.id, transactionDate = novMid, createdAt = novMid, updatedAt = novMid))
        recordTx(Transaction("t-nov-xfer", TransactionType.TRANSFER, 300_000L, bca.id, destinationWalletId = cash.id, transactionDate = novMid, createdAt = novMid + 1, updatedAt = novMid + 1))

        allocateToGoal(goal.id, bca.id, 1_000_000L, date = novMid, now = novMid + 2)
        withdrawFromGoal(goal.id, bca.id, 200_000L, WithdrawalTarget.DIRECT_EXPENSE, date = novMid, reason = "Medicine", expenseCategoryId = catFood.id, now = novMid + 3)

        val snap = observeDashboard(zoneId = zone, yearMonth = nov2026).first()
        // Nov income = 2M; Nov expenses = 500k + 200k (DIRECT_EXPENSE's EXPENSE) = 700k; Transfer & Goal alloc/withdraw excluded
        assertThat(snap.monthlyActivity.income).isEqualTo(2_000_000L)
        assertThat(snap.monthlyActivity.expenses).isEqualTo(700_000L)
        assertThat(snap.monthlyActivity.netCashFlow).isEqualTo(1_300_000L)
        assertThat(snap.recentTransactions).hasSize(5)
        assertThat(snap.overview.protectedFunds).isEqualTo(800_000L)
    }

    @Test
    fun `archived wallet is excluded from activeWallets preview but remains in totalAssets`() = runTest {
        store.createWallet(bca)
        store.createWallet(cash)
        store.createGoal(Goal("g-arch", "Old", 1_000_000L, status = GoalStatus.ARCHIVED, createdAt = novStart, updatedAt = novStart))
        archiveWallet(cash.id, archived = true, updatedAt = novMid)

        val snap = observeDashboard(zoneId = zone, yearMonth = nov2026).first()
        assertThat(snap.activeWallets.map { it.wallet.id }).containsExactly(bca.id)
        assertThat(snap.overview.totalAssets).isEqualTo(6_000_000L)
        assertThat(snap.activeGoals).isEmpty()
    }

    @Test
    fun `calculateMonthlyActivity throws ArithmeticOverflowException on Long overflow`() {
        val txs = listOf(
            Transaction("t1", TransactionType.INCOME, Long.MAX_VALUE, bca.id, categoryId = catSal.id, transactionDate = novStart, createdAt = novStart, updatedAt = novStart),
            Transaction("t2", TransactionType.INCOME, 1L, bca.id, categoryId = catSal.id, transactionDate = novMid, createdAt = novMid, updatedAt = novMid),
        )
        assertThrows(DomainException.ArithmeticOverflowException::class.java) {
            FinancialEngine.calculateMonthlyActivity(txs, nov2026, zone)
        }
    }
}
