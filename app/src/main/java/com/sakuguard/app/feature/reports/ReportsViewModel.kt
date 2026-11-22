package com.sakuguard.app.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.ReportPeriodPreset
import com.sakuguard.app.domain.model.ReportsSnapshot
import com.sakuguard.app.domain.usecase.ObserveReportsUseCase
import com.sakuguard.app.domain.usecase.ReportPeriodResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReportsUiState(
    val isLoading: Boolean = true,
    val selectedPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    val customStartMillis: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    val customEndInclusiveMillis: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    val amountsHidden: Boolean = false,
    val snapshot: ReportsSnapshot? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val observeReportsUseCase: ObserveReportsUseCase,
) : ViewModel() {

    private data class FilterParams(
        val preset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
        val customStartMillis: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
        val customEndInclusiveMillis: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    )

    private val filterParams = MutableStateFlow(FilterParams())
    private val amountsHidden = MutableStateFlow(false)

    val uiState: StateFlow<ReportsUiState> = filterParams
        .flatMapLatest { params ->
            val range = ReportPeriodResolver.resolve(
                preset = params.preset,
                customStartMillis = params.customStartMillis,
                customEndInclusiveMillis = params.customEndInclusiveMillis,
            )
            combine(
                observeReportsUseCase(range),
                amountsHidden,
            ) { snapshot, hidden ->
                ReportsUiState(
                    isLoading = false,
                    selectedPreset = params.preset,
                    customStartMillis = params.customStartMillis,
                    customEndInclusiveMillis = params.customEndInclusiveMillis,
                    amountsHidden = hidden,
                    snapshot = snapshot,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState(),
        )

    fun onPresetSelect(preset: ReportPeriodPreset) {
        filterParams.value = filterParams.value.copy(preset = preset)
    }

    fun onCustomDateRangeSelect(startMillis: Long, endInclusiveMillis: Long) {
        val (s, e) = if (endInclusiveMillis < startMillis) {
            endInclusiveMillis to startMillis
        } else {
            startMillis to endInclusiveMillis
        }
        filterParams.value = FilterParams(
            preset = ReportPeriodPreset.CUSTOM,
            customStartMillis = s,
            customEndInclusiveMillis = e,
        )
    }

    fun togglePrivacy() {
        amountsHidden.value = !amountsHidden.value
    }
}
