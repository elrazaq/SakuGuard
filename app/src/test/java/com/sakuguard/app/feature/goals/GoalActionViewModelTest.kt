package com.sakuguard.app.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveGoalDetailUseCase
import com.sakuguard.app.domain.usecase.WithdrawFromGoalUseCase
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
class GoalActionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        store.createWallet(Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L))
        store.createGoal(Goal("g1", "Emergency", 5_000_000L, protectionMode = ProtectionMode.STRICT, createdAt = 0L, updatedAt = 0L))
        AllocateToGoalUseCase(store, store, store)("g1", "w1", 2_000_000L, date = 100L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `strict withdrawal requires reason and two-step unlock before executing`() = runTest {
        val vm = GoalActionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            observeGoalDetailUseCase = ObserveGoalDetailUseCase(store, store, store),
            categoryRepository = store,
            allocateToGoalUseCase = AllocateToGoalUseCase(store, store, store),
            withdrawFromGoalUseCase = WithdrawFromGoalUseCase(store, store, store, store),
        )
        advanceUntilIdle()

        vm.onAmountChange("500000")
        vm.unlockStrictOrWithdraw()
        advanceUntilIdle()

        assertThat(vm.uiState.value.reasonErrorRes).isEqualTo(R.string.goal_error_reason_required)
        assertThat(vm.uiState.value.strictUnlocked).isFalse()

        vm.onReasonChange("Medical emergency")
        vm.unlockStrictOrWithdraw() // Step 1: unlock
        advanceUntilIdle()
        assertThat(vm.uiState.value.strictUnlocked).isTrue()
        assertThat(vm.uiState.value.completedSuccessfully).isFalse()

        vm.unlockStrictOrWithdraw() // Step 2: confirm
        advanceUntilIdle()
        assertThat(vm.uiState.value.completedSuccessfully).isTrue()
        assertThat(FinancialEngine.calculateGoalAllocatedAmount("g1", store.getAllAllocations())).isEqualTo(1_500_000L)
    }
}
