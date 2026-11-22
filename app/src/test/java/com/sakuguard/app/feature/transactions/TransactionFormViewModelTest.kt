package com.sakuguard.app.feature.transactions

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.RecordTransactionUseCase
import com.sakuguard.app.domain.usecase.UpdateTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        store.createWallet(Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 1_000_000L, 0L, createdAt = 0L, updatedAt = 0L))
        store.createWallet(Wallet("w2", "Cash", WalletType.CASH, 500_000L, 0L, createdAt = 0L, updatedAt = 0L))
        store.createWallet(Wallet("w3", "Old", WalletType.OTHER, 0L, 0L, isArchived = true, createdAt = 0L, updatedAt = 0L))
        store.upsertCategory(Category("c1", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, createdAt = 0L))
        store.upsertCategory(Category("c2", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `activeWallets excludes archived and transfer rejects identical wallets`() = runTest {
        val vm = TransactionFormViewModel(
            savedStateHandle = SavedStateHandle(),
            walletRepository = store,
            categoryRepository = store,
            transactionRepository = store,
            recordTransactionUseCase = RecordTransactionUseCase(store, store, store, store),
            updateTransactionUseCase = UpdateTransactionUseCase(store, store, store, store),
        )
        advanceUntilIdle()

        assertThat(vm.uiState.value.activeWallets.map { it.id }).containsExactly("w1", "w2")

        vm.onTypeSelect(TransactionType.TRANSFER)
        vm.onAmountChange("100000")
        vm.onWalletSelect("w1")
        vm.onDestinationWalletSelect("w1")
        vm.saveTransaction()
        advanceUntilIdle()

        assertThat(vm.uiState.value.destinationWalletErrorRes).isEqualTo(R.string.tx_error_same_wallet)
        assertThat(vm.uiState.value.savedSuccessfully).isFalse()
    }

    @Test
    fun `edit mode locks transaction type and saves updated amount`() = runTest {
        val now = System.currentTimeMillis()
        store.insertTransaction(
            Transaction("t1", TransactionType.EXPENSE, 50_000L, "w1", categoryId = "c2", notes = "Lunch", transactionDate = now, createdAt = now, updatedAt = now),
        )
        val vm = TransactionFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "t1")),
            walletRepository = store,
            categoryRepository = store,
            transactionRepository = store,
            recordTransactionUseCase = RecordTransactionUseCase(store, store, store, store),
            updateTransactionUseCase = UpdateTransactionUseCase(store, store, store, store),
        )
        advanceUntilIdle()

        vm.onTypeSelect(TransactionType.INCOME) // must be ignored in edit mode
        assertThat(vm.uiState.value.selectedType).isEqualTo(TransactionType.EXPENSE)

        vm.onAmountChange("75000")
        vm.saveTransaction()
        advanceUntilIdle()

        assertThat(vm.uiState.value.savedSuccessfully).isTrue()
        assertThat(store.getTransactionById("t1")?.amount).isEqualTo(75_000L)
    }
}
