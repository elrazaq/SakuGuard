package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TransactionInvariantsTest {

    private lateinit var store: InMemoryLedgerStore
    private lateinit var createWallet: CreateWalletUseCase
    private lateinit var updateWallet: UpdateWalletUseCase
    private lateinit var archiveWallet: ArchiveWalletUseCase
    private lateinit var createGoal: CreateGoalUseCase
    private lateinit var archiveGoal: ArchiveGoalUseCase
    private lateinit var allocateToGoal: AllocateToGoalUseCase
    private lateinit var withdrawFromGoal: WithdrawFromGoalUseCase
    private lateinit var recordTx: RecordTransactionUseCase
    private lateinit var updateTx: UpdateTransactionUseCase
    private lateinit var deleteTx: DeleteTransactionUseCase

    private val bank = Wallet("w-bank", "Bank", WalletType.BANK_ACCOUNT, 3_000_000L, trackingStartDate = 1_000L, createdAt = 1_000L, updatedAt = 1_000L)
    private val cash = Wallet("w-cash", "Cash", WalletType.CASH, 1_000_000L, trackingStartDate = 1_000L, createdAt = 1_000L, updatedAt = 1_000L)
    private val catSalary = Category("c-sal", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, isDefault = true, createdAt = 0L)
    private val catFood = Category("c-food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, isDefault = true, createdAt = 0L)
    private val goal = Goal("g-1", "Emergency", 5_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 1_000L, updatedAt = 1_000L)

    @Before
    fun setup() = runTest {
        store = InMemoryLedgerStore()
        createWallet = CreateWalletUseCase(store)
        updateWallet = UpdateWalletUseCase(store, store, store)
        archiveWallet = ArchiveWalletUseCase(store, store, store)
        createGoal = CreateGoalUseCase(store)
        archiveGoal = ArchiveGoalUseCase(store, store)
        allocateToGoal = AllocateToGoalUseCase(store, store, store)
        withdrawFromGoal = WithdrawFromGoalUseCase(store, store, store, store)
        recordTx = RecordTransactionUseCase(store, store, store, store)
        updateTx = UpdateTransactionUseCase(store, store, store, store)
        deleteTx = DeleteTransactionUseCase(store, store, store)

        createWallet(bank)
        createWallet(cash)
        store.upsertCategory(catSalary)
        store.upsertCategory(catFood)
        createGoal(goal)
    }

    @Test
    fun `negative opening balance and pre-tracking transactions are rejected`() = runTest {
        assertThrowsSuspend<DomainException.InvalidAmountException> {
            createWallet(bank.copy(id = "w-bad", openingBalance = -100L))
        }
        assertThrowsSuspend<DomainException.PreTrackingDateException> {
            recordTx(Transaction("t-old", TransactionType.INCOME, 100_000L, bank.id, categoryId = catSalary.id, transactionDate = 999L, createdAt = 1_000L, updatedAt = 1_000L))
        }
    }

    @Test
    fun `wallet-specific goal withdrawal rejects amount exceeding source wallet allocation`() = runTest {
        allocateToGoal(goal.id, bank.id, 3_000_000L, date = 2_000L)
        allocateToGoal(goal.id, cash.id, 1_000_000L, date = 2_000L)

        // Overall goal allocation is 4M, but Cash only has 1M -> withdrawing 2M from Cash must fail
        assertThrowsSuspend<DomainException.InsufficientGoalAllocationException> {
            withdrawFromGoal(goal.id, cash.id, 2_000_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = 3_000L, reason = "Test")
        }
        // Withdrawing 2M from Bank succeeds
        withdrawFromGoal(goal.id, bank.id, 2_000_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = 3_000L, reason = "Test")
        assertThat(FinancialEngine.calculateGoalAllocatedAmount(goal.id, store.getAllAllocations())).isEqualTo(2_000_000L)
    }

    @Test
    fun `deleting income that funds active allocation is rejected and rolls back atomically`() = runTest {
        val inc = Transaction("t-inc", TransactionType.INCOME, 2_000_000L, cash.id, categoryId = catSalary.id, transactionDate = 2_000L, createdAt = 2_000L, updatedAt = 2_000L)
        recordTx(inc) // Cash now 3M
        allocateToGoal(goal.id, cash.id, 2_500_000L, date = 2_500L) // Cash freeMoney = 500k

        // Deleting 2M income would drop Cash balance to 1M < 2.5M allocated -> must fail & leave DB intact
        assertThrowsSuspend<DomainException.InsufficientFreeMoneyException> {
            deleteTx(inc.id)
        }
        assertThat(store.getTransactionById(inc.id)).isNotNull()
        assertThat(FinancialEngine.calculateWalletBalance(cash, store.getAllTransactions())).isEqualTo(3_000_000L)
    }

    @Test
    fun `direct expense withdrawal updates and deletes linked pair atomically`() = runTest {
        allocateToGoal(goal.id, bank.id, 2_000_000L, date = 2_000L)
        withdrawFromGoal(goal.id, bank.id, 1_000_000L, WithdrawalTarget.DIRECT_EXPENSE, date = 3_000L, reason = "Laptop repair", expenseCategoryId = catFood.id)

        // Bank balance: 3M - 1M = 2M; Goal allocated: 2M - 1M = 1M; Free money: 1M
        assertThat(FinancialEngine.calculateWalletBalance(bank, store.getAllTransactions())).isEqualTo(2_000_000L)
        assertThat(FinancialEngine.calculateGoalAllocatedAmount(goal.id, store.getAllAllocations())).isEqualTo(1_000_000L)

        val expenseTx = store.getAllTransactions().first { it.type == TransactionType.EXPENSE }
        updateTx(expenseTx.copy(amount = 1_500_000L, updatedAt = 3_100L))
        assertThat(FinancialEngine.calculateWalletBalance(bank, store.getAllTransactions())).isEqualTo(1_500_000L)
        assertThat(FinancialEngine.calculateGoalAllocatedAmount(goal.id, store.getAllAllocations())).isEqualTo(500_000L)

        deleteTx(expenseTx.id)
        assertThat(FinancialEngine.calculateWalletBalance(bank, store.getAllTransactions())).isEqualTo(3_000_000L)
        assertThat(FinancialEngine.calculateGoalAllocatedAmount(goal.id, store.getAllAllocations())).isEqualTo(2_000_000L)
        assertThat(store.getAllTransactions().none { it.type == TransactionType.GOAL_WITHDRAWAL }).isTrue()
    }

    @Test
    fun `archived entities block new writes and goals or wallets with funds cannot be archived`() = runTest {
        allocateToGoal(goal.id, cash.id, 500_000L, date = 2_000L)

        assertThrowsSuspend<DomainException.InsufficientGoalAllocationException> {
            archiveWallet(cash.id, archived = true, updatedAt = 3_000L)
        }
        assertThrowsSuspend<DomainException.InsufficientGoalAllocationException> {
            archiveGoal(goal.id, updatedAt = 3_000L)
        }

        // Archive bank (0 allocations) -> allowed, then new tx on bank must fail
        archiveWallet(bank.id, archived = true, updatedAt = 3_000L)
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            recordTx(Transaction("t-arch", TransactionType.EXPENSE, 10_000L, bank.id, categoryId = catFood.id, transactionDate = 3_100L, createdAt = 3_100L, updatedAt = 3_100L))
        }

        // Archive category -> existing txs intact, new tx rejected
        store.setArchived(catFood.id, archived = true)
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            recordTx(Transaction("t-cat", TransactionType.EXPENSE, 10_000L, cash.id, categoryId = catFood.id, transactionDate = 3_100L, createdAt = 3_100L, updatedAt = 3_100L))
        }
    }

    @Test
    fun `self-transfer and arithmetic overflow are rejected without partial commit`() = runTest {
        assertThrowsSuspend<DomainException.InvalidTransferException> {
            recordTx(Transaction("t-self", TransactionType.TRANSFER, 100_000L, bank.id, destinationWalletId = bank.id, transactionDate = 2_000L, createdAt = 2_000L, updatedAt = 2_000L))
        }
        assertThrowsSuspend<DomainException.ArithmeticOverflowException> {
            recordTx(Transaction("t-ovf", TransactionType.INCOME, Long.MAX_VALUE, bank.id, categoryId = catSalary.id, transactionDate = 2_000L, createdAt = 2_000L, updatedAt = 2_000L))
        }
        assertThat(store.getAllTransactions()).isEmpty()
    }

    private suspend inline fun <reified T : Throwable> assertThrowsSuspend(crossinline block: suspend () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (t: Throwable) {
            thrown = t
        }
        assertThat(thrown).isInstanceOf(T::class.java)
    }
}
