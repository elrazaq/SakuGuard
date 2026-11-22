package com.sakuguard.app.feature.reports

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.ReportPeriodPreset
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveBudgetsUseCase
import com.sakuguard.app.domain.usecase.ObserveReportsUseCase
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
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore
    private lateinit var viewModel: ReportsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        viewModel = ReportsViewModel(
            observeReportsUseCase = ObserveReportsUseCase(
                transactionRepository = store,
                walletRepository = store,
                categoryRepository = store,
                goalRepository = store,
                observeBudgetsUseCase = ObserveBudgetsUseCase(store, store, store),
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preset switching and privacy toggle update UI state`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.selectedPreset).isEqualTo(ReportPeriodPreset.THIS_MONTH)
        assertThat(viewModel.uiState.value.amountsHidden).isFalse()

        viewModel.togglePrivacy()
        viewModel.onPresetSelect(ReportPeriodPreset.LAST_6_MONTHS)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.amountsHidden).isTrue()
        assertThat(viewModel.uiState.value.selectedPreset).isEqualTo(ReportPeriodPreset.LAST_6_MONTHS)
        assertThat(viewModel.uiState.value.snapshot?.periodRange?.isMultiMonth).isTrue()

        job.cancel()
    }

    @Test
    fun `category drill-down viewmodel paginates 20 items per page`() = runTest {
        val now = 1_700_000_000_000L
        val wallet = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 50_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
        val food = Category("c_food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
        store.createWallet(wallet)
        store.upsertCategory(food)

        val record = RecordTransactionUseCase(store, store, store, store)
        repeat(25) { idx ->
            val ts = now + idx * 1_000L
            record(
                Transaction(
                    id = "tx-$idx",
                    type = TransactionType.EXPENSE,
                    amount = 10_000L,
                    walletId = wallet.id,
                    categoryId = food.id,
                    transactionDate = ts,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
        }

        val detailVm = ReportCategoryDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "type" to TransactionType.EXPENSE.name,
                    "categoryId" to food.id,
                    "startDate" to now,
                    "endDateExclusive" to (now + 100_000L),
                ),
            ),
            transactionRepository = store,
            walletRepository = store,
            categoryRepository = store,
        )

        val job = backgroundScope.launch { detailVm.uiState.collect() }
        advanceUntilIdle()

        assertThat(detailVm.uiState.value.items).hasSize(20)
        assertThat(detailVm.uiState.value.hasMore).isTrue()

        detailVm.loadNextPage()
        advanceUntilIdle()

        assertThat(detailVm.uiState.value.items).hasSize(25)
        assertThat(detailVm.uiState.value.hasMore).isFalse()

        job.cancel()
    }
}
