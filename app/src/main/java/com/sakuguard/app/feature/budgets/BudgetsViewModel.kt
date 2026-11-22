package com.sakuguard.app.feature.budgets

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.BudgetAlertEvent
import com.sakuguard.app.domain.model.BudgetSummary
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.usecase.ArchiveBudgetUseCase
import com.sakuguard.app.domain.usecase.EvaluateBudgetAlertsUseCase
import com.sakuguard.app.domain.usecase.ObserveBudgetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val activeBudgets: List<BudgetSummary> = emptyList(),
    val archivedBudgets: List<BudgetSummary> = emptyList(),
    val showArchived: Boolean = false,
    val thresholdAlerts: List<BudgetAlertEvent> = emptyList(),
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observeBudgetsUseCase: ObserveBudgetsUseCase,
    private val archiveBudgetUseCase: ArchiveBudgetUseCase,
    private val evaluateBudgetAlertsUseCase: EvaluateBudgetAlertsUseCase,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)
    private val thresholdAlerts = MutableStateFlow<List<BudgetAlertEvent>>(emptyList())
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    init {
        evaluateAlerts()
    }

    val uiState: StateFlow<BudgetsUiState> = combine(
        observeBudgetsUseCase(),
        showArchived,
        thresholdAlerts,
        errorMessageRes,
    ) { overview, archivedExpanded, alerts, errorRes ->
        BudgetsUiState(
            isLoading = false,
            activeBudgets = overview.activeBudgets,
            archivedBudgets = overview.archivedBudgets,
            showArchived = archivedExpanded,
            thresholdAlerts = alerts,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsUiState(),
    )

    fun evaluateAlerts() {
        viewModelScope.launch {
            val newAlerts = evaluateBudgetAlertsUseCase()
            if (newAlerts.isNotEmpty()) {
                thresholdAlerts.value = newAlerts
            }
        }
    }

    fun dismissAlerts() {
        thresholdAlerts.value = emptyList()
    }

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    fun restoreBudget(budgetId: String) {
        viewModelScope.launch {
            try {
                archiveBudgetUseCase(budgetId, archived = false, updatedAt = System.currentTimeMillis())
            } catch (_: DomainException.InvalidInputException) {
                errorMessageRes.value = R.string.budget_error_overlap
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.budget_error_generic
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
