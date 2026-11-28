package com.sakuguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sakuguard.app.core.database.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE (:includeArchived = 1 OR isArchived = 0) ORDER BY createdAt ASC")
    fun observeWallets(includeArchived: Boolean): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    suspend fun getAllWallets(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getWalletById(id: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallet: WalletEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(wallets: List<WalletEntity>)

    @Query("DELETE FROM wallets")
    suspend fun deleteAll()

    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("UPDATE wallets SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :walletId")
    suspend fun setArchived(walletId: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun deleteById(walletId: String)
}
