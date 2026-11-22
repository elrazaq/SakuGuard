package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.Wallet
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun observeWallets(includeArchived: Boolean = true): Flow<List<Wallet>>
    suspend fun getAllWallets(): List<Wallet>
    suspend fun getWalletById(id: String): Wallet?
    suspend fun createWallet(wallet: Wallet)
    suspend fun updateWallet(wallet: Wallet)
    suspend fun setArchived(walletId: String, archived: Boolean, updatedAt: Long)
}
