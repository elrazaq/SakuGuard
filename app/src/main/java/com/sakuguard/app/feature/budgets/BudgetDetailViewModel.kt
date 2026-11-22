package com.sakuguard.app.feature.budgets

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.BudgetSummary
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.usecase.ArchiveBudgetUseCase
import com.sakuguard.app.domain.usecase.DeleteBudgetUseCase
import com.sakuguard.app.domain.usecase.ObserveBudgetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetDetailUiState(
    val isLoading: Boolean = true,
    val summary: BudgetSummary? = null,
    val deletedSuccessfully: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class BudgetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeBudgetsUseCase: ObserveBudgetsUseCase,
    private val archiveBudgetUseCase: ArchiveBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
) : ViewModel() {

    private val budgetId: String = checkNotNull(savedStateHandle["budgetId"])
    private val deletedSuccessfully = MutableStateFlow(false)
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<BudgetDetailUiState> = combine(
        observeBudgetsUseCase(),
        deletedSuccessfully,
        errorMessageRes,
    ) { overview, deleted, errorRes ->
        val summary = (overview.activeBudgets + overview.archivedBudgets)
            .firstOrNull { it.budget.id == budgetId }
        BudgetDetailUiState(
            isLoading = false,
            summary = summary,
            deletedSuccessfully = deleted,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetDetailUiState(),
    )

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            try {
                archiveBudgetUseCase(budgetId, archived, System.currentTimeMillis())
            } catch (_: DomainException.InvalidInputException) {
                errorMessageRes.value = R.string.budget_error_overlap
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.budget_error_generic
            }
        }
    }

    fun deleteBudget() {
        viewModelScope.launch {
            try {
                deleteBudgetUseCase(budgetId)
                deletedSuccessfully.value = true
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.budget_error_generic
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
