package com.sakuguard.app.feature.wallets

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.ArchiveWalletUseCase
import com.sakuguard.app.domain.usecase.CreateWalletUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveWalletPortfolioUseCase
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
class WalletsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore
    private lateinit var viewModel: WalletsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        viewModel = WalletsViewModel(
            observeWalletPortfolioUseCase = ObserveWalletPortfolioUseCase(store, store, store),
            archiveWalletUseCase = ArchiveWalletUseCase(store, store, store),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty database emits empty active and archived lists with zero totals`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.activeWallets).isEmpty()
        assertThat(state.archivedWallets).isEmpty()
        assertThat(state.overview.totalAssets).isEqualTo(0L)
        job.cancel()
    }

    @Test
    fun `restoreWallet moves archived wallet back to activeWallets`() = runTest {
        val createWallet = CreateWalletUseCase(store)
        createWallet(
            Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 1_000_000L, 100L, isArchived = true, createdAt = 100L, updatedAt = 100L),
        )
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.archivedWallets).hasSize(1)

        viewModel.toggleShowArchived()
        viewModel.restoreWallet("w1")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showArchived).isTrue()
        assertThat(viewModel.uiState.value.activeWallets).hasSize(1)
        assertThat(viewModel.uiState.value.archivedWallets).isEmpty()
        job.cancel()
    }
}
