package com.sakuguard.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.domain.usecase.DashboardSnapshot
import com.sakuguard.app.domain.usecase.ObserveDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val amountsHidden: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeDashboardUseCase: ObserveDashboardUseCase,
) : ViewModel() {

    private val amountsHidden = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        observeDashboardUseCase(),
        amountsHidden,
    ) { snapshot, hidden ->
        DashboardUiState(
            isLoading = false,
            snapshot = snapshot,
            amountsHidden = hidden,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun togglePrivacy() {
        amountsHidden.value = !amountsHidden.value
    }
}
