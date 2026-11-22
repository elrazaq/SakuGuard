package com.sakuguard.app.feature.goals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.GoalSummary
import com.sakuguard.app.domain.usecase.ObserveGoalsPortfolioUseCase
import com.sakuguard.app.domain.usecase.RestoreGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val isLoading: Boolean = true,
    val activeGoals: List<GoalSummary> = emptyList(),
    val completedGoals: List<GoalSummary> = emptyList(),
    val archivedGoals: List<GoalSummary> = emptyList(),
    val overview: FinancialOverview = FinancialOverview(0L, 0L, 0L, 0L),
    val showArchived: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    observeGoalsPortfolioUseCase: ObserveGoalsPortfolioUseCase,
    private val restoreGoalUseCase: RestoreGoalUseCase,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<GoalsUiState> = combine(
        observeGoalsPortfolioUseCase(),
        showArchived,
        errorMessageRes,
    ) { portfolio, archivedExpanded, errorRes ->
        GoalsUiState(
            isLoading = false,
            activeGoals = portfolio.activeGoals,
            completedGoals = portfolio.completedGoals,
            archivedGoals = portfolio.archivedGoals,
            overview = portfolio.overview,
            showArchived = archivedExpanded,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalsUiState(),
    )

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    fun restoreGoal(goalId: String) {
        viewModelScope.launch {
            try {
                restoreGoalUseCase(goalId, System.currentTimeMillis())
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.goal_error_generic
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
