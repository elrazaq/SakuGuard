package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.model.WithdrawalTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GoalUseCasesPhase4Test {

    private lateinit var store: InMemoryLedgerStore
    private lateinit var createGoal: CreateGoalUseCase
    private lateinit var updateGoal: UpdateGoalUseCase
    private lateinit var updateProtection: UpdateGoalProtectionUseCase
    private lateinit var updateStatus: UpdateGoalStatusUseCase
    private lateinit var archiveGoal: ArchiveGoalUseCase
    private lateinit var restoreGoal: RestoreGoalUseCase
    private lateinit var allocate: AllocateToGoalUseCase
    private lateinit var withdraw: WithdrawFromGoalUseCase
    private lateinit var updateTx: UpdateTransactionUseCase
    private lateinit var deleteTx: DeleteTransactionUseCase

    private val bca = Wallet("w-bca", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
    private val cash = Wallet("w-cash", "Cash", WalletType.CASH, 2_000_000L, 0L, createdAt = 0L, updatedAt = 0L)
    private val catExpense = Category("c-exp", "Shopping", CategoryType.EXPENSE, "shopping_bag", 0xFFDB2777L, createdAt = 0L)

    @Before
    fun setup() = runTest {
        store = InMemoryLedgerStore()
        createGoal = CreateGoalUseCase(store)
        updateGoal = UpdateGoalUseCase(store, store)
        updateProtection = UpdateGoalProtectionUseCase(store)
        updateStatus = UpdateGoalStatusUseCase(store)
        archiveGoal = ArchiveGoalUseCase(store, store)
        restoreGoal = RestoreGoalUseCase(store)
        allocate = AllocateToGoalUseCase(store, store, store)
        withdraw = WithdrawFromGoalUseCase(store, store, store, store)
        updateTx = UpdateTransactionUseCase(store, store, store, store)
        deleteTx = DeleteTransactionUseCase(store, store, store)

        store.createWallet(bca)
        store.createWallet(cash)
        store.upsertCategory(catExpense)
    }

    @Test
    fun `large progress calculation avoids Long overflow and rejects non-positive target`() {
        assertThat(FinancialEngine.calculateGoalProgressPermille(Long.MAX_VALUE / 2, Long.MAX_VALUE)).isEqualTo(499)
        assertThat(FinancialEngine.calculateGoalProgressPermille(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(1000)
        assertThrows(DomainException.InvalidAmountException::class.java) {
            FinancialEngine.calculateGoalProgressPermille(100L, 0L)
        }
        assertThrows(DomainException.InvalidAmountException::class.java) {
            FinancialEngine.calculateGoalProgressPermille(100L, -50L)
        }
    }

    @Test
    fun `direct expense invariants hold across STANDARD, PROTECTED, and STRICT and lock linked expense`() = runTest {
        val gStd = Goal("g-std", "Std", 2_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 100L, updatedAt = 100L)
        val gProt = Goal("g-prot", "Prot", 2_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 100L, updatedAt = 100L)
        val gStrict = Goal("g-strict", "Strict", 2_000_000L, protectionMode = ProtectionMode.STRICT, createdAt = 100L, updatedAt = 100L)
        createGoal(gStd)
        createGoal(gProt)
        createGoal(gStrict)

        allocate(gStd.id, bca.id, 1_000_000L, date = 200L)
        allocate(gProt.id, bca.id, 1_000_000L, date = 200L)
        allocate(gStrict.id, cash.id, 1_000_000L, date = 200L)

        // TotalAssets = 7M, Allocated = 3M, Protected = 2M, Free = 4M
        val before = FinancialEngine.calculateFinancialOverview(store.getAllWallets(), store.getAllTransactions(), store.getAllGoals(), store.getAllAllocations())
        assertThat(before.totalAssets).isEqualTo(7_000_000L)
        assertThat(before.allocatedFunds).isEqualTo(3_000_000L)
        assertThat(before.protectedFunds).isEqualTo(2_000_000L)
        assertThat(before.freeMoney).isEqualTo(4_000_000L)

        // DIRECT_EXPENSE 400k from STANDARD -> TotalAssets -400k, Allocated -400k, Free unchanged (4M), Protected unchanged (2M)
        withdraw(gStd.id, bca.id, 400_000L, WithdrawalTarget.DIRECT_EXPENSE, date = 300L, expenseCategoryId = catExpense.id)
        val afterStd = FinancialEngine.calculateFinancialOverview(store.getAllWallets(), store.getAllTransactions(), store.getAllGoals(), store.getAllAllocations())
        assertThat(afterStd.totalAssets).isEqualTo(6_600_000L)
        assertThat(afterStd.allocatedFunds).isEqualTo(2_600_000L)
        assertThat(afterStd.freeMoney).isEqualTo(4_000_000L)
        assertThat(afterStd.protectedFunds).isEqualTo(2_000_000L)

        // PROTECTED & STRICT require non-blank reason
        assertThrowsSuspend<DomainException.InvalidInputException> {
            withdraw(gProt.id, bca.id, 300_000L, WithdrawalTarget.DIRECT_EXPENSE, date = 300L, reason = " ", expenseCategoryId = catExpense.id)
        }
        assertThrowsSuspend<DomainException.InvalidInputException> {
            withdraw(gStrict.id, cash.id, 300_000L, WithdrawalTarget.DIRECT_EXPENSE, date = 300L, reason = null, expenseCategoryId = catExpense.id)
        }

        // Valid DIRECT_EXPENSE 300k from PROTECTED & 200k from STRICT
        withdraw(gProt.id, bca.id, 300_000L, WithdrawalTarget.DIRECT_EXPENSE, date = 300L, reason = "Emergency bill", expenseCategoryId = catExpense.id)
        withdraw(gStrict.id, cash.id, 200_000L, WithdrawalTarget.DIRECT_EXPENSE, date = 300L, reason = "Urgent", expenseCategoryId = catExpense.id)

        val afterProtected = FinancialEngine.calculateFinancialOverview(store.getAllWallets(), store.getAllTransactions(), store.getAllGoals(), store.getAllAllocations())
        assertThat(afterProtected.totalAssets).isEqualTo(6_100_000L)
        assertThat(afterProtected.allocatedFunds).isEqualTo(2_100_000L)
        assertThat(afterProtected.protectedFunds).isEqualTo(1_500_000L)
        assertThat(afterProtected.freeMoney).isEqualTo(4_000_000L)

        // Linked EXPENSE cannot be edited or deleted independently
        val linkedExpense = store.getAllTransactions().first { it.type == TransactionType.EXPENSE }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            updateTx(linkedExpense.copy(amount = 100_000L))
        }
        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            deleteTx(linkedExpense.id)
        }
    }

    @Test
    fun `goal completion, reopening, archival, restoration, target edit, and protection change`() = runTest {
        val goal = Goal("g-1", "Laptop", 3_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 100L, updatedAt = 100L)
        createGoal(goal)
        allocate(goal.id, bca.id, 1_000_000L, date = 200L)

        // Complete goal even before target reached -> blocks allocation, allows withdrawal
        updateStatus.markCompleted(goal.id, completedAt = 300L)
        assertThat(store.getGoalById(goal.id)?.status).isEqualTo(GoalStatus.COMPLETED)
        assertThat(store.getGoalById(goal.id)?.completedAt).isEqualTo(300L)

        assertThrowsSuspend<DomainException.InvalidTransactionMutationException> {
            allocate(goal.id, bca.id, 100_000L, date = 300L)
        }
        withdraw(goal.id, bca.id, 400_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = 350L)

        // Reopen clears completedAt and restores ACTIVE
        updateStatus.reopen(goal.id, updatedAt = 400L)
        assertThat(store.getGoalById(goal.id)?.status).isEqualTo(GoalStatus.ACTIVE)
        assertThat(store.getGoalById(goal.id)?.completedAt).isNull()

        // Protection mode upgrade reclassifies existing 600k into Protected Funds
        updateProtection(goal.id, ProtectionMode.PROTECTED, updatedAt = 450L)
        val afterProtect = FinancialEngine.calculateFinancialOverview(store.getAllWallets(), store.getAllTransactions(), store.getAllGoals(), store.getAllAllocations())
        assertThat(afterProtect.protectedFunds).isEqualTo(600_000L)

        // Target edit preserves allocation history
        updateGoal(store.getGoalById(goal.id)!!.copy(targetAmount = 1_200_000L, updatedAt = 500L))
        assertThat(FinancialEngine.calculateGoalAllocatedAmount(goal.id, store.getAllAllocations())).isEqualTo(600_000L)

        // Archive rejected while 600k remains; succeeds after full withdrawal; restore returns to ACTIVE
        assertThrowsSuspend<DomainException.InsufficientGoalAllocationException> {
            archiveGoal(goal.id, updatedAt = 600L)
        }
        withdraw(goal.id, bca.id, 600_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = 600L, reason = "Done")
        archiveGoal(goal.id, updatedAt = 650L)
        assertThat(store.getGoalById(goal.id)?.status).isEqualTo(GoalStatus.ARCHIVED)

        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            allocate(goal.id, bca.id, 100_000L, date = 700L)
        }
        assertThrowsSuspend<DomainException.ArchivedEntityException> {
            withdraw(goal.id, bca.id, 100_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = 700L, reason = "x")
        }

        restoreGoal(goal.id, updatedAt = 750L)
        assertThat(store.getGoalById(goal.id)?.status).isEqualTo(GoalStatus.ACTIVE)
    }

    @Test
    fun `invalid wallet and wallet-specific withdrawal cap reject with atomic rollback`() = runTest {
        val goal = Goal("g-2", "Trip", 5_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 100L, updatedAt = 100L)
        createGoal(goal)
        allocate(goal.id, bca.id, 2_000_000L, date = 200L)
        allocate(goal.id, cash.id, 500_000L, date = 200L)

        assertThrowsSuspend<DomainException.EntityNotFoundException> {
            allocate(goal.id, "w-missing", 100_000L, date = 200L)
        }
        assertThrowsSuspend<DomainException.InsufficientGoalAllocationException> {
            withdraw(goal.id, cash.id, 600_000L, WithdrawalTarget.RETURN_TO_FREE_MONEY, date = 300L)
        }
        assertThat(FinancialEngine.calculateGoalAllocatedAmount(goal.id, store.getAllAllocations())).isEqualTo(2_500_000L)
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
