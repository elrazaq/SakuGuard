package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WithdrawalTarget
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import java.util.UUID
import javax.inject.Inject

class CreateGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(goal: Goal) {
        LedgerValidator.requirePositiveAmount(goal.targetAmount, "Target amount")
        goalRepository.createGoal(goal)
    }
}

class UpdateGoalProtectionUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(goalId: String, mode: ProtectionMode, updatedAt: Long) {
        val goal = goalRepository.getGoalById(goalId)
            ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
        if (goal.status == GoalStatus.ARCHIVED) {
            throw DomainException.ArchivedEntityException("Cannot change protection on archived goal")
        }
        goalRepository.updateProtectionMode(goalId, mode, updatedAt)
    }
}

class ArchiveGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(goalId: String, updatedAt: Long) {
        transactionRepository.runInTransaction {
            val goal = goalRepository.getGoalById(goalId)
                ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
            val allocated = FinancialEngine.calculateGoalAllocatedAmount(
                goalId = goal.id,
                allocations = goalRepository.getAllAllocations(),
            )
            if (allocated > 0L) {
                throw DomainException.InsufficientGoalAllocationException(
                    "Cannot archive goal with outstanding allocations ($allocated)",
                )
            }
            goalRepository.updateStatus(goalId, GoalStatus.ARCHIVED, goal.completedAt, updatedAt)
        }
    }
}

class AllocateToGoalUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        goalId: String,
        walletId: String,
        amount: Long,
        date: Long,
        description: String = "",
        notes: String = "",
        now: Long = date,
    ) {
        LedgerValidator.requirePositiveAmount(amount, "Allocation amount")
        transactionRepository.runInTransaction {
            val wallet = walletRepository.getWalletById(walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: $walletId")
            if (wallet.isArchived) {
                throw DomainException.ArchivedEntityException("Cannot allocate from archived wallet")
            }
            if (LedgerValidator.isBeforeTrackingDay(date, wallet.trackingStartDate)) {
                throw DomainException.PreTrackingDateException("Allocation date precedes wallet tracking start")
            }
            val goal = goalRepository.getGoalById(goalId)
                ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
            if (goal.status == GoalStatus.ARCHIVED) {
                throw DomainException.ArchivedEntityException("Cannot allocate to archived goal")
            }

            val txId = UUID.randomUUID().toString()
            val tx = Transaction(
                id = txId,
                type = TransactionType.GOAL_ALLOCATION,
                amount = amount,
                walletId = walletId,
                goalId = goalId,
                description = description,
                notes = notes,
                transactionDate = date,
                createdAt = now,
                updatedAt = now,
            )
            val entry = GoalAllocationEntry(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                walletId = walletId,
                transactionId = txId,
                entryType = GoalLedgerType.ALLOCATION,
                amount = amount,
                createdAt = now,
            )

            val wallets = walletRepository.getAllWallets()
            val transactions = transactionRepository.getAllTransactions() + tx
            val allocations = goalRepository.getAllAllocations() + entry
            LedgerValidator.validateResultingState(wallets, transactions, allocations)

            transactionRepository.insertTransaction(tx)
            transactionRepository.insertAllocationEntry(entry)
        }
    }
}

class WithdrawFromGoalUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        goalId: String,
        walletId: String,
        amount: Long,
        target: WithdrawalTarget,
        date: Long,
        reason: String? = null,
        expenseCategoryId: String? = null,
        description: String = "",
        notes: String = "",
        now: Long = date,
    ) {
        LedgerValidator.requirePositiveAmount(amount, "Withdrawal amount")
        transactionRepository.runInTransaction {
            val wallet = walletRepository.getWalletById(walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: $walletId")
            if (wallet.isArchived) {
                throw DomainException.ArchivedEntityException("Cannot withdraw from archived wallet")
            }
            if (LedgerValidator.isBeforeTrackingDay(date, wallet.trackingStartDate)) {
                throw DomainException.PreTrackingDateException("Withdrawal date precedes wallet tracking start")
            }
            val goal = goalRepository.getGoalById(goalId)
                ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
            if (goal.status == GoalStatus.ARCHIVED) {
                throw DomainException.ArchivedEntityException("Cannot withdraw from archived goal")
            }

            if (target == WithdrawalTarget.DIRECT_EXPENSE) {
                val catId = expenseCategoryId
                    ?: throw DomainException.InvalidTransactionMutationException("Direct expense requires an expense category")
                val category = categoryRepository.getCategoryById(catId)
                    ?: throw DomainException.EntityNotFoundException("Category not found: $catId")
                if (category.isArchived) {
                    throw DomainException.ArchivedEntityException("Cannot use archived category")
                }
                if (category.type != CategoryType.EXPENSE) {
                    throw DomainException.InvalidTransactionMutationException("Direct expense requires an EXPENSE category")
                }
            }

            val withdrawTxId = UUID.randomUUID().toString()
            val withdrawTx = Transaction(
                id = withdrawTxId,
                type = TransactionType.GOAL_WITHDRAWAL,
                amount = amount,
                walletId = walletId,
                goalId = goalId,
                description = description,
                notes = notes,
                transactionDate = date,
                createdAt = now,
                updatedAt = now,
            )

            val expenseTx = if (target == WithdrawalTarget.DIRECT_EXPENSE) {
                Transaction(
                    id = UUID.randomUUID().toString(),
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    walletId = walletId,
                    categoryId = expenseCategoryId,
                    goalId = goalId,
                    description = description,
                    notes = notes,
                    transactionDate = date,
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                null
            }

            val entry = GoalAllocationEntry(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                walletId = walletId,
                transactionId = withdrawTxId,
                linkedExpenseTransactionId = expenseTx?.id,
                entryType = GoalLedgerType.WITHDRAWAL,
                amount = amount,
                reason = reason,
                withdrawalTarget = target,
                createdAt = now,
            )

            val wallets = walletRepository.getAllWallets()
            val newTxs = listOfNotNull(withdrawTx, expenseTx)
            val transactions = transactionRepository.getAllTransactions() + newTxs
            val allocations = goalRepository.getAllAllocations() + entry
            LedgerValidator.validateResultingState(wallets, transactions, allocations)

            transactionRepository.insertTransaction(withdrawTx)
            if (expenseTx != null) {
                transactionRepository.insertTransaction(expenseTx)
            }
            transactionRepository.insertAllocationEntry(entry)
        }
    }
}
