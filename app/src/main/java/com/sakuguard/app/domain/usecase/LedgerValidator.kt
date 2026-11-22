package com.sakuguard.app.domain.usecase

import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.TransactionRepository
import java.time.ZoneId

/**
 * Validates that a candidate post-mutation ledger state satisfies all financial invariants.
 */
object LedgerValidator {

    fun isBeforeTrackingDay(
        transactionDate: Long,
        trackingStartDate: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val txDay = DateFormatter.toLocalDate(transactionDate, zoneId)
        val startDay = DateFormatter.toLocalDate(trackingStartDate, zoneId)
        return txDay.isBefore(startDay)
    }

    fun requirePositiveAmount(amount: Long, label: String = "Amount") {
        if (amount <= 0L) {
            throw DomainException.InvalidAmountException("$label must be greater than zero")
        }
    }

    fun requireNonNegativeAmount(amount: Long, label: String = "Amount") {
        if (amount < 0L) {
            throw DomainException.InvalidAmountException("$label cannot be negative")
        }
    }

    /**
     * Validates affected wallets using indexed SQL component sums inside an active transaction,
     * avoiding full-history Kotlin loads while preserving all SafeMath and allocation invariants.
     */
    suspend fun validateAffectedWallets(
        affectedWallets: List<Wallet>,
        transactionRepository: TransactionRepository,
        allocations: List<GoalAllocationEntry>,
        affectedGoalIds: Set<String> = emptySet(),
    ) {
        for (wallet in affectedWallets) {
            val totals = transactionRepository.getWalletLedgerTotals(wallet.id)
            val balance = FinancialEngine.calculateWalletBalanceFromTotals(wallet.openingBalance, totals)
            val allocated = FinancialEngine.calculateWalletAllocatedFunds(wallet.id, allocations)
            val freeMoney = FinancialEngine.calculateWalletFreeMoney(balance, allocated)
            if (freeMoney < 0L) {
                throw DomainException.InsufficientFreeMoneyException(
                    "Wallet '${wallet.name}' would have negative free money ($freeMoney)",
                )
            }
        }

        val walletIds = affectedWallets.map { it.id }.toSet()
        val pairsToCheck = allocations
            .filter { it.walletId in walletIds && (affectedGoalIds.isEmpty() || it.goalId in affectedGoalIds) }
            .map { it.goalId to it.walletId }
            .distinct()

        for ((goalId, walletId) in pairsToCheck) {
            val allocatedInWallet = FinancialEngine.calculateGoalAllocatedInWallet(
                goalId = goalId,
                walletId = walletId,
                allocations = allocations,
            )
            if (allocatedInWallet < 0L) {
                throw DomainException.InsufficientGoalAllocationException(
                    "Goal '$goalId' in wallet '$walletId' would have negative allocation ($allocatedInWallet)",
                )
            }
        }
    }

    fun validateResultingState(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        allocations: List<GoalAllocationEntry>,
    ) {
        for (wallet in wallets) {
            val balance = FinancialEngine.calculateWalletBalance(wallet, transactions)
            val allocated = FinancialEngine.calculateWalletAllocatedFunds(wallet.id, allocations)
            val freeMoney = FinancialEngine.calculateWalletFreeMoney(balance, allocated)
            if (freeMoney < 0L) {
                throw DomainException.InsufficientFreeMoneyException(
                    "Wallet '${wallet.name}' would have negative free money ($freeMoney)",
                )
            }
        }

        val goalWalletPairs = allocations.map { it.goalId to it.walletId }.distinct()
        for ((goalId, walletId) in goalWalletPairs) {
            val allocatedInWallet = FinancialEngine.calculateGoalAllocatedInWallet(
                goalId = goalId,
                walletId = walletId,
                allocations = allocations,
            )
            if (allocatedInWallet < 0L) {
                throw DomainException.InsufficientGoalAllocationException(
                    "Goal '$goalId' in wallet '$walletId' would have negative allocation ($allocatedInWallet)",
                )
            }
        }
    }
}
