package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class WalletUseCasesTest {

    private lateinit var store: InMemoryLedgerStore
    private lateinit var createWallet: CreateWalletUseCase
    private lateinit var updateWallet: UpdateWalletUseCase
    private lateinit var archiveWallet: ArchiveWalletUseCase
    private lateinit var observePortfolio: ObserveWalletPortfolioUseCase
    private lateinit var observeDetail: ObserveWalletDetailUseCase
    private lateinit var allocateToGoal: AllocateToGoalUseCase

    private val bca = Wallet(
        id = "w-bca",
        name = "BCA",
        type = WalletType.BANK_ACCOUNT,
        openingBalance = 2_000_000L,
        trackingStartDate = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Before
    fun setup() {
        store = InMemoryLedgerStore()
        createWallet = CreateWalletUseCase(store)
        updateWallet = UpdateWalletUseCase(store, store, store)
        archiveWallet = ArchiveWalletUseCase(store, store, store)
        observePortfolio = ObserveWalletPortfolioUseCase(store, store, store)
        observeDetail = ObserveWalletDetailUseCase(store, store, store)
        allocateToGoal = AllocateToGoalUseCase(store, store, store)
    }

    @Test
    fun `createWallet rejects blank name and negative opening balance`() = runTest {
        var nameError: Throwable? = null
        try {
            createWallet(bca.copy(name = "   "))
        } catch (t: Throwable) {
            nameError = t
        }
        assertThat(nameError).isInstanceOf(DomainException.InvalidInputException::class.java)

        var balanceError: Throwable? = null
        try {
            createWallet(bca.copy(openingBalance = -1L))
        } catch (t: Throwable) {
            balanceError = t
        }
        assertThat(balanceError).isInstanceOf(DomainException.InvalidAmountException::class.java)
        assertThat(store.getAllWallets()).isEmpty()
    }

    @Test
    fun `updateWallet rejects opening balance reduction below active allocations and preserves DB`() = runTest {
        createWallet(bca)
        val goal = Goal("g-1", "Emergency", 5_000_000L, protectionMode = ProtectionMode.PROTECTED, createdAt = 1_000L, updatedAt = 1_000L)
        store.createGoal(goal)
        allocateToGoal(goal.id, bca.id, 1_500_000L, date = 2_000L)

        var rejected: Throwable? = null
        try {
            updateWallet(bca.copy(openingBalance = 1_000_000L, updatedAt = 3_000L))
        } catch (t: Throwable) {
            rejected = t
        }
        assertThat(rejected).isInstanceOf(DomainException.InsufficientFreeMoneyException::class.java)
        assertThat(store.getWalletById(bca.id)?.openingBalance).isEqualTo(2_000_000L)
    }

    @Test
    fun `archive and restore preserve history and portfolio separates active vs archived`() = runTest {
        val cash = bca.copy(id = "w-cash", name = "Cash", type = WalletType.CASH, openingBalance = 500_000L)
        createWallet(bca)
        createWallet(cash)

        archiveWallet(cash.id, archived = true, updatedAt = 2_000L)
        val afterArchive = observePortfolio().first()
        assertThat(afterArchive.activeWallets.map { it.wallet.id }).containsExactly(bca.id)
        assertThat(afterArchive.archivedWallets.map { it.wallet.id }).containsExactly(cash.id)
        assertThat(afterArchive.overview.totalAssets).isEqualTo(2_500_000L)

        archiveWallet(cash.id, archived = false, updatedAt = 3_000L)
        val afterRestore = observePortfolio().first()
        assertThat(afterRestore.activeWallets).hasSize(2)
        assertThat(afterRestore.archivedWallets).isEmpty()
    }

    @Test
    fun `observeDetail reports wallet balance, free money, allocated money, and goal breakdown`() = runTest {
        createWallet(bca)
        val goal = Goal("g-1", "Laptop", 5_000_000L, protectionMode = ProtectionMode.STANDARD, createdAt = 1_000L, updatedAt = 1_000L)
        store.createGoal(goal)
        allocateToGoal(goal.id, bca.id, 750_000L, date = 2_000L)

        val detail = observeDetail(bca.id).first()
        assertThat(detail).isNotNull()
        assertThat(detail!!.summary.currentBalance).isEqualTo(2_000_000L)
        assertThat(detail.summary.allocatedFunds).isEqualTo(750_000L)
        assertThat(detail.summary.freeMoney).isEqualTo(1_250_000L)
        assertThat(detail.goalAllocations).hasSize(1)
        assertThat(detail.goalAllocations.first().allocatedAmount).isEqualTo(750_000L)
    }
}
