package com.sakuguard.app.feature.wallets

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.usecase.ArchiveWalletUseCase
import com.sakuguard.app.domain.usecase.DeleteWalletUseCase
import com.sakuguard.app.domain.usecase.ObserveWalletDetailUseCase
import com.sakuguard.app.domain.usecase.WalletDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletDetailUiState(
    val isLoading: Boolean = true,
    val detail: WalletDetail? = null,
    val deletedSuccessfully: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWalletDetailUseCase: ObserveWalletDetailUseCase,
    private val archiveWalletUseCase: ArchiveWalletUseCase,
    private val deleteWalletUseCase: DeleteWalletUseCase,
) : ViewModel() {

    private val walletId: String = checkNotNull(savedStateHandle["walletId"])
    private val deletedSuccessfully = MutableStateFlow(false)
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<WalletDetailUiState> = combine(
        observeWalletDetailUseCase(walletId),
        deletedSuccessfully,
        errorMessageRes,
    ) { detail, deleted, errorRes ->
        WalletDetailUiState(
            isLoading = false,
            detail = detail,
            deletedSuccessfully = deleted,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WalletDetailUiState(),
    )

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            try {
                archiveWalletUseCase(
                    walletId = walletId,
                    archived = archived,
                    updatedAt = System.currentTimeMillis(),
                )
            } catch (_: DomainException.InsufficientGoalAllocationException) {
                errorMessageRes.value = R.string.wallet_error_archive_allocated
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.wallet_error_generic
            }
        }
    }

    fun deleteWallet() {
        viewModelScope.launch {
            try {
                deleteWalletUseCase(walletId)
                deletedSuccessfully.value = true
            } catch (_: DomainException.InvalidTransactionMutationException) {
                errorMessageRes.value = R.string.wallet_error_delete_has_history
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.wallet_error_generic
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
