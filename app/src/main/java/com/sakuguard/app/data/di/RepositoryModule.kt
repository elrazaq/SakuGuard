package com.sakuguard.app.data.di

import com.sakuguard.app.data.repository.BudgetRepositoryImpl
import com.sakuguard.app.data.repository.CategoryRepositoryImpl
import com.sakuguard.app.data.repository.GoalRepositoryImpl
import com.sakuguard.app.data.repository.TransactionRepositoryImpl
import com.sakuguard.app.data.repository.WalletRepositoryImpl
import com.sakuguard.app.domain.repository.BudgetRepository
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository

    @Binds @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository
}
