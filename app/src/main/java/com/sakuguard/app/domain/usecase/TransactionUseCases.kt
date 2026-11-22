package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class TransactionFeedItem(
    val transaction: Transaction,
    val wallet: Wallet?,
    val destinationWallet: Wallet?,
    val category: Category?,
)

data class TransactionsFeed(
    val items: List<TransactionFeedItem>,
    val wallets: List<Wallet>,
    val categories: List<Category>,
)

class ObserveTransactionsFeedUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(): Flow<TransactionsFeed> = combine(
        transactionRepository.observeTransactions(),
        walletRepository.observeWallets(includeArchived = true),
        categoryRepository.observeCategories(includeArchived = true),
    ) { transactions, wallets, categories ->
        val walletMap = wallets.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val items = transactions.map { tx ->
            TransactionFeedItem(
                transaction = tx,
                wallet = walletMap[tx.walletId],
                destinationWallet = tx.destinationWalletId?.let { walletMap[it] },
                category = tx.categoryId?.let { categoryMap[it] },
            )
        }
        TransactionsFeed(items = items, wallets = wallets, categories = categories)
    }
}

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
            if (LedgerValidator.isBeforeTrackingDay(transaction.transactionDate, sourceWallet.trackingStartDate)) {
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
                    if (LedgerValidator.isBeforeTrackingDay(transaction.transactionDate, destWallet.trackingStartDate)) {
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
            if (existing.type == TransactionType.GOAL_ALLOCATION ||
                existing.type == TransactionType.GOAL_WITHDRAWAL
            ) {
                throw DomainException.InvalidTransactionMutationException(
                    "Goal ledger transactions cannot be edited via transaction management",
                )
            }
            if (existing.type != updated.type) {
                throw DomainException.InvalidTransactionMutationException("Cannot change transaction type")
            }

            // Archived wallet policy: reject mutations involving any original or resulting archived wallet
            val affectedWalletIds = listOfNotNull(
                existing.walletId,
                existing.destinationWalletId,
                updated.walletId,
                updated.destinationWalletId,
            ).distinct()
            val affectedWallets = affectedWalletIds.associateWith { id ->
                walletRepository.getWalletById(id)
                    ?: throw DomainException.EntityNotFoundException("Wallet not found: $id")
            }
            for ((_, w) in affectedWallets) {
                if (w.isArchived) {
                    throw DomainException.ArchivedEntityException(
                        "Cannot modify transaction involving archived wallet '${w.name}'",
                    )
                }
            }

            val wallet = affectedWallets.getValue(updated.walletId)
            if (LedgerValidator.isBeforeTrackingDay(updated.transactionDate, wallet.trackingStartDate)) {
                throw DomainException.PreTrackingDateException("Transaction date precedes wallet tracking start")
            }

            when (updated.type) {
                TransactionType.INCOME, TransactionType.EXPENSE -> {
                    val catId = updated.categoryId
                        ?: throw DomainException.InvalidTransactionMutationException("Category is required")
                    val category = categoryRepository.getCategoryById(catId)
                        ?: throw DomainException.EntityNotFoundException("Category not found: $catId")
                    if (category.isArchived && catId != existing.categoryId) {
                        throw DomainException.ArchivedEntityException("Cannot assign archived category")
                    }
                    val expectedType = if (updated.type == TransactionType.INCOME) {
                        CategoryType.INCOME
                    } else {
                        CategoryType.EXPENSE
                    }
                    if (category.type != expectedType) {
                        throw DomainException.InvalidTransactionMutationException("Category type mismatch")
                    }
                }
                TransactionType.TRANSFER -> {
                    val destId = updated.destinationWalletId
                        ?: throw DomainException.InvalidTransferException("Destination wallet required")
                    if (destId == updated.walletId) {
                        throw DomainException.InvalidTransferException("Source and destination wallets cannot be identical")
                    }
                    val dest = affectedWallets.getValue(destId)
                    if (LedgerValidator.isBeforeTrackingDay(updated.transactionDate, dest.trackingStartDate)) {
                        throw DomainException.PreTrackingDateException("Transfer date precedes destination wallet tracking start")
                    }
                }
                else -> Unit
            }

            val currentTransactions = transactionRepository.getAllTransactions()
            val currentAllocations = goalRepository.getAllAllocations()

            val linkedAllocForExpense = transactionRepository.getAllocationByLinkedExpenseId(existing.id)

            val updatedTransactions: List<Transaction>
            val updatedAllocations: List<com.sakuguard.app.domain.model.GoalAllocationEntry>
            var extraTxToUpdate: Transaction? = null
            var allocToUpdate: com.sakuguard.app.domain.model.GoalAllocationEntry? = null

            if (linkedAllocForExpense != null) {
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
            } else {
                updatedTransactions = currentTransactions.map { if (it.id == updated.id) updated else it }
                updatedAllocations = currentAllocations
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

            if (existing.type == TransactionType.GOAL_ALLOCATION ||
                existing.type == TransactionType.GOAL_WITHDRAWAL
            ) {
                throw DomainException.InvalidTransactionMutationException(
                    "Goal ledger transactions cannot be deleted via transaction management",
                )
            }

            val affectedWalletIds = listOfNotNull(existing.walletId, existing.destinationWalletId).distinct()
            for (wId in affectedWalletIds) {
                val wallet = walletRepository.getWalletById(wId)
                    ?: throw DomainException.EntityNotFoundException("Wallet not found: $wId")
                if (wallet.isArchived) {
                    throw DomainException.ArchivedEntityException(
                        "Cannot delete transaction involving archived wallet '${wallet.name}'",
                    )
                }
            }

            val linkedAlloc = transactionRepository.getAllocationByLinkedExpenseId(existing.id)
            val txIdsToDelete = buildSet {
                add(existing.id)
                if (linkedAlloc != null) {
                    add(linkedAlloc.transactionId)
                }
            }

            val remainingTxs = transactionRepository.getAllTransactions().filterNot { it.id in txIdsToDelete }
            val remainingAllocs = if (linkedAlloc != null) {
                goalRepository.getAllAllocations().filterNot { it.id == linkedAlloc.id }
            } else {
                goalRepository.getAllAllocations()
            }

            LedgerValidator.validateResultingState(
                wallets = walletRepository.getAllWallets(),
                transactions = remainingTxs,
                allocations = remainingAllocs,
            )

            if (linkedAlloc != null) {
                transactionRepository.deleteAllocationEntryById(linkedAlloc.id)
            }
            for (id in txIdsToDelete) {
                transactionRepository.deleteTransactionById(id)
            }
        }
    }
}
