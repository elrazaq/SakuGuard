package com.sakuguard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sakuguard.app.core.database.dao.BudgetDao
import com.sakuguard.app.core.database.dao.CategoryDao
import com.sakuguard.app.core.database.dao.GoalAllocationDao
import com.sakuguard.app.core.database.dao.GoalDao
import com.sakuguard.app.core.database.dao.TransactionDao
import com.sakuguard.app.core.database.dao.WalletDao
import com.sakuguard.app.core.database.entity.BudgetEntity
import com.sakuguard.app.core.database.entity.CategoryEntity
import com.sakuguard.app.core.database.entity.GoalAllocationEntity
import com.sakuguard.app.core.database.entity.GoalEntity
import com.sakuguard.app.core.database.entity.TransactionEntity
import com.sakuguard.app.core.database.entity.WalletEntity

@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        GoalEntity::class,
        TransactionEntity::class,
        GoalAllocationEntity::class,
        BudgetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SakuGuardDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun goalDao(): GoalDao
    abstract fun transactionDao(): TransactionDao
    abstract fun goalAllocationDao(): GoalAllocationDao
    abstract fun budgetDao(): BudgetDao
}
