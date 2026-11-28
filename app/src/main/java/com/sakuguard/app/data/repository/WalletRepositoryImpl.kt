package com.sakuguard.app.data.repository

import com.sakuguard.app.core.database.dao.WalletDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
) : WalletRepository {
    override fun observeWallets(includeArchived: Boolean): Flow<List<Wallet>> =
        walletDao.observeWallets(includeArchived).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllWallets(): List<Wallet> =
        walletDao.getAllWallets().map { it.toDomain() }

    override suspend fun getWalletById(id: String): Wallet? =
        walletDao.getWalletById(id)?.toDomain()

    override suspend fun createWallet(wallet: Wallet) {
        walletDao.insert(wallet.toEntity())
    }

    override suspend fun updateWallet(wallet: Wallet) {
        walletDao.update(wallet.toEntity())
    }

    override suspend fun setArchived(walletId: String, archived: Boolean, updatedAt: Long) {
        walletDao.setArchived(walletId, archived, updatedAt)
    }

    override suspend fun deleteWallet(walletId: String) {
        walletDao.deleteById(walletId)
    }
}
