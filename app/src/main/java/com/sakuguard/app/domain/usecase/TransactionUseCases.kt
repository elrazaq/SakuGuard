package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import javax.inject.Inject

class RecordTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(transaction: Transaction) {
        if (transaction.type == TransactionType.GOAL_ALLOCATION ||
            transaction.type == TransactionType.GOAL_WITHDRAWAL
        ) {
            throw DomainException.InvalidTransactionMutationException(
                "Use AllocateToGoalUseCase or WithdrawFromGoalUseCase for goal ledger entries",
            )
        }
        LedgerValidator.requirePositiveAmount(transaction.amount)

        transactionRepository.runInTransaction {
            val sourceWallet = walletRepository.getWalletById(transaction.walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: ${transaction.walletId}")
            if (sourceWallet.isArchived) {
                throw DomainException.ArchivedEntityException("Cannot record transaction on archived wallet")
            }
            if (transaction.transactionDate < sourceWallet.trackingStartDate) {
                throw DomainException.PreTrackingDateException("Transaction date precedes wallet tracking start")
            }

            when (transaction.type) {
                TransactionType.INCOME, TransactionType.EXPENSE -> {
                    val catId = transaction.categoryId
                        ?: throw DomainException.InvalidTransactionMutationException("Category is required")
                    val category = categoryRepository.getCategoryById(catId)
                        ?: throw DomainException.EntityNotFoundException("Category not found: $catId")
                    if (category.isArchived) {
                        throw DomainException.ArchivedEntityException("Cannot use archived category")
                    }
                    val expectedType = if (transaction.type == TransactionType.INCOME) {
                        CategoryType.INCOME
                    } else {
                        CategoryType.EXPENSE
                    }
                    if (category.type != expectedType) {
                        throw DomainException.InvalidTransactionMutationException("Category type mismatch")
                    }
                }
                TransactionType.TRANSFER -> {
                    val destId = transaction.destinationWalletId
                        ?: throw DomainException.InvalidTransferException("Destination wallet required")
                    if (destId == transaction.walletId) {
                        throw DomainException.InvalidTransferException("Source and destination wallets cannot be identical")
                    }
                    val destWallet = walletRepository.getWalletById(destId)
                        ?: throw DomainException.EntityNotFoundException("Destination wallet not found: $destId")
                    if (destWallet.isArchived) {
                        throw DomainException.ArchivedEntityException("Cannot transfer to archived wallet")
                    }
                    if (transaction.transactionDate < destWallet.trackingStartDate) {
                        throw DomainException.PreTrackingDateException("Transfer date precedes destination wallet tracking start")
                    }
                }
                else -> Unit
            }

            val wallets = walletRepository.getAllWallets()
            val transactions = transactionRepository.getAllTransactions() + transaction
            val allocations = goalRepository.getAllAllocations()
            LedgerValidator.validateResultingState(wallets, transactions, allocations)

            transactionRepository.insertTransaction(transaction)
        }
    }
}

class UpdateTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(updated: Transaction) {
        LedgerValidator.requirePositiveAmount(updated.amount)
        transactionRepository.runInTransaction {
            val existing = transactionRepository.getTransactionById(updated.id)
                ?: throw DomainException.EntityNotFoundException("Transaction not found: ${updated.id}")
            if (existing.type != updated.type) {
                throw DomainException.InvalidTransactionMutationException("Cannot change transaction type")
            }

            val wallet = walletRepository.getWalletById(updated.walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: ${updated.walletId}")
            if (wallet.isArchived && updated.walletId != existing.walletId) {
                throw DomainException.ArchivedEntityException("Cannot move transaction to archived wallet")
            }
            if (updated.transactionDate < wallet.trackingStartDate) {
                throw DomainException.PreTrackingDateException("Transaction date precedes wallet tracking start")
            }

            if (updated.type == TransactionType.TRANSFER) {
                val destId = updated.destinationWalletId
                    ?: throw DomainException.InvalidTransferException("Destination wallet required")
                if (destId == updated.walletId) {
                    throw DomainException.InvalidTransferException("Source and destination wallets cannot be identical")
                }
                val dest = walletRepository.getWalletById(destId)
                    ?: throw DomainException.EntityNotFoundException("Destination wallet not found: $destId")
                if (dest.isArchived && destId != existing.destinationWalletId) {
                    throw DomainException.ArchivedEntityException("Cannot transfer to archived wallet")
                }
                if (updated.transactionDate < dest.trackingStartDate) {
                    throw DomainException.PreTrackingDateException("Transfer date precedes destination wallet tracking start")
                }
            }

            if (updated.categoryId != null && updated.categoryId != existing.categoryId) {
                val category = categoryRepository.getCategoryById(updated.categoryId)
                    ?: throw DomainException.EntityNotFoundException("Category not found: ${updated.categoryId}")
                if (category.isArchived) {
                    throw DomainException.ArchivedEntityException("Cannot assign archived category")
                }
            }

            val currentTransactions = transactionRepository.getAllTransactions()
            val currentAllocations = goalRepository.getAllAllocations()

            // Handle linked goal allocation / direct-expense pair atomically
            val directAllocForTx = transactionRepository.getAllocationByTransactionId(existing.id)
            val linkedAllocForExpense = transactionRepository.getAllocationByLinkedExpenseId(existing.id)

            val updatedTransactions: List<Transaction>
            val updatedAllocations: List<com.sakuguard.app.domain.model.GoalAllocationEntry>
            var extraTxToUpdate: Transaction? = null
            var allocToUpdate: com.sakuguard.app.domain.model.GoalAllocationEntry? = null

            when {
                directAllocForTx != null -> {
                    val syncedAlloc = directAllocForTx.copy(
                        walletId = updated.walletId,
                        amount = updated.amount,
                    )
                    allocToUpdate = syncedAlloc
                    val linkedExpenseId = directAllocForTx.linkedExpenseTransactionId
                    if (linkedExpenseId != null) {
                        val linkedExpense = currentTransactions.firstOrNull { it.id == linkedExpenseId }
                            ?: throw DomainException.InvalidTransactionMutationException("Missing linked expense $linkedExpenseId")
                        extraTxToUpdate = linkedExpense.copy(
                            walletId = updated.walletId,
                            amount = updated.amount,
                            transactionDate = updated.transactionDate,
                            updatedAt = updated.updatedAt,
                        )
                    }
                    updatedTransactions = currentTransactions.map {
                        when (it.id) {
                            updated.id -> updated
                            extraTxToUpdate?.id -> extraTxToUpdate
                            else -> it
                        }
                    }
                    updatedAllocations = currentAllocations.map {
                        if (it.id == syncedAlloc.id) syncedAlloc else it
                    }
                }
                linkedAllocForExpense != null -> {
                    val syncedAlloc = linkedAllocForExpense.copy(
                        walletId = updated.walletId,
                        amount = updated.amount,
                    )
                    allocToUpdate = syncedAlloc
                    val withdrawTx = currentTransactions.firstOrNull { it.id == linkedAllocForExpense.transactionId }
                        ?: throw DomainException.InvalidTransactionMutationException("Missing linked withdrawal ${linkedAllocForExpense.transactionId}")
                    extraTxToUpdate = withdrawTx.copy(
                        walletId = updated.walletId,
                        amount = updated.amount,
                        transactionDate = updated.transactionDate,
                        updatedAt = updated.updatedAt,
                    )
                    updatedTransactions = currentTransactions.map {
                        when (it.id) {
                            updated.id -> updated
                            extraTxToUpdate.id -> extraTxToUpdate
                            else -> it
                        }
                    }
                    updatedAllocations = currentAllocations.map {
                        if (it.id == syncedAlloc.id) syncedAlloc else it
                    }
                }
                else -> {
                    updatedTransactions = currentTransactions.map { if (it.id == updated.id) updated else it }
                    updatedAllocations = currentAllocations
                }
            }

            LedgerValidator.validateResultingState(
                wallets = walletRepository.getAllWallets(),
                transactions = updatedTransactions,
                allocations = updatedAllocations,
            )

            transactionRepository.updateTransactionRecord(updated)
            if (extraTxToUpdate != null) {
                transactionRepository.updateTransactionRecord(extraTxToUpdate)
            }
            if (allocToUpdate != null) {
                transactionRepository.updateAllocationEntry(allocToUpdate)
            }
        }
    }
}

class DeleteTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(transactionId: String) {
        transactionRepository.runInTransaction {
            val existing = transactionRepository.getTransactionById(transactionId)
                ?: throw DomainException.EntityNotFoundException("Transaction not found: $transactionId")

            val directAlloc = transactionRepository.getAllocationByTransactionId(existing.id)
            val linkedAlloc = transactionRepository.getAllocationByLinkedExpenseId(existing.id)
            val allocToDelete = directAlloc ?: linkedAlloc

            val txIdsToDelete = buildSet {
                add(existing.id)
                if (allocToDelete != null) {
                    add(allocToDelete.transactionId)
                    allocToDelete.linkedExpenseTransactionId?.let { add(it) }
                }
            }

            val remainingTxs = transactionRepository.getAllTransactions().filterNot { it.id in txIdsToDelete }
            val remainingAllocs = if (allocToDelete != null) {
                goalRepository.getAllAllocations().filterNot { it.id == allocToDelete.id }
            } else {
                goalRepository.getAllAllocations()
            }

            LedgerValidator.validateResultingState(
                wallets = walletRepository.getAllWallets(),
                transactions = remainingTxs,
                allocations = remainingAllocs,
            )

            if (allocToDelete != null) {
                transactionRepository.deleteAllocationEntryById(allocToDelete.id)
            }
            for (id in txIdsToDelete) {
                transactionRepository.deleteTransactionById(id)
            }
        }
    }
}
