package com.sakuguard.app.feature.wallets

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.usecase.ArchiveWalletUseCase
import com.sakuguard.app.domain.usecase.ObserveWalletPortfolioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletsUiState(
    val isLoading: Boolean = true,
    val activeWallets: List<WalletSummary> = emptyList(),
    val archivedWallets: List<WalletSummary> = emptyList(),
    val overview: FinancialOverview = FinancialOverview(0L, 0L, 0L, 0L),
    val showArchived: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class WalletsViewModel @Inject constructor(
    observeWalletPortfolioUseCase: ObserveWalletPortfolioUseCase,
    private val archiveWalletUseCase: ArchiveWalletUseCase,
) : ViewModel() {

    private val showArchived = MutableStateFlow(false)
    private val errorMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<WalletsUiState> = combine(
        observeWalletPortfolioUseCase(),
        showArchived,
        errorMessageRes,
    ) { portfolio, archivedExpanded, errorRes ->
        WalletsUiState(
            isLoading = false,
            activeWallets = portfolio.activeWallets,
            archivedWallets = portfolio.archivedWallets,
            overview = portfolio.overview,
            showArchived = archivedExpanded,
            errorMessageRes = errorRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WalletsUiState(),
    )

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    fun restoreWallet(walletId: String) {
        viewModelScope.launch {
            try {
                archiveWalletUseCase(
                    walletId = walletId,
                    archived = false,
                    updatedAt = System.currentTimeMillis(),
                )
            } catch (_: DomainException) {
                errorMessageRes.value = R.string.wallet_error_generic
            }
        }
    }

    fun clearError() {
        errorMessageRes.value = null
    }
}
