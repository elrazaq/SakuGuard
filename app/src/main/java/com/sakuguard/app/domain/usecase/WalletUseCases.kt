package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class WalletPortfolio(
    val activeWallets: List<WalletSummary>,
    val archivedWallets: List<WalletSummary>,
    val overview: FinancialOverview,
)

data class WalletGoalAllocationItem(
    val goal: Goal,
    val allocatedAmount: Long,
)

data class WalletDetail(
    val summary: WalletSummary,
    val goalAllocations: List<WalletGoalAllocationItem>,
)

class ObserveWalletPortfolioUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    operator fun invoke(): Flow<WalletPortfolio> = combine(
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeTransactions(),
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
    ) { wallets, transactions, goals, allocations ->
        val summaries = wallets.map { wallet ->
            FinancialEngine.summarizeWallet(wallet, transactions, allocations)
        }
        val overview = FinancialEngine.calculateFinancialOverview(
            wallets = wallets,
            transactions = transactions,
            goals = goals,
            allocations = allocations,
        )
        WalletPortfolio(
            activeWallets = summaries.filterNot { it.wallet.isArchived },
            archivedWallets = summaries.filter { it.wallet.isArchived },
            overview = overview,
        )
    }
}

class ObserveWalletDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    operator fun invoke(walletId: String): Flow<WalletDetail?> = combine(
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeTransactions(),
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
    ) { wallets, transactions, goals, allocations ->
        val wallet = wallets.firstOrNull { it.id == walletId } ?: return@combine null
        val summary = FinancialEngine.summarizeWallet(wallet, transactions, allocations)
        val goalAllocations = goals.mapNotNull { goal ->
            val allocated = FinancialEngine.calculateGoalAllocatedInWallet(
                goalId = goal.id,
                walletId = wallet.id,
                allocations = allocations,
            )
            if (allocated > 0L) WalletGoalAllocationItem(goal = goal, allocatedAmount = allocated) else null
        }
        WalletDetail(summary = summary, goalAllocations = goalAllocations)
    }
}

class CreateWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {
    suspend operator fun invoke(wallet: Wallet) {
        if (wallet.name.isBlank()) {
            throw DomainException.InvalidInputException("Wallet name cannot be blank")
        }
        LedgerValidator.requireNonNegativeAmount(wallet.openingBalance, "Opening balance")
        walletRepository.createWallet(wallet.copy(name = wallet.name.trim()))
    }
}

class UpdateWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(updatedWallet: Wallet) {
        if (updatedWallet.name.isBlank()) {
            throw DomainException.InvalidInputException("Wallet name cannot be blank")
        }
        LedgerValidator.requireNonNegativeAmount(updatedWallet.openingBalance, "Opening balance")
        val normalized = updatedWallet.copy(name = updatedWallet.name.trim())
        transactionRepository.runInTransaction {
            val existing = walletRepository.getWalletById(normalized.id)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: ${normalized.id}")
            val transactions = transactionRepository.getAllTransactions()
            for (tx in transactions) {
                if ((tx.walletId == existing.id || tx.destinationWalletId == existing.id) &&
                    tx.transactionDate < normalized.trackingStartDate
                ) {
                    throw DomainException.PreTrackingDateException(
                        "Existing transaction ${tx.id} precedes new tracking start date",
                    )
                }
            }
            val wallets = walletRepository.getAllWallets().map {
                if (it.id == normalized.id) normalized else it
            }
            val allocations = goalRepository.getAllAllocations()
            LedgerValidator.validateResultingState(wallets, transactions, allocations)
            walletRepository.updateWallet(normalized)
        }
    }
}

class ArchiveWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(walletId: String, archived: Boolean, updatedAt: Long) {
        transactionRepository.runInTransaction {
            walletRepository.getWalletById(walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: $walletId")
            if (archived) {
                val allocations = goalRepository.getAllAllocations()
                val allocated = FinancialEngine.calculateWalletAllocatedFunds(walletId, allocations)
                if (allocated > 0L) {
                    throw DomainException.InsufficientGoalAllocationException(
                        "Cannot archive wallet with active goal allocations ($allocated)",
                    )
                }
            }
            walletRepository.setArchived(walletId, archived, updatedAt)
        }
    }
}
