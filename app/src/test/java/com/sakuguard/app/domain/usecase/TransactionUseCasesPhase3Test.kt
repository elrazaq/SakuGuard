package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.core.formatting.DateFormatter
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
import java.time.LocalDate
import java.time.ZoneId

class TransactionUseCasesPhase3Test {

    private lateinit var store: InMemoryLedgerStore
    private lateinit var recordTx: RecordTransactionUseCase
    private lateinit var updateTx: UpdateTransactionUseCase
    private lateinit var deleteTx: DeleteTransactionUseCase
    private lateinit var allocateToGoal: AllocateToGoalUseCase
    private lateinit var withdrawFromGoal: WithdrawFromGoalUseCase
    private lateinit var archiveWallet: ArchiveWalletUseCase

    private val zone = ZoneId.systemDefault()
    private val trackingDay = LocalDate.of(2026, 11, 22)
    private val trackingCreationNoon = trackingDay.atTime(12, 30).atZone(zone).toInstant().toEpochMilli()
    private val sameDayMidnight = trackingDay.atStartOfDay(zone).toInstant().toEpochMilli()
    private val previousDay = trackingDay.minusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

    private val bank = Wallet("w-bank", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, trackingCreationNoon, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
    private val cash = Wallet("w-cash", "Cash", WalletType.CASH, 1_000_000L, trackingCreationNoon, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
    private val catSalary = Category("c-sal", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, isDefault = true, createdAt = 0L)
    private val catFood = Category("c-food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, isDefault = true, createdAt = 0L)
    private val goal = Goal("g-1", "Emergency", 5_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)

    @Before
    fun setup() = runTest {
        store = InMemoryLedgerStore()
        recordTx = RecordTransactionUseCase(store, store, store, store)
        updateTx = UpdateTransactionUseCase(store, store, store, store)
        deleteTx = DeleteTransactionUseCase(store, store, store)
        allocateToGoal = AllocateToGoalUseCase(store, store, store)
        withdrawFromGoal = WithdrawFromGoalUseCase(store, store, store, store)
        archiveWallet = ArchiveWalletUseCase(store, store, store)

        store.createWallet(bank)
        store.createWallet(cash)
        store.upsertCategory(catSalary)
        store.upsertCategory(catFood)
        store.createGoal(goal)
    }

    @Test
    fun `same-day transaction at start of day succeeds while previous-day is rejected`() = runTest {
        recordTx(
            Transaction("t-same", TransactionType.INCOME, 250_000L, bank.id, categoryId = catSalary.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon),
        )
        assertThat(store.getTransactionById("t-same")).isNotNull()

        assertThrowsSuspend<DomainException.PreTrackingDateException> {
            recordTx(
                Transaction("t-prev", TransactionType.INCOME, 250_000L, bank.id, categoryId = catSalary.id, transactionDate = previousDay, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon),
            )
        }
    }

    @Test
    fun `transfer preserves total assets and allocations and validates free money and overflow`() = runTest {
        allocateToGoal(goal.id, bank.id, 4_000_000L, date = trackingCreationNoon) // Bank freeMoney = 1M

        // Transfer exceeding Bank freeMoney (1.5M > 1M) rejected
        assertThrowsSuspend<DomainException.InsufficientFreeMoneyException> {
            recordTx(Transaction("t-over", TransactionType.TRANSFER, 1_500_000L, bank.id, destinationWalletId = cash.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon))
        }

        // Valid 1M transfer
        recordTx(Transaction("t-ok", TransactionType.TRANSFER, 1_000_000L, bank.id, destinationWalletId = cash.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon))
        val overview = FinancialEngine.calculateFinancialOverview(
            wallets = store.getAllWallets(),
            transactions = store.getAllTransactions(),
            goals = store.getAllGoals(),
            allocations = store.getAllAllocations(),
        )
        assertThat(overview.totalAssets).isEqualTo(6_000_000L)
        assertThat(overview.allocatedFunds).isEqualTo(4_000_000L)
        assertThat(overview.freeMoney).isEqualTo(2_000_000L)

        // Destination overflow rejected without partial commit
        val hugeWallet = Wallet("w-huge", "Huge", WalletType.OTHER, Long.MAX_VALUE, trackingCreationNoon, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
        store.createWallet(hugeWallet)
        assertThrowsSuspend<DomainException.ArithmeticOverflowException> {
            recordTx(Transaction("t-ovf", TransactionType.TRANSFER, 100_000L, cash.id, destinationWalletId = hugeWallet.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon))
        }
        assertThat(store.getTransactionById("t-ovf")).isNull()
    }

    @Test
    fun `editing or deleting goal ledger transactions is rejected by domain`() = runTest {
        allocateToGoal(goal.id, bank.id, 1_000_000L, date = trackingCreationNoon)
        withdrawFromGoal(goal.id, bank.id, 200_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = trackingCreationNoon, reason = "Test")

        val allocTx = store.getAllTransactions().first { it.type == TransactionType.GOAL_ALLOCATION }
        val withdrawTx = store.getAllTransactions().first { it.type == TransactionType.GOAL_WITHDRAWAL }

        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            updateTx(allocTx.copy(amount = 500_000L))
        }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            deleteTx(allocTx.id)
        }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            updateTx(withdrawTx.copy(amount = 100_000L))
        }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            deleteTx(withdrawTx.id)
        }
    }

    @Test
    fun `archived wallet blocks new transfers and edit or delete of historical transactions`() = runTest {
        val inc = Transaction("t-1", TransactionType.INCOME, 500_000L, cash.id, categoryId = catSalary.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
        val xfer = Transaction("t-2", TransactionType.TRANSFER, 200_000L, bank.id, destinationWalletId = cash.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
        recordTx(inc)
        recordTx(xfer)

        archiveWallet(cash.id, archived = true, updatedAt = trackingCreationNoon)

        // New transfer from/to archived wallet rejected
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            recordTx(Transaction("t-new1", TransactionType.TRANSFER, 50_000L, cash.id, destinationWalletId = bank.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon))
        }
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            recordTx(Transaction("t-new2", TransactionType.TRANSFER, 50_000L, bank.id, destinationWalletId = cash.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon))
        }

        // Edit/delete historical income & transfer involving archived wallet rejected
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            updateTx(inc.copy(amount = 600_000L))
        }
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            deleteTx(inc.id)
        }
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            updateTx(xfer.copy(amount = 300_000L))
        }
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            deleteTx(xfer.id)
        }

        // Edit transfer moving active wallet transaction into archived wallet rejected
        val thirdWallet = Wallet("w-3", "GoPay", WalletType.E_WALLET, 500_000L, trackingCreationNoon, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
        store.createWallet(thirdWallet)
        val activeXfer = Transaction("t-3", TransactionType.TRANSFER, 100_000L, bank.id, destinationWalletId = thirdWallet.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
        recordTx(activeXfer)
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            updateTx(activeXfer.copy(destinationWalletId = cash.id))
        }
    }

    @Test
    fun `category type mismatch and transaction type change during edit are rejected`() = runTest {
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            recordTx(Transaction("t-bad-cat", TransactionType.EXPENSE, 50_000L, cash.id, categoryId = catSalary.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon))
        }

        val validExp = Transaction("t-exp", TransactionType.EXPENSE, 50_000L, cash.id, categoryId = catFood.id, transactionDate = sameDayMidnight, createdAt = trackingCreationNoon, updatedAt = trackingCreationNoon)
        recordTx(validExp)

        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            updateTx(validExp.copy(categoryId = catSalary.id))
        }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            updateTx(validExp.copy(type = TransactionType.INCOME, categoryId = catSalary.id))
        }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            updateTx(validExp.copy(type = TransactionType.TRANSFER, destinationWalletId = bank.id, categoryId = null))
        }
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
