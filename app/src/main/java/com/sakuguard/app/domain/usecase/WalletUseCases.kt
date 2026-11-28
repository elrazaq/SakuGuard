package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletLedgerTotals
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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

class ObserveWalletPortfolioUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(
        walletRepository: WalletRepository,
        transactionRepository: TransactionRepository,
        goalRepository: GoalRepository,
    ) : this(walletRepository, transactionRepository, goalRepository, Dispatchers.Default)

    operator fun invoke(): Flow<WalletPortfolio> = combine(
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeAllWalletLedgerTotals(),
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
    ) { wallets, totalsByWallet, goals, allocations ->
        val summaries = wallets.map { wallet ->
            val totals = totalsByWallet[wallet.id] ?: WalletLedgerTotals(wallet.id, 0L, 0L, 0L, 0L)
            FinancialEngine.summarizeWalletFromTotals(wallet, totals, allocations)
        }
        val overview = FinancialEngine.calculateFinancialOverviewFromTotals(
            wallets = wallets,
            totalsByWallet = totalsByWallet,
            goals = goals,
            allocations = allocations,
        )
        WalletPortfolio(
            activeWallets = summaries.filterNot { it.wallet.isArchived },
            archivedWallets = summaries.filter { it.wallet.isArchived },
            overview = overview,
        )
    }.distinctUntilChanged().flowOn(computeDispatcher)
}

class ObserveWalletDetailUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(
        walletRepository: WalletRepository,
        transactionRepository: TransactionRepository,
        goalRepository: GoalRepository,
    ) : this(walletRepository, transactionRepository, goalRepository, Dispatchers.Default)

    operator fun invoke(walletId: String): Flow<WalletDetail?> = combine(
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeAllWalletLedgerTotals(),
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
    ) { wallets, totalsByWallet, goals, allocations ->
        val wallet = wallets.firstOrNull { it.id == walletId } ?: return@combine null
        val totals = totalsByWallet[wallet.id] ?: WalletLedgerTotals(wallet.id, 0L, 0L, 0L, 0L)
        val summary = FinancialEngine.summarizeWalletFromTotals(wallet, totals, allocations)
        val goalAllocations = goals.mapNotNull { goal ->
            val allocated = FinancialEngine.calculateGoalAllocatedInWallet(
                goalId = goal.id,
                walletId = wallet.id,
                allocations = allocations,
            )
            if (allocated > 0L) WalletGoalAllocationItem(goal = goal, allocatedAmount = allocated) else null
        }
        WalletDetail(summary = summary, goalAllocations = goalAllocations)
    }.distinctUntilChanged().flowOn(computeDispatcher)
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
            val earliestTxDate = transactionRepository.getEarliestTransactionDateForWallet(existing.id)
            if (earliestTxDate != null &&
                LedgerValidator.isBeforeTrackingDay(earliestTxDate, normalized.trackingStartDate)
            ) {
                throw DomainException.PreTrackingDateException(
                    "Existing transaction precedes new tracking start date",
                )
            }
            walletRepository.updateWallet(normalized)
            LedgerValidator.validateAffectedWallets(
                affectedWallets = listOf(normalized),
                transactionRepository = transactionRepository,
                allocations = goalRepository.getAllAllocations(),
            )
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

class DeleteWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(walletId: String) {
        transactionRepository.runInTransaction {
            walletRepository.getWalletById(walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: $walletId")
            val txCount = transactionRepository.countTransactionsForWallet(walletId)
            val allocCount = goalRepository.countAllocationsForWallet(walletId)
            if (txCount > 0 || allocCount > 0) {
                throw DomainException.InvalidTransactionMutationException(
                    "Cannot delete wallet with existing history ($txCount txs, $allocCount allocations)",
                )
            }
            walletRepository.deleteWallet(walletId)
        }
    }
}
