package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.GoalWithdrawalMode
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.SavingsGoal
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetUseCasesTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private lateinit var store: InMemoryLedgerStore
    private lateinit var createBudget: CreateBudgetUseCase
    private lateinit var archiveBudget: ArchiveBudgetUseCase
    private lateinit var observeBudgets: ObserveBudgetsUseCase
    private lateinit var evaluateAlerts: EvaluateBudgetAlertsUseCase

    private val food = Category("c_food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
    private val salary = Category("c_sal", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, createdAt = 0L)
    private val wallet = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L)

    @Before
    fun setup() = runTest {
        store = InMemoryLedgerStore()
        store.upsertCategory(food)
        store.upsertCategory(salary)
        store.createWallet(wallet)
        createBudget = CreateBudgetUseCase(store, store, store)
        archiveBudget = ArchiveBudgetUseCase(store, store)
        observeBudgets = ObserveBudgetsUseCase(store, store, store)
        evaluateAlerts = EvaluateBudgetAlertsUseCase(store, store)
    }

    private fun millis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun budget(
        id: String,
        periodType: BudgetPeriodType,
        start: LocalDate,
        endInclusive: LocalDate = start,
        categoryId: String = food.id,
        limit: Long = 1_000_000L,
    ) = Budget(
        id = id,
        name = "Budget $id",
        categoryId = categoryId,
        limitAmount = limit,
        periodType = periodType,
        startDate = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endDateExclusive = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `6-case overlap prevention rejects all overlapping active budget combinations`() = runTest {
        // 1. MONTHLY + MONTHLY
        createBudget(budget("m1", BudgetPeriodType.MONTHLY, LocalDate.of(2026, 3, 1)), zone)
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(budget("m2", BudgetPeriodType.MONTHLY, LocalDate.of(2026, 5, 10)), zone)
            }
        }
        // 2. MONTHLY + WEEKLY
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(budget("w1", BudgetPeriodType.WEEKLY, LocalDate.of(2026, 4, 1)), zone)
            }
        }
        // 3. MONTHLY + CUSTOM overlapping
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(
                    budget("c1", BudgetPeriodType.CUSTOM, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)),
                    zone,
                )
            }
        }
        // CUSTOM strictly BEFORE MONTHLY start (Feb < Mar) is allowed
        createBudget(
            budget("c_before", BudgetPeriodType.CUSTOM, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 20)),
            zone,
        )

        // Archive m1 to test WEEKLY & CUSTOM combinations
        archiveBudget("m1", archived = true, updatedAt = 1L)

        // 4. WEEKLY + WEEKLY
        createBudget(budget("wk1", BudgetPeriodType.WEEKLY, LocalDate.of(2026, 3, 2)), zone)
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(budget("wk2", BudgetPeriodType.WEEKLY, LocalDate.of(2026, 4, 6)), zone)
            }
        }
        // 5. WEEKLY + CUSTOM overlapping
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(
                    budget("c2", BudgetPeriodType.CUSTOM, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 8)),
                    zone,
                )
            }
        }

        // Restoring m1 fails while wk1 is active
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                archiveBudget("m1", archived = false, updatedAt = 2L)
            }
        }

        archiveBudget("wk1", archived = true, updatedAt = 3L)

        // 6. CUSTOM + CUSTOM overlapping vs disjoint
        createBudget(
            budget("c3", BudgetPeriodType.CUSTOM, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10)),
            zone,
        )
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(
                    budget("c4", BudgetPeriodType.CUSTOM, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 15)),
                    zone,
                )
            }
        }
        createBudget(
            budget("c5", BudgetPeriodType.CUSTOM, LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 20)),
            zone,
        )
    }

    @Test
    fun `income category cannot be assigned to budget`() {
        assertThrows(DomainException.InvalidInputException::class.java) {
            kotlinx.coroutines.runBlocking {
                createBudget(
                    budget("bad", BudgetPeriodType.MONTHLY, LocalDate.of(2026, 3, 1), categoryId = salary.id),
                    zone,
                )
            }
        }
    }

    @Test
    fun `recurring monthly budget resets across months and counts direct expense withdrawal once`() = runTest {
        createBudget(budget("m1", BudgetPeriodType.MONTHLY, LocalDate.of(2026, 3, 1), limit = 500_000L), zone)

        val recordTx = RecordTransactionUseCase(store, store, store, store)
        val createGoal = CreateGoalUseCase(store)
        val allocateGoal = AllocateToGoalUseCase(store, store, store, store)
        val withdrawGoal = WithdrawFromGoalUseCase(store, store, store, store)

        val march15 = millis(2026, 3, 15)
        val april5 = millis(2026, 4, 5)

        // March expense
        recordTx(Transaction("t1", TransactionType.EXPENSE, 200_000L, wallet.id, categoryId = food.id, transactionDate = march15, createdAt = march15, updatedAt = march15))
        // April expense + DIRECT_EXPENSE goal withdrawal in Food category
        recordTx(Transaction("t2", TransactionType.EXPENSE, 100_000L, wallet.id, categoryId = food.id, transactionDate = april5, createdAt = april5, updatedAt = april5))

        createGoal(SavingsGoal("g1", "Trip", 1_000_000L, ProtectionMode.STANDARD, createdAt = 0L, updatedAt = 0L))
        allocateGoal("g1", wallet.id, 300_000L, march15, "save")
        withdrawGoal(
            goalId = "g1",
            walletId = wallet.id,
            amount = 150_000L,
            mode = GoalWithdrawalMode.DIRECT_EXPENSE,
            categoryId = food.id,
            now = april5,
            note = "meal",
            reason = null,
            explicitUnlockConfirmed = false,
        )
        // TO_WALLET withdrawal must NOT be counted as budget expense
        withdrawGoal(
            goalId = "g1",
            walletId = wallet.id,
            amount = 50_000L,
            mode = GoalWithdrawalMode.TO_WALLET,
            categoryId = null,
            now = april5,
            note = "back",
            reason = null,
            explicitUnlockConfirmed = false,
        )

        val marchOverview = observeBudgets(zone, LocalDate.of(2026, 3, 20)).first()
        assertThat(marchOverview.activeBudgets.single().spentAmount).isEqualTo(200_000L)

        val aprilOverview = observeBudgets(zone, LocalDate.of(2026, 4, 10)).first()
        assertThat(aprilOverview.activeBudgets.single().spentAmount).isEqualTo(250_000L)
    }

    @Test
    fun `threshold alerts deduplicate per periodKey and escalate from 80 to 100`() = runTest {
        createBudget(budget("m1", BudgetPeriodType.MONTHLY, LocalDate.of(2026, 3, 1), limit = 1_000_000L), zone)
        val recordTx = RecordTransactionUseCase(store, store, store, store)
        val mar10 = millis(2026, 3, 10)
        val apr2 = millis(2026, 4, 2)

        // 80% crossed in March
        recordTx(Transaction("t1", TransactionType.EXPENSE, 800_000L, wallet.id, categoryId = food.id, transactionDate = mar10, createdAt = mar10, updatedAt = mar10))
        val firstRun = evaluateAlerts(zone, LocalDate.of(2026, 3, 10), mar10)
        assertThat(firstRun.map { it.level }).containsExactly(80)

        // Re-evaluating same period emits nothing
        val secondRun = evaluateAlerts(zone, LocalDate.of(2026, 3, 11), mar10)
        assertThat(secondRun).isEmpty()

        // Crossing >100% in March escalates to 100 once
        recordTx(Transaction("t2", TransactionType.EXPENSE, 300_000L, wallet.id, categoryId = food.id, transactionDate = mar10, createdAt = mar10, updatedAt = mar10))
        val thirdRun = evaluateAlerts(zone, LocalDate.of(2026, 3, 12), mar10)
        assertThat(thirdRun.map { it.level }).containsExactly(100)

        // New period (April) resets alert state; 800k in April fires 80 again
        recordTx(Transaction("t3", TransactionType.EXPENSE, 850_000L, wallet.id, categoryId = food.id, transactionDate = apr2, createdAt = apr2, updatedAt = apr2))
        val aprilRun = evaluateAlerts(zone, LocalDate.of(2026, 4, 2), apr2)
        assertThat(aprilRun.map { it.level }).containsExactly(80)
    }
}
