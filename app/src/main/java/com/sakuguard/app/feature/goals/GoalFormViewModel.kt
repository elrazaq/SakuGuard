package com.sakuguard.app.feature.goals

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.core.formatting.CurrencyInputParser
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.usecase.CreateGoalUseCase
import com.sakuguard.app.domain.usecase.UpdateGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GoalFormUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val targetInput: String = "",
    val deadlineEpochMillis: Long? = null,
    val description: String = "",
    val iconKey: String = GoalVisuals.iconOptions.first().key,
    val colorHex: Long = GoalVisuals.colorPalette.first(),
    val protectionMode: ProtectionMode = ProtectionMode.STANDARD,
    @StringRes val nameErrorRes: Int? = null,
    @StringRes val targetErrorRes: Int? = null,
    @StringRes val submitErrorRes: Int? = null,
    val savedSuccessfully: Boolean = false,
)

@HiltViewModel
class GoalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goalRepository: GoalRepository,
    private val createGoalUseCase: CreateGoalUseCase,
    private val updateGoalUseCase: UpdateGoalUseCase,
) : ViewModel() {

    private val editingGoalId: String? = savedStateHandle["goalId"]
    private var existingGoal: Goal? = null

    private val _uiState = MutableStateFlow(
        GoalFormUiState(
            isEditMode = editingGoalId != null,
            isLoading = editingGoalId != null,
        ),
    )
    val uiState: StateFlow<GoalFormUiState> = _uiState.asStateFlow()

    init {
        if (editingGoalId != null) {
            loadGoal(editingGoalId)
        }
    }

    private fun loadGoal(goalId: String) {
        viewModelScope.launch {
            val goal = goalRepository.getGoalById(goalId)
            existingGoal = goal
            if (goal != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = goal.name,
                        targetInput = goal.targetAmount.toString(),
                        deadlineEpochMillis = goal.deadlineEpochMillis,
                        description = goal.description,
                        iconKey = goal.iconKey ?: GoalVisuals.iconOptions.first().key,
                        colorHex = goal.colorHex ?: GoalVisuals.colorPalette.first(),
                        protectionMode = goal.protectionMode,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, submitErrorRes = R.string.goal_error_generic)
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameErrorRes = null, submitErrorRes = null) }
    }

    fun onTargetChange(value: String) {
        _uiState.update { it.copy(targetInput = value, targetErrorRes = null, submitErrorRes = null) }
    }

    fun onDeadlineSelect(millis: Long?) {
        _uiState.update { it.copy(deadlineEpochMillis = millis) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onIconSelect(iconKey: String) {
        _uiState.update { it.copy(iconKey = iconKey) }
    }

    fun onColorSelect(colorHex: Long) {
        _uiState.update { it.copy(colorHex = colorHex) }
    }

    fun onProtectionModeSelect(mode: ProtectionMode) {
        _uiState.update { it.copy(protectionMode = mode) }
    }

    fun saveGoal() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val nameValid = current.name.isNotBlank()
        val parsedTarget = CurrencyInputParser.parseNonNegativeIdr(current.targetInput)
            ?.takeIf { it > 0L }

        if (!nameValid || parsedTarget == null) {
            _uiState.update {
                it.copy(
                    nameErrorRes = if (!nameValid) R.string.goal_error_name_blank else null,
                    targetErrorRes = if (parsedTarget == null) R.string.goal_error_target_invalid else null,
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, submitErrorRes = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                val base = existingGoal
                if (base != null) {
                    updateGoalUseCase(
                        base.copy(
                            name = current.name.trim(),
                            targetAmount = parsedTarget,
                            deadlineEpochMillis = current.deadlineEpochMillis,
                            description = current.description.trim(),
                            iconKey = current.iconKey,
                            colorHex = current.colorHex,
                            protectionMode = current.protectionMode,
                            updatedAt = now,
                        ),
                    )
                } else {
                    createGoalUseCase(
                        Goal(
                            id = UUID.randomUUID().toString(),
                            name = current.name.trim(),
                            targetAmount = parsedTarget,
                            deadlineEpochMillis = current.deadlineEpochMillis,
                            description = current.description.trim(),
                            iconKey = current.iconKey,
                            colorHex = current.colorHex,
                            priority = 0,
                            status = GoalStatus.ACTIVE,
                            protectionMode = current.protectionMode,
                            createdAt = now,
                            completedAt = null,
                            updatedAt = now,
                        ),
                    )
                }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (_: DomainException.ArchivedEntityException) {
                _uiState.update { it.copy(isSaving = false, submitErrorRes = R.string.goal_error_archived_entity) }
            } catch (_: DomainException.InvalidInputException) {
                _uiState.update { it.copy(isSaving = false, nameErrorRes = R.string.goal_error_name_blank) }
            } catch (_: DomainException.InvalidAmountException) {
                _uiState.update { it.copy(isSaving = false, targetErrorRes = R.string.goal_error_target_invalid) }
            } catch (_: DomainException) {
                _uiState.update { it.copy(isSaving = false, submitErrorRes = R.string.goal_error_generic) }
            }
        }
    }
}
