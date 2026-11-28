package com.sakuguard.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.ArchiveBudgetUseCase
import com.sakuguard.app.domain.usecase.CreateBudgetUseCase
import com.sakuguard.app.domain.usecase.EvaluateBudgetAlertsUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveBudgetsUseCase
import com.sakuguard.app.domain.usecase.RecordTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore
    private lateinit var viewModel: BudgetsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        viewModel = BudgetsViewModel(
            observeBudgetsUseCase = ObserveBudgetsUseCase(store, store, store, testDispatcher),
            archiveBudgetUseCase = ArchiveBudgetUseCase(store, store),
            evaluateBudgetAlertsUseCase = EvaluateBudgetAlertsUseCase(store, store),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `budgets viewmodel exposes active archived and threshold alerts`() = runTest {
        val now = System.currentTimeMillis()
        val food = Category("c1", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
        val wallet = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
        store.upsertCategory(food)
        store.createWallet(wallet)

        CreateBudgetUseCase(store, store, store)(
            Budget(
                id = "b1",
                name = "Food Monthly",
                categoryId = food.id,
                limitAmount = 1_000_000L,
                periodType = BudgetPeriodType.MONTHLY,
                startDate = now,
                endDateExclusive = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        RecordTransactionUseCase(store, store, store, store)(
            Transaction(
                id = "t1",
                type = TransactionType.EXPENSE,
                amount = 850_000L,
                walletId = wallet.id,
                categoryId = food.id,
                transactionDate = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val job = backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.evaluateAlerts()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeBudgets).hasSize(1)
        assertThat(viewModel.uiState.value.thresholdAlerts.map { it.level }).containsExactly(80)

        viewModel.dismissAlerts()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.thresholdAlerts).isEmpty()

        ArchiveBudgetUseCase(store, store)("b1", archived = true, updatedAt = now)
        viewModel.toggleShowArchived()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeBudgets).isEmpty()
        assertThat(viewModel.uiState.value.archivedBudgets).hasSize(1)
        assertThat(viewModel.uiState.value.showArchived).isTrue()

        // Create a replacement active budget so restore hits overlap error
        CreateBudgetUseCase(store, store, store)(
            Budget(
                id = "b2",
                name = "Food New",
                categoryId = food.id,
                limitAmount = 500_000L,
                periodType = BudgetPeriodType.MONTHLY,
                startDate = now,
                endDateExclusive = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        viewModel.restoreBudget("b1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.budget_error_overlap)

        job.cancel()
    }
}
