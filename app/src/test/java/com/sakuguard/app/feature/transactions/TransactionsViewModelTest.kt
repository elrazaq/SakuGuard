package com.sakuguard.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.DeleteTransactionUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.RecordTransactionUseCase
import com.sakuguard.app.domain.usecase.UpdateTransactionUseCase
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
class TransactionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        viewModel = TransactionsViewModel(
            transactionRepository = store,
            walletRepository = store,
            categoryRepository = store,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty database shows empty state`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.hasAnyTransactions).isFalse()
        assertThat(viewModel.uiState.value.items).isEmpty()
        job.cancel()
    }

    @Test
    fun `search and type, wallet, category filters narrow feed accurately`() = runTest {
        val now = System.currentTimeMillis()
        val bank = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
        val cash = Wallet("w2", "Cash", WalletType.CASH, 1_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
        val sal = Category("c1", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, createdAt = 0L)
        val food = Category("c2", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
        store.createWallet(bank)
        store.createWallet(cash)
        store.upsertCategory(sal)
        store.upsertCategory(food)

        val record = RecordTransactionUseCase(store, store, store, store)
        record(Transaction("t1", TransactionType.INCOME, 2_000_000L, bank.id, categoryId = sal.id, notes = "Monthly payroll", transactionDate = now, createdAt = now, updatedAt = now))
        record(Transaction("t2", TransactionType.EXPENSE, 50_000L, cash.id, categoryId = food.id, notes = "Nasi goreng", transactionDate = now, createdAt = now, updatedAt = now))
        record(Transaction("t3", TransactionType.TRANSFER, 300_000L, bank.id, destinationWalletId = cash.id, notes = "ATM", transactionDate = now, createdAt = now, updatedAt = now))

        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items).hasSize(3)

        viewModel.onTypeFilterChange(TransactionTypeFilter.EXPENSE)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.transaction.id }).containsExactly("t2")

        viewModel.onTypeFilterChange(TransactionTypeFilter.ALL)
        viewModel.onSearchQueryChange("payroll")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.transaction.id }).containsExactly("t1")

        viewModel.clearFilters()
        viewModel.onWalletFilterChange(cash.id)
        advanceUntilIdle()
        // Cash is wallet on t2 and destinationWallet on t3
        assertThat(viewModel.uiState.value.items.map { it.transaction.id }).containsExactly("t2", "t3")

        job.cancel()
    }

    @Test
    fun `incremental keyset scroll loads 20 items then appends remaining without duplicates`() = runTest {
        val now = 1_700_000_000_000L
        val bank = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 50_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
        val food = Category("c2", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
        store.createWallet(bank)
        store.upsertCategory(food)

        val record = RecordTransactionUseCase(store, store, store, store)
        repeat(45) { idx ->
            val ts = now + idx * 1_000L
            record(
                Transaction(
                    id = "tx-${idx.toString().padStart(2, '0')}",
                    type = TransactionType.EXPENSE,
                    amount = 10_000L,
                    walletId = bank.id,
                    categoryId = food.id,
                    notes = "Meal $idx",
                    transactionDate = ts,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
        }

        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.items).hasSize(20)
        assertThat(viewModel.uiState.value.hasMore).isTrue()
        assertThat(viewModel.uiState.value.items.first().transaction.id).isEqualTo("tx-44")

        viewModel.loadNextPage()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items).hasSize(40)
        assertThat(viewModel.uiState.value.hasMore).isTrue()

        viewModel.loadNextPage()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items).hasSize(45)
        assertThat(viewModel.uiState.value.hasMore).isFalse()
        assertThat(viewModel.uiState.value.items.map { it.transaction.id }.distinct()).hasSize(45)

        job.cancel()
    }

    @Test
    fun `auto refreshes feed on insert, edit of older transaction, and delete without touching filters`() = runTest {
        val bank = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
        val food = Category("c1", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, createdAt = 0L)
        store.createWallet(bank)
        store.upsertCategory(food)

        val record = RecordTransactionUseCase(store, store, store, store)
        val update = UpdateTransactionUseCase(store, store, store, store)
        val delete = DeleteTransactionUseCase(store, store, store)

        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items).isEmpty()

        val t1 = Transaction("t1", TransactionType.EXPENSE, 25_000L, bank.id, categoryId = food.id, notes = "Lunch", transactionDate = 100L, createdAt = 100L, updatedAt = 100L)
        val t2 = Transaction("t2", TransactionType.EXPENSE, 40_000L, bank.id, categoryId = food.id, notes = "Dinner", transactionDate = 200L, createdAt = 200L, updatedAt = 200L)
        record(t1)
        record(t2)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.transaction.id }).containsExactly("t2", "t1").inOrder()

        // Edit older transaction t1 (not the newest row) — ledger signal must trigger reload
        update(t1.copy(amount = 30_000L, notes = "Lunch updated", updatedAt = 300L))
        advanceUntilIdle()
        val updatedT1 = viewModel.uiState.value.items.first { it.transaction.id == "t1" }.transaction
        assertThat(updatedT1.amount).isEqualTo(30_000L)
        assertThat(updatedT1.notes).isEqualTo("Lunch updated")

        // Delete t2 — ledger signal must trigger reload
        delete("t2")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.transaction.id }).containsExactly("t1")

        job.cancel()
    }
}
