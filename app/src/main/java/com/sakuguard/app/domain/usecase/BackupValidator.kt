package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.engine.SafeMath
import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupPayload
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WithdrawalTarget
import com.sakuguard.app.domain.model.toDomain

object BackupValidator {

    fun validate(payload: BackupPayload) {
        try {
            validateUniqueIds(payload)

            val wallets = payload.wallets.map { it.toDomain() }
            val categories = payload.categories.map { it.toDomain() }
            val goals = payload.goals.map { it.toDomain() }
            val transactions = payload.transactions.map { it.toDomain() }
            val allocations = payload.goalAllocations.map { it.toDomain() }
            val budgets = payload.budgets.map { it.toDomain() }

            val walletMap = wallets.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }
            val goalMap = goals.associateBy { it.id }
            val txMap = transactions.associateBy { it.id }

            for (wallet in wallets) {
                if (wallet.name.isBlank()) {
                    throw BackupException.IntegrityValidationException("Wallet name cannot be blank")
                }
                if (wallet.openingBalance < 0L) {
                    throw BackupException.IntegrityValidationException("Wallet opening balance cannot be negative")
                }
            }

            for (category in categories) {
                if (category.name.isBlank()) {
                    throw BackupException.IntegrityValidationException("Category name cannot be blank")
                }
            }

            for (goal in goals) {
                if (goal.name.isBlank()) {
                    throw BackupException.IntegrityValidationException("Goal name cannot be blank")
                }
                if (goal.targetAmount <= 0L) {
                    throw BackupException.IntegrityValidationException("Goal target amount must be positive")
                }
            }

            for (tx in transactions) {
                if (tx.amount <= 0L) {
                    throw BackupException.IntegrityValidationException("Transaction amount must be positive")
                }
                val sourceWallet = walletMap[tx.walletId]
                    ?: throw BackupException.IntegrityValidationException("Missing wallet for transaction ${tx.id}")
                if (LedgerValidator.isBeforeTrackingDay(tx.transactionDate, sourceWallet.trackingStartDate)) {
                    throw BackupException.IntegrityValidationException("Transaction precedes wallet tracking start date")
                }

                when (tx.type) {
                    TransactionType.INCOME -> {
                        val cat = tx.categoryId?.let { categoryMap[it] }
                            ?: throw BackupException.IntegrityValidationException("Income transaction missing category")
                        if (cat.type != CategoryType.INCOME || tx.destinationWalletId != null) {
                            throw BackupException.IntegrityValidationException("Invalid income transaction category or destination")
                        }
                    }
                    TransactionType.EXPENSE -> {
                        val cat = tx.categoryId?.let { categoryMap[it] }
                            ?: throw BackupException.IntegrityValidationException("Expense transaction missing category")
                        if (cat.type != CategoryType.EXPENSE || tx.destinationWalletId != null) {
                            throw BackupException.IntegrityValidationException("Invalid expense transaction category or destination")
                        }
                    }
                    TransactionType.TRANSFER -> {
                        val destId = tx.destinationWalletId
                            ?: throw BackupException.IntegrityValidationException("Transfer missing destination wallet")
                        if (destId == tx.walletId || tx.categoryId != null) {
                            throw BackupException.IntegrityValidationException("Invalid transfer configuration")
                        }
                        val destWallet = walletMap[destId]
                            ?: throw BackupException.IntegrityValidationException("Missing destination wallet for transfer")
                        if (LedgerValidator.isBeforeTrackingDay(tx.transactionDate, destWallet.trackingStartDate)) {
                            throw BackupException.IntegrityValidationException("Transfer precedes destination tracking start date")
                        }
                    }
                    TransactionType.GOAL_ALLOCATION,
                    TransactionType.GOAL_WITHDRAWAL -> {
                        val goalId = tx.goalId
                            ?: throw BackupException.IntegrityValidationException("Goal ledger transaction missing goalId")
                        if (!goalMap.containsKey(goalId)) {
                            throw BackupException.IntegrityValidationException("Missing goal for transaction ${tx.id}")
                        }
                    }
                }
            }

            val allocByTxId = allocations.associateBy { it.transactionId }
            if (allocByTxId.size != allocations.size) {
                throw BackupException.IntegrityValidationException("Duplicate transactionId in goal allocations")
            }

            for (tx in transactions) {
                if (tx.type == TransactionType.GOAL_ALLOCATION || tx.type == TransactionType.GOAL_WITHDRAWAL) {
                    val entry = allocByTxId[tx.id]
                        ?: throw BackupException.IntegrityValidationException("Missing goal allocation entry for ${tx.id}")
                    val expectedLedgerType = if (tx.type == TransactionType.GOAL_ALLOCATION) {
                        GoalLedgerType.ALLOCATION
                    } else {
                        GoalLedgerType.WITHDRAWAL
                    }
                    if (entry.entryType != expectedLedgerType ||
                        entry.goalId != tx.goalId ||
                        entry.walletId != tx.walletId ||
                        entry.amount != tx.amount
                    ) {
                        throw BackupException.IntegrityValidationException("Goal transaction/allocation mismatch")
                    }
                }
            }

            for (entry in allocations) {
                if (entry.amount <= 0L) {
                    throw BackupException.IntegrityValidationException("Goal allocation amount must be positive")
                }
                if (!goalMap.containsKey(entry.goalId) || !walletMap.containsKey(entry.walletId)) {
                    throw BackupException.IntegrityValidationException("Goal allocation references missing goal or wallet")
                }
                val parentTx = txMap[entry.transactionId]
                    ?: throw BackupException.IntegrityValidationException("Goal allocation references missing transaction")

                val linkedId = entry.linkedExpenseTransactionId
                if (entry.entryType == GoalLedgerType.WITHDRAWAL &&
                    entry.withdrawalTarget == WithdrawalTarget.DIRECT_EXPENSE
                ) {
                    val expTx = linkedId?.let { txMap[it] }
                        ?: throw BackupException.IntegrityValidationException("DIRECT_EXPENSE missing linked expense")
                    if (expTx.type != TransactionType.EXPENSE ||
                        expTx.amount != entry.amount ||
                        expTx.walletId != entry.walletId ||
                        expTx.transactionDate != parentTx.transactionDate
                    ) {
                        throw BackupException.IntegrityValidationException("DIRECT_EXPENSE linked expense mismatch")
                    }
                } else if (linkedId != null) {
                    throw BackupException.IntegrityValidationException("Unexpected linkedExpenseTransactionId")
                }
            }

            for (wallet in wallets) {
                val summary = FinancialEngine.summarizeWallet(wallet, transactions, allocations)
                if (summary.currentBalance < 0L) {
                    throw BackupException.IntegrityValidationException("Negative balance in wallet ${wallet.name}")
                }
                if (summary.freeMoney < 0L) {
                    throw BackupException.IntegrityValidationException("Negative free money in wallet ${wallet.name}")
                }
            }

            for (goal in goals) {
                val goalSummary = FinancialEngine.summarizeGoal(goal, allocations)
                if (goalSummary.allocatedAmount < 0L) {
                    throw BackupException.IntegrityValidationException("Negative allocation in goal ${goal.name}")
                }
            }

            val overview = FinancialEngine.calculateFinancialOverview(wallets, transactions, goals, allocations)
            if (overview.protectedFunds > overview.allocatedFunds ||
                overview.allocatedFunds > overview.totalAssets ||
                SafeMath.add(overview.freeMoney, overview.allocatedFunds) != overview.totalAssets
            ) {
                throw BackupException.IntegrityValidationException("Financial overview invariants violated")
            }

            val activeBudgets = budgets.filterNot { it.isArchived }
            for (budget in budgets) {
                if (budget.name.isBlank() || budget.limitAmount <= 0L) {
                    throw BackupException.IntegrityValidationException("Invalid budget name or limit")
                }
                val cat = categoryMap[budget.categoryId]
                    ?: throw BackupException.IntegrityValidationException("Budget references missing category")
                if (cat.type != CategoryType.EXPENSE) {
                    throw BackupException.IntegrityValidationException("Budget category must be EXPENSE")
                }
                if (budget.periodType == BudgetPeriodType.CUSTOM && budget.endDateExclusive <= budget.startDate) {
                    throw BackupException.IntegrityValidationException("Invalid custom budget date range")
                }
            }
            for (i in activeBudgets.indices) {
                for (j in i + 1 until activeBudgets.size) {
                    if (BudgetPeriodResolver.overlaps(activeBudgets[i], activeBudgets[j])) {
                        throw BackupException.IntegrityValidationException("Overlapping active budgets in backup")
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: DomainException) {
            throw BackupException.IntegrityValidationException(e.message ?: "Financial integrity validation failed")
        }
    }

    private fun validateUniqueIds(payload: BackupPayload) {
        if (payload.wallets.mapTo(HashSet()) { it.id }.size != payload.wallets.size) {
            throw BackupException.IntegrityValidationException("Duplicate wallet IDs")
        }
        if (payload.categories.mapTo(HashSet()) { it.id }.size != payload.categories.size) {
            throw BackupException.IntegrityValidationException("Duplicate category IDs")
        }
        if (payload.goals.mapTo(HashSet()) { it.id }.size != payload.goals.size) {
            throw BackupException.IntegrityValidationException("Duplicate goal IDs")
        }
        if (payload.transactions.mapTo(HashSet()) { it.id }.size != payload.transactions.size) {
            throw BackupException.IntegrityValidationException("Duplicate transaction IDs")
        }
        if (payload.goalAllocations.mapTo(HashSet()) { it.id }.size != payload.goalAllocations.size) {
            throw BackupException.IntegrityValidationException("Duplicate goal allocation IDs")
        }
        if (payload.budgets.mapTo(HashSet()) { it.id }.size != payload.budgets.size) {
            throw BackupException.IntegrityValidationException("Duplicate budget IDs")
        }
    }
}
