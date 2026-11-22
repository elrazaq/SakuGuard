package com.sakuguard.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.core.database.SakuGuardDatabase
import com.sakuguard.app.core.database.entity.BudgetEntity
import com.sakuguard.app.core.database.entity.CategoryEntity
import com.sakuguard.app.core.database.entity.GoalAllocationEntity
import com.sakuguard.app.core.database.entity.GoalEntity
import com.sakuguard.app.core.database.entity.TransactionEntity
import com.sakuguard.app.core.database.entity.WalletEntity
import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WalletType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRestoreDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: SakuGuardDatabase
    private lateinit var repo: BackupRepositoryImpl
    private lateinit var backupFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SakuGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BackupRepositoryImpl(
            context = context,
            database = db,
            walletDao = db.walletDao(),
            categoryDao = db.categoryDao(),
            goalDao = db.goalDao(),
            transactionDao = db.transactionDao(),
            goalAllocationDao = db.goalAllocationDao(),
            budgetDao = db.budgetDao(),
        )
        backupFile = File(context.cacheDir, "test_roundtrip.saku").apply { delete() }
    }

    @After
    fun tearDown() {
        backupFile.delete()
        db.close()
    }

    @Test
    fun exportAndRestoreAllSixEntitiesAndRollbackOnFailure() = runBlocking {
        db.walletDao().insert(WalletEntity("w1", "BCA", WalletType.BANK_ACCOUNT, 5_000_000L, 0L, "", null, null, false, 0L, 0L))
        db.categoryDao().upsert(CategoryEntity("c1", "Food", CategoryType.EXPENSE, "restaurant", 0L, true, false, 0L))
        db.goalDao().insert(GoalEntity("g1", "Trip", 2_000_000L, null, "", null, null, 0, GoalStatus.ACTIVE, ProtectionMode.PROTECTED, 0L, null, 0L))
        db.transactionDao().insert(TransactionEntity("t1", TransactionType.EXPENSE, 150_000L, "w1", null, "c1", null, "", "", 100L, 100L, 100L))
        db.transactionDao().insert(TransactionEntity("t2", TransactionType.GOAL_ALLOCATION, 500_000L, "w1", null, null, "g1", "", "", 100L, 100L, 100L))
        db.goalAllocationDao().insert(GoalAllocationEntity("a1", "g1", "w1", "t2", null, GoalLedgerType.ALLOCATION, 500_000L, null, null, 100L))
        db.budgetDao().insert(BudgetEntity("b1", "Food Cap", "c1", 1_000_000L, BudgetPeriodType.MONTHLY, 0L, 0L, true, true, null, 0, false, 0L, 0L))

        val uri = Uri.fromFile(backupFile).toString()
        val exportResult = repo.exportBackup(uri, "Pass#2026".toCharArray())
        assertThat(exportResult.encrypted).isTrue()
        assertThat(exportResult.recordCounts.transactions).isEqualTo(2)

        // Mutate live DB to verify replace-all
        db.walletDao().insert(WalletEntity("w_extra", "Cash", WalletType.CASH, 100_000L, 0L, "", null, null, false, 0L, 0L))
        assertThat(db.walletDao().getAllWallets()).hasSize(2)

        // Wrong password must leave live DB untouched (2 wallets)
        assertThrows(BackupException.WrongPasswordOrCorruptedException::class.java) {
            runBlocking { repo.restoreBackup(uri, "Wrong".toCharArray()) }
        }
        assertThat(db.walletDao().getAllWallets()).hasSize(2)

        // Correct password restores exact snapshot (1 wallet, 1 budget, 1 goal allocation)
        val restoreResult = repo.restoreBackup(uri, "Pass#2026".toCharArray())
        assertThat(restoreResult.recordCounts.transactions).isEqualTo(2)
        assertThat(db.walletDao().getAllWallets().map { it.id }).containsExactly("w1")
        assertThat(db.budgetDao().getAllBudgets().map { it.id }).containsExactly("b1")
        assertThat(db.goalAllocationDao().getAllAllocations().map { it.id }).containsExactly("a1")
    }
}
