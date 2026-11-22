package com.sakuguard.app.domain.usecase

import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetAlertEvent
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.BudgetSummary
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.repository.BudgetRepository
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class BudgetsOverview(
    val activeBudgets: List<BudgetSummary> = emptyList(),
    val archivedBudgets: List<BudgetSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBudgetsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
        referenceDate: LocalDate = LocalDate.now(zoneId),
    ): Flow<BudgetsOverview> = combine(
        budgetRepository.observeBudgets(includeArchived = true),
        categoryRepository.observeCategories(includeArchived = true),
    ) { budgets, categories ->
        budgets to categories.associateBy { it.id }
    }.flatMapLatest { (budgets, categoryMap) ->
        if (budgets.isEmpty()) {
            return@flatMapLatest flowOf(BudgetsOverview())
        }
        val summaryFlows = budgets.map { budget ->
            val window = BudgetPeriodResolver.resolveWindow(budget, referenceDate, zoneId)
            val spentFlow = if (window.isActiveInPeriod || budget.periodType == BudgetPeriodType.CUSTOM) {
                transactionRepository.observeExpenseSumForCategoryPeriod(
                    categoryId = budget.categoryId,
                    startInclusive = window.startInclusive,
                    endExclusive = window.endExclusive,
                )
            } else {
                flowOf(0L)
            }
            spentFlow.map { spent ->
                FinancialEngine.summarizeBudget(
                    budget = budget,
                    category = categoryMap[budget.categoryId],
                    spentAmount = spent,
                    effectiveStart = window.startInclusive,
                    effectiveEndExclusive = window.endExclusive,
                )
            }
        }
        combine(summaryFlows) { summaries ->
            BudgetsOverview(
                activeBudgets = summaries.filterNot { it.budget.isArchived },
                archivedBudgets = summaries.filter { it.budget.isArchived },
            )
        }
    }.distinctUntilChanged()
}

class CreateBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        budget: Budget,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        if (budget.name.isBlank()) {
            throw DomainException.InvalidInputException("Budget name cannot be blank")
        }
        LedgerValidator.requirePositiveAmount(budget.limitAmount, "Budget limit")

        val normalized = normalizeBudgetDates(budget, zoneId)
        transactionRepository.runInTransaction {
            val category = categoryRepository.getCategoryById(normalized.categoryId)
                ?: throw DomainException.EntityNotFoundException("Category not found: ${normalized.categoryId}")
            if (category.isArchived) {
                throw DomainException.ArchivedEntityException("Cannot create budget for archived category")
            }
            if (category.type != CategoryType.EXPENSE) {
                throw DomainException.InvalidInputException("Budgets only support EXPENSE categories")
            }
            val existing = budgetRepository.getAllBudgets()
            for (other in existing) {
                if (BudgetPeriodResolver.overlaps(normalized, other, zoneId)) {
                    throw DomainException.InvalidInputException(
                        "An active budget already exists for this category and period",
                    )
                }
            }
            budgetRepository.createBudget(normalized)
        }
    }
}

class UpdateBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        updated: Budget,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        if (updated.name.isBlank()) {
            throw DomainException.InvalidInputException("Budget name cannot be blank")
        }
        LedgerValidator.requirePositiveAmount(updated.limitAmount, "Budget limit")

        val normalized = normalizeBudgetDates(updated, zoneId)
        transactionRepository.runInTransaction {
            val existing = budgetRepository.getBudgetById(normalized.id)
                ?: throw DomainException.EntityNotFoundException("Budget not found: ${normalized.id}")
            if (existing.isArchived) {
                throw DomainException.ArchivedEntityException("Cannot edit archived budget")
            }
            val category = categoryRepository.getCategoryById(normalized.categoryId)
                ?: throw DomainException.EntityNotFoundException("Category not found: ${normalized.categoryId}")
            if (category.isArchived && normalized.categoryId != existing.categoryId) {
                throw DomainException.ArchivedEntityException("Cannot assign archived category")
            }
            if (category.type != CategoryType.EXPENSE) {
                throw DomainException.InvalidInputException("Budgets only support EXPENSE categories")
            }
            val allBudgets = budgetRepository.getAllBudgets()
            for (other in allBudgets) {
                if (other.id != normalized.id && BudgetPeriodResolver.overlaps(normalized, other, zoneId)) {
                    throw DomainException.InvalidInputException(
                        "An active budget already exists for this category and period",
                    )
                }
            }
            budgetRepository.updateBudget(normalized)
        }
    }
}

class DeleteBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
) {
    suspend operator fun invoke(budgetId: String) {
        budgetRepository.deleteBudgetById(budgetId)
    }
}

class ArchiveBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        budgetId: String,
        archived: Boolean,
        updatedAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        transactionRepository.runInTransaction {
            val budget = budgetRepository.getBudgetById(budgetId)
                ?: throw DomainException.EntityNotFoundException("Budget not found: $budgetId")
            if (!archived && budget.isArchived) {
                val restored = budget.copy(isArchived = false, updatedAt = updatedAt)
                val others = budgetRepository.getAllBudgets()
                for (other in others) {
                    if (other.id != restored.id && BudgetPeriodResolver.overlaps(restored, other, zoneId)) {
                        throw DomainException.InvalidInputException(
                            "Cannot restore budget: overlaps with active budget '${other.name}'",
                        )
                    }
                }
            }
            budgetRepository.setArchived(budgetId, archived, updatedAt)
        }
    }
}

/**
 * Evaluates active budgets inside a single database transaction and emits deduplicated
 * in-app threshold alert events when spending crosses 80% or 100%.
 */
class EvaluateBudgetAlertsUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
        referenceDate: LocalDate = LocalDate.now(zoneId),
        now: Long = System.currentTimeMillis(),
    ): List<BudgetAlertEvent> {
        val alerts = mutableListOf<BudgetAlertEvent>()
        transactionRepository.runInTransaction {
            val budgets = budgetRepository.getAllBudgets().filterNot { it.isArchived }
            for (budget in budgets) {
                val window = BudgetPeriodResolver.resolveWindow(budget, referenceDate, zoneId)
                if (!window.isActiveInPeriod) continue

                val spent = transactionRepository.getExpenseSumForCategoryPeriod(
                    categoryId = budget.categoryId,
                    startInclusive = window.startInclusive,
                    endExclusive = window.endExclusive,
                )
                val permille = FinancialEngine.calculateGoalProgressPermille(spent, budget.limitAmount)
                val currentLevel = when {
                    budget.alert100Enabled && spent > budget.limitAmount -> 100
                    budget.alert80Enabled && permille >= 800 -> 80
                    else -> 0
                }

                val samePeriod = budget.lastAlertedPeriodKey == window.periodKey
                val prevLevel = if (samePeriod) budget.lastAlertedLevel else 0

                if (currentLevel > prevLevel) {
                    budgetRepository.updateAlertState(budget.id, window.periodKey, currentLevel, now)
                    alerts.add(
                        BudgetAlertEvent(
                            budgetId = budget.id,
                            budgetName = budget.name,
                            periodKey = window.periodKey,
                            level = currentLevel,
                        ),
                    )
                } else if (currentLevel < prevLevel || !samePeriod) {
                    budgetRepository.updateAlertState(budget.id, window.periodKey, currentLevel, now)
                }
            }
        }
        return alerts
    }
}

private fun normalizeBudgetDates(budget: Budget, zoneId: ZoneId): Budget {
    val cleanStart = BudgetPeriodResolver.effectiveRecurringStart(budget.periodType, budget.startDate, zoneId)
    val cleanEnd = if (budget.periodType == BudgetPeriodType.CUSTOM) {
        val startLocal = DateFormatter.toLocalDate(cleanStart, zoneId)
        val endLocal = DateFormatter.toLocalDate(budget.endDateExclusive - 1L, zoneId)
        if (endLocal.isBefore(startLocal)) {
            throw DomainException.InvalidInputException("End date cannot precede start date")
        }
        endLocal.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    } else {
        cleanStart
    }
    return budget.copy(
        name = budget.name.trim(),
        startDate = cleanStart,
        endDateExclusive = cleanEnd,
    )
}
