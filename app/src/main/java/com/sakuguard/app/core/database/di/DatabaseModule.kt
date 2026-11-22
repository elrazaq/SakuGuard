package com.sakuguard.app.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakuguard.app.core.database.SakuDatabaseSeeder
import com.sakuguard.app.core.database.SakuGuardDatabase
import com.sakuguard.app.core.database.dao.BudgetDao
import com.sakuguard.app.core.database.dao.CategoryDao
import com.sakuguard.app.core.database.dao.GoalAllocationDao
import com.sakuguard.app.core.database.dao.GoalDao
import com.sakuguard.app.core.database.dao.TransactionDao
import com.sakuguard.app.core.database.dao.WalletDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `limitAmount` INTEGER NOT NULL,
                `periodType` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `endDateExclusive` INTEGER NOT NULL,
                `alert80Enabled` INTEGER NOT NULL,
                `alert100Enabled` INTEGER NOT NULL,
                `lastAlertedPeriodKey` TEXT,
                `lastAlertedLevel` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_isArchived_startDate_endDateExclusive` ON `budgets` (`isArchived`, `startDate`, `endDateExclusive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type_categoryId_transactionDate` ON `transactions` (`type`, `categoryId`, `transactionDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionDate_createdAt_id` ON `transactions` (`transactionDate`, `createdAt`, `id`)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        categoryDaoProvider: Provider<CategoryDao>,
    ): SakuGuardDatabase {
        return Room.databaseBuilder(
            context,
            SakuGuardDatabase::class.java,
            "sakuguard.db",
        ).addMigrations(MIGRATION_1_2)
            .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    categoryDaoProvider.get().insertAllIgnore(
                        SakuDatabaseSeeder.defaultCategories(System.currentTimeMillis()),
                    )
                }
            }
        }).build()
    }

    @Provides fun provideWalletDao(db: SakuGuardDatabase): WalletDao = db.walletDao()
    @Provides fun provideCategoryDao(db: SakuGuardDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideGoalDao(db: SakuGuardDatabase): GoalDao = db.goalDao()
    @Provides fun provideTransactionDao(db: SakuGuardDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideGoalAllocationDao(db: SakuGuardDatabase): GoalAllocationDao = db.goalAllocationDao()
    @Provides fun provideBudgetDao(db: SakuGuardDatabase): BudgetDao = db.budgetDao()
}
