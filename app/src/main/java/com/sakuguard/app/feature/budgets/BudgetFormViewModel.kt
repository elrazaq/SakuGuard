package com.sakuguard.app.feature.budgets

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.core.formatting.CurrencyInputParser
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.repository.BudgetRepository
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.usecase.CreateBudgetUseCase
import com.sakuguard.app.domain.usecase.UpdateBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class BudgetFormUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val name: String = "",
    val selectedCategoryId: String? = null,
    val expenseCategories: List<Category> = emptyList(),
    val limitInput: String = "",
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val startDate: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    val endDateInclusive: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    val alert80Enabled: Boolean = true,
    val alert100Enabled: Boolean = true,
    @StringRes val nameErrorRes: Int? = null,
    @StringRes val limitErrorRes: Int? = null,
    @StringRes val categoryErrorRes: Int? = null,
    @StringRes val submitErrorRes: Int? = null,
    val savedSuccessfully: Boolean = false,
)

@HiltViewModel
class BudgetFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val updateBudgetUseCase: UpdateBudgetUseCase,
) : ViewModel() {

    private val editingBudgetId: String? = savedStateHandle["budgetId"]
    private var existingBudget: Budget? = null

    private val _uiState = MutableStateFlow(BudgetFormUiState(isEditMode = editingBudgetId != null))
    val uiState: StateFlow<BudgetFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val expenseCategories = categoryRepository.getAllCategories()
                .filter { !it.isArchived && it.type == CategoryType.EXPENSE }
            val existing = editingBudgetId?.let { budgetRepository.getBudgetById(it) }
            existingBudget = existing

            if (editingBudgetId != null && existing == null) {
                _uiState.update { it.copy(isLoading = false, submitErrorRes = R.string.budget_error_generic) }
                return@launch
            }

            val zone = ZoneId.systemDefault()
            val endInc = if (existing != null && existing.periodType == BudgetPeriodType.CUSTOM) {
                DateFormatter.toLocalDate(existing.endDateExclusive - 1L, zone)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                LocalDate.now(zone).plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    expenseCategories = expenseCategories,
                    name = existing?.name ?: "",
                    selectedCategoryId = existing?.categoryId ?: expenseCategories.firstOrNull()?.id,
                    limitInput = existing?.limitAmount?.toString() ?: "",
                    periodType = existing?.periodType ?: BudgetPeriodType.MONTHLY,
                    startDate = existing?.startDate ?: DateFormatter.startOfDayMillis(System.currentTimeMillis()),
                    endDateInclusive = endInc,
                    alert80Enabled = existing?.alert80Enabled ?: true,
                    alert100Enabled = existing?.alert100Enabled ?: true,
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameErrorRes = null, submitErrorRes = null) }
    }

    fun onCategorySelect(categoryId: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, categoryErrorRes = null, submitErrorRes = null) }
    }

    fun onLimitChange(value: String) {
        _uiState.update { it.copy(limitInput = value, limitErrorRes = null, submitErrorRes = null) }
    }

    fun onPeriodTypeSelect(type: BudgetPeriodType) {
        _uiState.update { it.copy(periodType = type, submitErrorRes = null) }
    }

    fun onStartDateSelect(millis: Long) {
        _uiState.update { it.copy(startDate = millis, submitErrorRes = null) }
    }

    fun onEndDateSelect(millis: Long) {
        _uiState.update { it.copy(endDateInclusive = millis, submitErrorRes = null) }
    }

    fun onAlert80Toggle(enabled: Boolean) {
        _uiState.update { it.copy(alert80Enabled = enabled) }
    }

    fun onAlert100Toggle(enabled: Boolean) {
        _uiState.update { it.copy(alert100Enabled = enabled) }
    }

    fun saveBudget() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val nameValid = current.name.isNotBlank()
        val parsedLimit = CurrencyInputParser.parseNonNegativeIdr(current.limitInput)?.takeIf { it > 0L }
        val categoryId = current.selectedCategoryId

        if (!nameValid || parsedLimit == null || categoryId == null) {
            _uiState.update {
                it.copy(
                    nameErrorRes = if (!nameValid) R.string.budget_error_name_blank else null,
                    limitErrorRes = if (parsedLimit == null) R.string.budget_error_limit_invalid else null,
                    categoryErrorRes = if (categoryId == null) R.string.budget_error_category_required else null,
                )
            }
            return
        }

        val zone = ZoneId.systemDefault()
        val endExclusive = if (current.periodType == BudgetPeriodType.CUSTOM) {
            DateFormatter.toLocalDate(current.endDateInclusive, zone)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            current.startDate
        }

        _uiState.update { it.copy(isSaving = true, submitErrorRes = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                val base = existingBudget
                if (base != null) {
                    updateBudgetUseCase(
                        base.copy(
                            name = current.name.trim(),
                            categoryId = categoryId,
                            limitAmount = parsedLimit,
                            periodType = current.periodType,
                            startDate = current.startDate,
                            endDateExclusive = endExclusive,
                            alert80Enabled = current.alert80Enabled,
                            alert100Enabled = current.alert100Enabled,
                            updatedAt = now,
                        ),
                    )
                } else {
                    createBudgetUseCase(
                        Budget(
                            id = UUID.randomUUID().toString(),
                            name = current.name.trim(),
                            categoryId = categoryId,
                            limitAmount = parsedLimit,
                            periodType = current.periodType,
                            startDate = current.startDate,
                            endDateExclusive = endExclusive,
                            alert80Enabled = current.alert80Enabled,
                            alert100Enabled = current.alert100Enabled,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: DomainException) {
                val errorRes = when (e) {
                    is DomainException.InvalidInputException -> {
                        if (e.message?.contains("End date", ignoreCase = true) == true) {
                            R.string.budget_error_date_range
                        } else {
                            R.string.budget_error_overlap
                        }
                    }
                    is DomainException.InvalidAmountException -> R.string.budget_error_limit_invalid
                    else -> R.string.budget_error_generic
                }
                _uiState.update { it.copy(isSaving = false, submitErrorRes = errorRes) }
            }
        }
    }
}
