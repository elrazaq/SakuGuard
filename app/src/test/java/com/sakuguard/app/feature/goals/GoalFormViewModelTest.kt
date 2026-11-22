package com.sakuguard.app.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.usecase.CreateGoalUseCase
import com.sakuguard.app.domain.usecase.InMemoryLedgerStore
import com.sakuguard.app.domain.usecase.UpdateGoalUseCase
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
class GoalFormViewModelTest {

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
    fun `validates blank name and zero target amount`() = runTest {
        val vm = GoalFormViewModel(
            savedStateHandle = SavedStateHandle(),
            goalRepository = store,
            createGoalUseCase = CreateGoalUseCase(store),
            updateGoalUseCase = UpdateGoalUseCase(store, store),
        )
        vm.onNameChange("  ")
        vm.onTargetChange("0")
        vm.saveGoal()
        advanceUntilIdle()

        assertThat(vm.uiState.value.nameErrorRes).isEqualTo(R.string.goal_error_name_blank)
        assertThat(vm.uiState.value.targetErrorRes).isEqualTo(R.string.goal_error_target_invalid)
        assertThat(store.getAllGoals()).isEmpty()
    }

    @Test
    fun `creates goal with chosen protection mode`() = runTest {
        val vm = GoalFormViewModel(
            savedStateHandle = SavedStateHandle(),
            goalRepository = store,
            createGoalUseCase = CreateGoalUseCase(store),
            updateGoalUseCase = UpdateGoalUseCase(store, store),
        )
        vm.onNameChange("Emergency")
        vm.onTargetChange("10000000")
        vm.onProtectionModeSelect(ProtectionMode.STRICT)
        vm.saveGoal()
        advanceUntilIdle()

        assertThat(vm.uiState.value.savedSuccessfully).isTrue()
        assertThat(store.getAllGoals().single().protectionMode).isEqualTo(ProtectionMode.STRICT)
    }
}
