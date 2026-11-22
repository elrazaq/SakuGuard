package com.sakuguard.app.feature.goals

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.core.formatting.CurrencyInputParser
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.model.WithdrawalTarget
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.GoalWalletShare
import com.sakuguard.app.domain.usecase.ObserveGoalDetailUseCase
import com.sakuguard.app.domain.usecase.WithdrawFromGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalActionUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val goal: Goal? = null,
    val totalGoalAllocated: Long = 0L,
    val activeWallets: List<WalletSummary> = emptyList(),
    val walletShares: List<GoalWalletShare> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val selectedWalletId: String? = null,
    val amountInput: String = "",
    val dateMillis: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    val withdrawalTarget: WithdrawalTarget = WithdrawalTarget.RETURN_TO_FREE_MONEY,
    val selectedCategoryId: String? = null,
    val reason: String = "",
    val strictUnlocked: Boolean = false,
    @StringRes val amountErrorRes: Int? = null,
    @StringRes val reasonErrorRes: Int? = null,
    @StringRes val submitErrorRes: Int? = null,
    val completedSuccessfully: Boolean = false,
)

@HiltViewModel
class GoalActionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeGoalDetailUseCase: ObserveGoalDetailUseCase,
    private val categoryRepository: CategoryRepository,
    private val allocateToGoalUseCase: AllocateToGoalUseCase,
    private val withdrawFromGoalUseCase: WithdrawFromGoalUseCase,
) : ViewModel() {

    private val goalId: String = checkNotNull(savedStateHandle["goalId"])

    private val _uiState = MutableStateFlow(GoalActionUiState())
    val uiState: StateFlow<GoalActionUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val detail = observeGoalDetailUseCase(goalId).first()
            val expenseCats = categoryRepository.getAllCategories()
                .filter { !it.isArchived && it.type == CategoryType.EXPENSE }
            if (detail == null) {
                _uiState.update { it.copy(isLoading = false, submitErrorRes = R.string.goal_error_generic) }
                return@launch
            }
            val defaultAllocWalletId = detail.activeWallets.firstOrNull { it.freeMoney > 0L }?.wallet?.id
                ?: detail.activeWallets.firstOrNull()?.wallet?.id
            val defaultWithdrawWalletId = detail.walletShares.firstOrNull { !it.wallet.isArchived }?.wallet?.id
            _uiState.update {
                it.copy(
                    isLoading = false,
                    goal = detail.summary.goal,
                    totalGoalAllocated = detail.summary.allocatedAmount,
                    activeWallets = detail.activeWallets,
                    walletShares = detail.walletShares.filter { share -> !share.wallet.isArchived },
                    expenseCategories = expenseCats,
                    selectedWalletId = defaultWithdrawWalletId ?: defaultAllocWalletId,
                    selectedCategoryId = expenseCats.firstOrNull()?.id,
                )
            }
        }
    }

    fun initForAllocation() {
        val current = _uiState.value
        val valid = current.activeWallets.any { it.wallet.id == current.selectedWalletId }
        if (!valid) {
            _uiState.update {
                it.copy(
                    selectedWalletId = current.activeWallets.firstOrNull { w -> w.freeMoney > 0L }?.wallet?.id
                        ?: current.activeWallets.firstOrNull()?.wallet?.id,
                )
            }
        }
    }

    fun onWalletSelect(walletId: String) {
        _uiState.update {
            it.copy(selectedWalletId = walletId, amountErrorRes = null, submitErrorRes = null, strictUnlocked = false)
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update {
            it.copy(amountInput = value, amountErrorRes = null, submitErrorRes = null, strictUnlocked = false)
        }
    }

    fun onDateSelect(millis: Long) {
        _uiState.update { it.copy(dateMillis = millis, submitErrorRes = null) }
    }

    fun onWithdrawalTargetSelect(target: WithdrawalTarget) {
        _uiState.update { it.copy(withdrawalTarget = target, submitErrorRes = null, strictUnlocked = false) }
    }

    fun onCategorySelect(categoryId: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, submitErrorRes = null) }
    }

    fun onReasonChange(value: String) {
        _uiState.update { it.copy(reason = value, reasonErrorRes = null, submitErrorRes = null) }
    }

    fun submitAllocation() {
        val current = _uiState.value
        if (current.isSubmitting || current.isLoading) return

        val parsedAmount = CurrencyInputParser.parseNonNegativeIdr(current.amountInput)
            ?.takeIf { it > 0L }
        val walletId = current.selectedWalletId

        if (parsedAmount == null || walletId == null) {
            _uiState.update {
                it.copy(
                    amountErrorRes = if (parsedAmount == null) R.string.tx_error_amount else null,
                    submitErrorRes = if (walletId == null) R.string.tx_error_wallet_required else null,
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, submitErrorRes = null) }
        viewModelScope.launch {
            try {
                allocateToGoalUseCase(
                    goalId = goalId,
                    walletId = walletId,
                    amount = parsedAmount,
                    date = current.dateMillis,
                    now = System.currentTimeMillis(),
                )
                _uiState.update { it.copy(isSubmitting = false, completedSuccessfully = true) }
            } catch (e: DomainException) {
                _uiState.update { it.copy(isSubmitting = false, submitErrorRes = mapError(e)) }
            }
        }
    }

    fun unlockStrictOrWithdraw() {
        val current = _uiState.value
        if (current.isSubmitting || current.isLoading) return

        val goal = current.goal ?: return
        val parsedAmount = CurrencyInputParser.parseNonNegativeIdr(current.amountInput)
            ?.takeIf { it > 0L }
        val walletId = current.selectedWalletId
        val reasonRequired = goal.protectionMode != ProtectionMode.STANDARD
        val reasonValid = !reasonRequired || current.reason.isNotBlank()

        if (parsedAmount == null || walletId == null || !reasonValid) {
            _uiState.update {
                it.copy(
                    amountErrorRes = if (parsedAmount == null) R.string.tx_error_amount else null,
                    reasonErrorRes = if (!reasonValid) R.string.goal_error_reason_required else null,
                    submitErrorRes = if (walletId == null) R.string.tx_error_wallet_required else null,
                )
            }
            return
        }

        if (goal.protectionMode == ProtectionMode.STRICT && !current.strictUnlocked) {
            _uiState.update { it.copy(strictUnlocked = true, submitErrorRes = null) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, submitErrorRes = null) }
        viewModelScope.launch {
            try {
                withdrawFromGoalUseCase(
                    goalId = goalId,
                    walletId = walletId,
                    amount = parsedAmount,
                    target = current.withdrawalTarget,
                    date = current.dateMillis,
                    reason = current.reason.trim().takeIf { it.isNotEmpty() },
                    expenseCategoryId = if (current.withdrawalTarget == WithdrawalTarget.DIRECT_EXPENSE) {
                        current.selectedCategoryId
                    } else {
                        null
                    },
                    now = System.currentTimeMillis(),
                )
                _uiState.update { it.copy(isSubmitting = false, completedSuccessfully = true) }
            } catch (e: DomainException) {
                _uiState.update { it.copy(isSubmitting = false, submitErrorRes = mapError(e)) }
            }
        }
    }

    @StringRes
    private fun mapError(e: DomainException): Int = when (e) {
        is DomainException.InsufficientFreeMoneyException -> R.string.goal_error_insufficient_free
        is DomainException.InsufficientGoalAllocationException -> R.string.goal_error_insufficient_goal_wallet
        is DomainException.ArchivedEntityException -> R.string.goal_error_archived_entity
        is DomainException.PreTrackingDateException -> R.string.tx_error_pre_tracking_date
        is DomainException.InvalidInputException -> R.string.goal_error_reason_required
        is DomainException.InvalidTransactionMutationException -> R.string.goal_error_completed_allocate
        is DomainException.ArithmeticOverflowException -> R.string.tx_error_overflow
        else -> R.string.goal_error_generic
    }
}
