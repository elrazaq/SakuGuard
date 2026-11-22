package com.sakuguard.app.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakuguard.app.core.database.SakuDatabaseSeeder
import com.sakuguard.app.core.database.SakuGuardDatabase
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
        ).addCallback(object : RoomDatabase.Callback() {
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
}
