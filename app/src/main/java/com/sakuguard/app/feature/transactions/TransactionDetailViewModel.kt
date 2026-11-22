package com.sakuguard.app.feature.transactions

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.usecase.DeleteTransactionUseCase
import com.sakuguard.app.domain.usecase.ObserveTransactionsFeedUseCase
import com.sakuguard.app.domain.usecase.TransactionFeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val item: TransactionFeedItem? = null,
    val canEditOrDelete: Boolean = false,
    val deletedSuccessfully: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTransactionsFeedUseCase: ObserveTransactionsFeedUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
) : ViewModel() {

    private val transactionId: String = checkNotNull(savedStateHandle["transactionId"])
    private val deletedSuccessfully = MutableStateFlow(false)
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        observeTransactionsFeedUseCase(),
        deletedSuccessfully,
        errorMessageRes,
    ) { feed, deleted, errorRes ->
        val item = feed.items.firstOrNull { it.transaction.id == transactionId }
        val editable = item != null &&
            item.transaction.type != TransactionType.GOAL_ALLOCATION &&
            item.transaction.type != TransactionType.GOAL_WITHDRAWAL &&
            item.transaction.goalId == null
        TransactionDetailUiState(
            isLoading = false,
            item = item,
            canEditOrDelete = editable,
            deletedSuccessfully = deleted,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionDetailUiState(),
    )

    fun deleteTransaction() {
        viewModelScope.launch {
            try {
                deleteTransactionUseCase(transactionId)
                deletedSuccessfully.value = true
            } catch (e: DomainException) {
                errorMessageRes.value = when (e) {
                    is DomainException.ArchivedEntityException -> R.string.tx_error_archived_wallet
                    is DomainException.InsufficientFreeMoneyException -> R.string.tx_error_insufficient_free
                    is DomainException.InvalidTransactionMutationException -> R.string.tx_error_goal_ledger_locked
                    else -> R.string.tx_error_generic
                }
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
