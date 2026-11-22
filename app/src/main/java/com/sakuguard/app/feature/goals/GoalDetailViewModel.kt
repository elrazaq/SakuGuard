package com.sakuguard.app.feature.goals

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.usecase.ArchiveGoalUseCase
import com.sakuguard.app.domain.usecase.GoalDetail
import com.sakuguard.app.domain.usecase.ObserveGoalDetailUseCase
import com.sakuguard.app.domain.usecase.RestoreGoalUseCase
import com.sakuguard.app.domain.usecase.UpdateGoalStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalDetailUiState(
    val isLoading: Boolean = true,
    val detail: GoalDetail? = null,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeGoalDetailUseCase: ObserveGoalDetailUseCase,
    private val archiveGoalUseCase: ArchiveGoalUseCase,
    private val restoreGoalUseCase: RestoreGoalUseCase,
    private val updateGoalStatusUseCase: UpdateGoalStatusUseCase,
) : ViewModel() {

    private val goalId: String = checkNotNull(savedStateHandle["goalId"])
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<GoalDetailUiState> = combine(
        observeGoalDetailUseCase(goalId),
        errorMessageRes,
    ) { detail, errorRes ->
        GoalDetailUiState(
            isLoading = false,
            detail = detail,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalDetailUiState(),
    )

    fun archiveGoal() {
        viewModelScope.launch {
            try {
                archiveGoalUseCase(goalId, System.currentTimeMillis())
            } catch (_: DomainException.InsufficientGoalAllocationException) {
                errorMessageRes.value = R.string.goal_error_archive_allocated
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.goal_error_generic
            }
        }
    }

    fun restoreGoal() {
        viewModelScope.launch {
            try {
                restoreGoalUseCase(goalId, System.currentTimeMillis())
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.goal_error_generic
            }
        }
    }

    fun markCompleted() {
        viewModelScope.launch {
            try {
                updateGoalStatusUseCase.markCompleted(goalId, System.currentTimeMillis())
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.goal_error_generic
            }
        }
    }

    fun reopenGoal() {
        viewModelScope.launch {
            try {
                updateGoalStatusUseCase.reopen(goalId, System.currentTimeMillis())
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.goal_error_generic
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
