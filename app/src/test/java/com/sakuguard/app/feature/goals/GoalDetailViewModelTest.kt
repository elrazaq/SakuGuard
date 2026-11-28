package com.sakuguard.app.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.usecase.AllocateToGoalUseCase
import com.sakuguard.app.domain.usecase.ArchiveGoalUseCase
import com.sakuguard.app.domain.usecase.DeleteGoalUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.ObserveGoalDetailUseCase
import com.sakuguard.app.domain.usecase.RestoreGoalUseCase
import com.sakuguard.app.domain.usecase.UpdateGoalStatusUseCase
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
class GoalDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryLedgerStore

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        store = InMemoryLedgerStore()
        store.createWallet(Wallet("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, createdAt = 0L, updatedAt = 0L))
        store.createGoal(Goal("g1", "Laptop", 2_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 0L, updatedAt = 0L))
        AllocateToGoalUseCase(store, store, store)("g1", "w1", 500_000L, date = 100L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `archiving goal with allocated funds surfaces error`() = runTest {
        val vm = GoalDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            observeGoalDetailUseCase = ObserveGoalDetailUseCase(store, store, store, testDispatcher),
            archiveGoalUseCase = ArchiveGoalUseCase(store, store),
            restoreGoalUseCase = RestoreGoalUseCase(store),
            updateGoalStatusUseCase = UpdateGoalStatusUseCase(store),
            deleteGoalUseCase = DeleteGoalUseCase(store, store),
        )
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.archiveGoal()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.goal_error_archive_allocated)
        job.cancel()
    }

    @Test
    fun `deleting goal with allocation history is rejected with error`() = runTest {
        val vm = GoalDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g1")),
            observeGoalDetailUseCase = ObserveGoalDetailUseCase(store, store, store, testDispatcher),
            archiveGoalUseCase = ArchiveGoalUseCase(store, store),
            restoreGoalUseCase = RestoreGoalUseCase(store),
            updateGoalStatusUseCase = UpdateGoalStatusUseCase(store),
            deleteGoalUseCase = DeleteGoalUseCase(store, store),
        )
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.deleteGoal()
        advanceUntilIdle()

        assertThat(vm.uiState.value.deletedSuccessfully).isFalse()
        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.goal_error_delete_has_history)
        assertThat(store.getGoalById("g1")).isNotNull()
        job.cancel()
    }

    @Test
    fun `deleting empty goal succeeds and marks deletedSuccessfully`() = runTest {
        store.createGoal(Goal("g_empty", "Emergency", 1_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 0L, updatedAt = 0L))
        val vm = GoalDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to "g_empty")),
            observeGoalDetailUseCase = ObserveGoalDetailUseCase(store, store, store, testDispatcher),
            archiveGoalUseCase = ArchiveGoalUseCase(store, store),
            restoreGoalUseCase = RestoreGoalUseCase(store),
            updateGoalStatusUseCase = UpdateGoalStatusUseCase(store),
            deleteGoalUseCase = DeleteGoalUseCase(store, store),
        )
        val job = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.deleteGoal()
        advanceUntilIdle()

        assertThat(vm.uiState.value.deletedSuccessfully).isTrue()
        assertThat(store.getGoalById("g_empty")).isNull()
        job.cancel()
    }
}
