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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
}

class ObserveTransactionDetailUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(transactionId: String): Flow<TransactionFeedItem?> = combine(
        transactionRepository.observeTransactionById(transactionId),
        walletRepository.observeWallets(includeArchived = true),
        categoryRepository.observeCategories(includeArchived = true),
    ) { tx, wallets, categories ->
        tx ?: return@combine null
        val walletMap = wallets.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        TransactionFeedItem(
            transaction = tx,
            wallet = walletMap[tx.walletId],
            destinationWallet = tx.destinationWalletId?.let { walletMap[it] },
            category = tx.categoryId?.let { categoryMap[it] },
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
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

            transactionRepository.insertTransaction(transaction)
            val affectedIds = listOfNotNull(transaction.walletId, transaction.destinationWalletId).distinct()
            val affectedWallets = affectedIds.mapNotNull { walletRepository.getWalletById(it) }
            LedgerValidator.validateAffectedWallets(
                affectedWallets = affectedWallets,
                transactionRepository = transactionRepository,
                allocations = goalRepository.getAllAllocations(),
            )
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

            val linkedAllocForExpense = transactionRepository.getAllocationByLinkedExpenseId(existing.id)
            if (linkedAllocForExpense != null) {
                throw DomainException.InvalidTransactionMutationException(
                    "Expense linked to goal withdrawal cannot be edited independently",
                )
            }

            transactionRepository.updateTransactionRecord(updated)
            LedgerValidator.validateAffectedWallets(
                affectedWallets = affectedWallets.values.toList(),
                transactionRepository = transactionRepository,
                allocations = goalRepository.getAllAllocations(),
            )
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
            val affectedWallets = affectedWalletIds.map { wId ->
                val wallet = walletRepository.getWalletById(wId)
                    ?: throw DomainException.EntityNotFoundException("Wallet not found: $wId")
                if (wallet.isArchived) {
                    throw DomainException.ArchivedEntityException(
                        "Cannot delete transaction involving archived wallet '${wallet.name}'",
                    )
                }
                wallet
            }

            val linkedAlloc = transactionRepository.getAllocationByLinkedExpenseId(existing.id)
            if (linkedAlloc != null) {
                throw DomainException.InvalidTransactionMutationException(
                    "Expense linked to goal withdrawal cannot be deleted independently",
                )
            }

            transactionRepository.deleteTransactionById(existing.id)
            LedgerValidator.validateAffectedWallets(
                affectedWallets = affectedWallets,
                transactionRepository = transactionRepository,
                allocations = goalRepository.getAllAllocations(),
            )
        }
    }
}
