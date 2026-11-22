package com.sakuguard.app.feature.dashboard

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveDashboardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        viewModel = DashboardViewModel(
            observeDashboardUseCase = ObserveDashboardUseCase(store, store, store, store),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reacts to wallet creation and toggles privacy without mutating snapshot`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.snapshot.hasAnyData).isFalse()

        store.createWallet(Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 2_500_000L, 0L, createdAt = 0L, updatedAt = 0L))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.snapshot.hasAnyData).isTrue()
        assertThat(viewModel.uiState.value.snapshot.overview.totalAssets).isEqualTo(2_500_000L)
        assertThat(viewModel.uiState.value.amountsHidden).isFalse()

        viewModel.togglePrivacy()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.amountsHidden).isTrue()
        assertThat(viewModel.uiState.value.snapshot.overview.totalAssets).isEqualTo(2_500_000L)
        job.cancel()
    }
}
