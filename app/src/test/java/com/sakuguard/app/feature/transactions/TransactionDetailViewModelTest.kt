package com.sakuguard.app.feature.transactions

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.DeleteTransactionUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveTransactionDetailUseCase
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
class TransactionDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        store.createWallet(Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 1_000_000L, 0L, createdAt = 0L, updatedAt = 0L))
        store.upsertCategory(Category("c1", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, createdAt = 0L))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `goal allocation hides edit and delete actions`() = runTest {
        store.createGoal(Goal("g1", "Trip", 5_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 0L, updatedAt = 0L))
        AllocateToGoalUseCase(store, store, store)("g1", "w1", 200_000L, date = 100L)
        val allocTxId = store.getAllTransactions().first().id

        val vm = TransactionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to allocTxId)),
            observeTransactionDetailUseCase = ObserveTransactionDetailUseCase(store, store, store),
            deleteTransactionUseCase = DeleteTransactionUseCase(store, store, store),
        )
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        assertThat(vm.uiState.value.canEditOrDelete).isFalse()
        vm.deleteTransaction()
        advanceUntilIdle()
        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.tx_error_goal_ledger_locked)
        assertThat(vm.uiState.value.deletedSuccessfully).isFalse()
        job.cancel()
    }

    @Test
    fun `deleting archived wallet transaction surfaces error`() = runTest {
        store.insertTransaction(
            Transaction("t1", TransactionType.INCOME, 500_000L, "w1", categoryId = "c1", transactionDate = 100L, createdAt = 100L, updatedAt = 100L),
        )
        store.setArchived("w1", true, 200L)

        val vm = TransactionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "t1")),
            observeTransactionDetailUseCase = ObserveTransactionDetailUseCase(store, store, store),
            deleteTransactionUseCase = DeleteTransactionUseCase(store, store, store),
        )
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.deleteTransaction()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.tx_error_archived_wallet)
        assertThat(vm.uiState.value.deletedSuccessfully).isFalse()
        job.cancel()
    }
}
