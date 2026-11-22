package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import javax.inject.Inject

class CreateWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {
    suspend operator fun invoke(wallet: Wallet) {
        LedgerValidator.requireNonNegativeAmount(wallet.openingBalance, "Opening balance")
        walletRepository.createWallet(wallet)
    }
}

class UpdateWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(updatedWallet: Wallet) {
        LedgerValidator.requireNonNegativeAmount(updatedWallet.openingBalance, "Opening balance")
        transactionRepository.runInTransaction {
            val existing = walletRepository.getWalletById(updatedWallet.id)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: ${updatedWallet.id}")
            val transactions = transactionRepository.getAllTransactions()
            for (tx in transactions) {
                if ((tx.walletId == existing.id || tx.destinationWalletId == existing.id) &&
                    tx.transactionDate < updatedWallet.trackingStartDate
                ) {
                    throw DomainException.PreTrackingDateException(
                        "Existing transaction ${tx.id} precedes new tracking start date",
                    )
                }
            }
            val wallets = walletRepository.getAllWallets().map {
                if (it.id == updatedWallet.id) updatedWallet else it
            }
            val allocations = goalRepository.getAllAllocations()
            LedgerValidator.validateResultingState(wallets, transactions, allocations)
            walletRepository.updateWallet(updatedWallet)
        }
    }
}

class ArchiveWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(walletId: String, archived: Boolean, updatedAt: Long) {
        transactionRepository.runInTransaction {
            walletRepository.getWalletById(walletId)
                ?: throw DomainException.EntityNotFoundException("Wallet not found: $walletId")
            if (archived) {
                val allocations = goalRepository.getAllAllocations()
                val allocated = FinancialEngine.calculateWalletAllocatedFunds(walletId, allocations)
                if (allocated > 0L) {
                    throw DomainException.InsufficientGoalAllocationException(
                        "Cannot archive wallet with active goal allocations ($allocated)",
                    )
                }
            }
            walletRepository.setArchived(walletId, archived, updatedAt)
        }
    }
}
