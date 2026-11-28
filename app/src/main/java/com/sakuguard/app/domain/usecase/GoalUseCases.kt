package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.GoalSummary
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.model.WithdrawalTarget
import com.sakuguard.app.domain.repository.CategoryRepository
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
import java.util.UUID
import javax.inject.Inject

data class GoalsPortfolio(
    val activeGoals: List<GoalSummary>,
    val completedGoals: List<GoalSummary>,
    val archivedGoals: List<GoalSummary>,
    val overview: FinancialOverview,
)

data class GoalWalletShare(
    val wallet: Wallet,
    val allocatedAmount: Long,
)

data class GoalHistoryItem(
    val entry: GoalAllocationEntry,
    val wallet: Wallet?,
)

data class GoalDetail(
    val summary: GoalSummary,
    val progressPermille: Int,
    val walletShares: List<GoalWalletShare>,
    val history: List<GoalHistoryItem>,
    val activeWallets: List<WalletSummary>,
)

class ObserveGoalsPortfolioUseCase(
    private val goalRepository: GoalRepository,
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(
        goalRepository: GoalRepository,
        walletRepository: WalletRepository,
        transactionRepository: TransactionRepository,
    ) : this(goalRepository, walletRepository, transactionRepository, Dispatchers.Default)

    operator fun invoke(): Flow<GoalsPortfolio> = combine(
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeAllWalletLedgerTotals(),
    ) { goals, allocations, wallets, totalsByWallet ->
        val summaries = goals.map { FinancialEngine.summarizeGoal(it, allocations) }
        val overview = FinancialEngine.calculateFinancialOverviewFromTotals(
            wallets = wallets,
            totalsByWallet = totalsByWallet,
            goals = goals,
            allocations = allocations,
        )
        GoalsPortfolio(
            activeGoals = summaries.filter { it.goal.status == GoalStatus.ACTIVE },
            completedGoals = summaries.filter { it.goal.status == GoalStatus.COMPLETED },
            archivedGoals = summaries.filter { it.goal.status == GoalStatus.ARCHIVED },
            overview = overview,
        )
    }.distinctUntilChanged().flowOn(computeDispatcher)
}

class ObserveGoalDetailUseCase(
    private val goalRepository: GoalRepository,
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(
        goalRepository: GoalRepository,
        walletRepository: WalletRepository,
        transactionRepository: TransactionRepository,
    ) : this(goalRepository, walletRepository, transactionRepository, Dispatchers.Default)

    operator fun invoke(goalId: String): Flow<GoalDetail?> = combine(
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeAllWalletLedgerTotals(),
    ) { goals, allocations, wallets, totalsByWallet ->
        val goal = goals.firstOrNull { it.id == goalId } ?: return@combine null
        val summary = FinancialEngine.summarizeGoal(goal, allocations)
        val progress = FinancialEngine.calculateGoalProgressPermille(
            allocated = summary.allocatedAmount,
            target = goal.targetAmount,
        )
        val walletMap = wallets.associateBy { it.id }
        val walletShares = summary.walletBreakdown.mapNotNull { (walletId, amount) ->
            walletMap[walletId]?.let { GoalWalletShare(wallet = it, allocatedAmount = amount) }
        }
        val history = allocations
            .filter { it.goalId == goalId }
            .sortedByDescending { it.createdAt }
            .map { entry -> GoalHistoryItem(entry = entry, wallet = walletMap[entry.walletId]) }
        val activeWallets = wallets
            .filterNot { it.isArchived }
            .map { wallet ->
                val totals = totalsByWallet[wallet.id] ?: WalletLedgerTotals(wallet.id, 0L, 0L, 0L, 0L)
                FinancialEngine.summarizeWalletFromTotals(wallet, totals, allocations)
            }
        GoalDetail(
            summary = summary,
            progressPermille = progress,
            walletShares = walletShares,
            history = history,
            activeWallets = activeWallets,
        )
    }.distinctUntilChanged().flowOn(computeDispatcher)
}

class CreateGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(goal: Goal) {
        if (goal.name.isBlank()) {
            throw DomainException.InvalidInputException("Goal name cannot be blank")
        }
        LedgerValidator.requirePositiveAmount(goal.targetAmount, "Target amount")
        goalRepository.createGoal(goal.copy(name = goal.name.trim()))
    }
}

class UpdateGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(updated: Goal) {
        if (updated.name.isBlank()) {
            throw DomainException.InvalidInputException("Goal name cannot be blank")
        }
        LedgerValidator.requirePositiveAmount(updated.targetAmount, "Target amount")
        transactionRepository.runInTransaction {
            val existing = goalRepository.getGoalById(updated.id)
                ?: throw DomainException.EntityNotFoundException("Goal not found: ${updated.id}")
            if (existing.status == GoalStatus.ARCHIVED) {
                throw DomainException.ArchivedEntityException("Cannot edit archived goal")
            }
            goalRepository.updateGoal(
                existing.copy(
                    name = updated.name.trim(),
                    targetAmount = updated.targetAmount,
                    deadlineEpochMillis = updated.deadlineEpochMillis,
                    description = updated.description.trim(),
                    iconKey = updated.iconKey,
                    colorHex = updated.colorHex,
                    protectionMode = updated.protectionMode,
                    updatedAt = updated.updatedAt,
                ),
            )
        }
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

class UpdateGoalStatusUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend fun markCompleted(goalId: String, completedAt: Long) {
        val goal = goalRepository.getGoalById(goalId)
            ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
        if (goal.status == GoalStatus.ARCHIVED) {
            throw DomainException.ArchivedEntityException("Cannot complete archived goal")
        }
        goalRepository.updateStatus(goalId, GoalStatus.COMPLETED, completedAt, completedAt)
    }

    suspend fun reopen(goalId: String, updatedAt: Long) {
        val goal = goalRepository.getGoalById(goalId)
            ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
        if (goal.status == GoalStatus.ARCHIVED) {
            throw DomainException.ArchivedEntityException("Restore archived goal before reopening")
        }
        goalRepository.updateStatus(goalId, GoalStatus.ACTIVE, null, updatedAt)
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

class RestoreGoalUseCase @Inject constructor(
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(goalId: String, updatedAt: Long) {
        val goal = goalRepository.getGoalById(goalId)
            ?: throw DomainException.EntityNotFoundException("Goal not found: $goalId")
        if (goal.status != GoalStatus.ARCHIVED) return
        goalRepository.updateStatus(goalId, GoalStatus.ACTIVE, null, updatedAt)
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
            if (goal.status == GoalStatus.COMPLETED) {
                throw DomainException.InvalidTransactionMutationException("Cannot allocate to completed goal")
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

            transactionRepository.insertTransaction(tx)
            transactionRepository.insertAllocationEntry(entry)
            LedgerValidator.validateAffectedWallets(
                affectedWallets = listOf(wallet),
                transactionRepository = transactionRepository,
                allocations = goalRepository.getAllAllocations(),
                affectedGoalIds = setOf(goalId),
            )
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

            if (goal.protectionMode != ProtectionMode.STANDARD && reason.isNullOrBlank()) {
                throw DomainException.InvalidInputException(
                    "Withdrawal reason is required for ${goal.protectionMode.name} goals",
                )
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
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                withdrawalTarget = target,
                createdAt = now,
            )

            transactionRepository.insertTransaction(withdrawTx)
            if (expenseTx != null) {
                transactionRepository.insertTransaction(expenseTx)
            }
            transactionRepository.insertAllocationEntry(entry)
            LedgerValidator.validateAffectedWallets(
                affectedWallets = listOf(wallet),
                transactionRepository = transactionRepository,
                allocations = goalRepository.getAllAllocations(),
                affectedGoalIds = setOf(goalId),
            )
        }
    }
}
