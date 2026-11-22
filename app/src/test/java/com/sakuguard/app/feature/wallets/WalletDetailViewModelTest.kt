package com.sakuguard.app.feature.wallets

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.ArchiveWalletUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveWalletDetailUseCase
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
class WalletDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `archiving wallet with active allocations surfaces error and leaves wallet active`() = runTest {
        val wallet = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 2_000_000L, 100L, createdAt = 100L, updatedAt = 100L)
        store.createWallet(wallet)
        store.createGoal(Goal("g1", "Trip", 5_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 100L, updatedAt = 100L))
        AllocateToGoalUseCase(store, store, store)("g1", "w1", 500_000L, date = 200L)

        val vm = WalletDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("walletId" to "w1")),
            observeWalletDetailUseCase = ObserveWalletDetailUseCase(store, store, store),
            archiveWalletUseCase = ArchiveWalletUseCase(store, store, store),
        )
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.wallet_error_archive_allocated)
        assertThat(store.getWalletById("w1")?.isArchived).isFalse()
        job.cancel()
    }
}
