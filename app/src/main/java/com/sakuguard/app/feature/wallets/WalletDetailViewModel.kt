package com.sakuguard.app.feature.wallets

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.usecase.ArchiveWalletUseCase
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
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWalletDetailUseCase: ObserveWalletDetailUseCase,
    private val archiveWalletUseCase: ArchiveWalletUseCase,
) : ViewModel() {

    private val walletId: String = checkNotNull(savedStateHandle["walletId"])
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<WalletDetailUiState> = combine(
        observeWalletDetailUseCase(walletId),
        errorMessageRes,
    ) { detail, errorRes ->
        WalletDetailUiState(
            isLoading = false,
            detail = detail,
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

    fun clearError() {
        errorMessageRes.value = null
    }
}
