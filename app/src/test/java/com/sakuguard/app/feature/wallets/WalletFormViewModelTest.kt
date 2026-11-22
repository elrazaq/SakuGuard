package com.sakuguard.app.feature.wallets

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.CreateWalletUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.UpdateWalletUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletFormViewModelTest {

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
    fun `create mode validates blank name and invalid opening balance`() = runTest {
        val vm = WalletFormViewModel(
            savedStateHandle = SavedStateHandle(),
            walletRepository = store,
            createWalletUseCase = CreateWalletUseCase(store),
            updateWalletUseCase = UpdateWalletUseCase(store, store, store),
        )
        vm.onNameChange("   ")
        vm.onOpeningBalanceChange("12.50")
        vm.saveWallet()
        advanceUntilIdle()

        assertThat(vm.uiState.value.nameErrorRes).isEqualTo(R.string.wallet_error_name_blank)
        assertThat(vm.uiState.value.balanceErrorRes).isEqualTo(R.string.wallet_error_invalid_amount)
        assertThat(vm.uiState.value.savedSuccessfully).isFalse()
        assertThat(store.getAllWallets()).isEmpty()
    }

    @Test
    fun `edit mode rejects reducing opening balance below goal allocations`() = runTest {
        val wallet = Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 2_000_000L, 100L, createdAt = 100L, updatedAt = 100L)
        store.createWallet(wallet)
        store.createGoal(Goal("g1", "Trip", 5_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 100L, updatedAt = 100L))
        AllocateToGoalUseCase(store, store, store)("g1", "w1", 1_500_000L, date = 200L)

        val vm = WalletFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("walletId" to "w1")),
            walletRepository = store,
            createWalletUseCase = CreateWalletUseCase(store),
            updateWalletUseCase = UpdateWalletUseCase(store, store, store),
        )
        advanceUntilIdle()

        vm.onOpeningBalanceChange("1000000")
        vm.saveWallet()
        advanceUntilIdle()

        assertThat(vm.uiState.value.submitErrorRes).isEqualTo(R.string.wallet_error_insufficient_free)
        assertThat(vm.uiState.value.savedSuccessfully).isFalse()
        assertThat(store.getWalletById("w1")?.openingBalance).isEqualTo(2_000_000L)
    }
}
