package com.sakuguard.app.feature.goals

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveGoalsPortfolioUseCase
import com.sakuguard.app.domain.usecase.RestoreGoalUseCase
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
class GoalsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore
    private lateinit var viewModel: GoalsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        viewModel = GoalsViewModel(
            observeGoalsPortfolioUseCase = ObserveGoalsPortfolioUseCase(store, store, store),
            restoreGoalUseCase = RestoreGoalUseCase(store),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty database shows empty goal lists and zero totals`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.activeGoals).isEmpty()
        assertThat(viewModel.uiState.value.overview.allocatedFunds).isEqualTo(0L)
        job.cancel()
    }

    @Test
    fun `portfolio separates active, completed, archived and restores archived goal`() = runTest {
        store.createWallet(Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L))
        store.createGoal(Goal("g1", "Active", 2_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 0L, updatedAt = 0L))
        store.createGoal(Goal("g2", "Done", 1_000_000L, status = GoalStatus.COMPLETED, createdAt = 0L, completedAt = 100L, updatedAt = 100L))
        store.createGoal(Goal("g3", "Old", 1_000_000L, status = GoalStatus.ARCHIVED, createdAt = 0L, updatedAt = 100L))
        AllocateToGoalUseCase(store, store, store)("g1", "w1", 500_000L, date = 100L)

        val job = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeGoals).hasSize(1)
        assertThat(viewModel.uiState.value.completedGoals).hasSize(1)
        assertThat(viewModel.uiState.value.archivedGoals).hasSize(1)
        assertThat(viewModel.uiState.value.overview.protectedFunds).isEqualTo(500_000L)

        viewModel.restoreGoal("g3")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeGoals).hasSize(2)
        assertThat(viewModel.uiState.value.archivedGoals).isEmpty()
        job.cancel()
    }
}
