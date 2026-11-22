package com.sakuguard.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.core.database.di.MIGRATION_1_2
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2AndQueryPlanTest {

    @Test
    fun migration1To2CreatesBudgetsTableAndCompositeIndicesNonDestructively() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_1_2_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `categories` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `name` TEXT NOT NULL,
                                `type` TEXT NOT NULL,
                                `iconName` TEXT NOT NULL,
                                `colorHex` INTEGER NOT NULL,
                                `isDefault` INTEGER NOT NULL,
                                `isArchived` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `transactions` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `type` TEXT NOT NULL,
                                `amount` INTEGER NOT NULL,
                                `walletId` TEXT NOT NULL,
                                `destinationWalletId` TEXT,
                                `categoryId` TEXT,
                                `notes` TEXT,
                                `transactionDate` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "INSERT INTO categories VALUES ('c1', 'Food', 'EXPENSE', 'restaurant', 0, 1, 0, 100)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

        val v1Db = helper.writableDatabase
        MIGRATION_1_2.migrate(v1Db)

        val tablesCursor = v1Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='budgets'")
        assertThat(tablesCursor.count).isEqualTo(1)
        tablesCursor.close()

        val catCursor = v1Db.query("SELECT COUNT(*) FROM categories WHERE id='c1'")
        catCursor.moveToFirst()
        assertThat(catCursor.getInt(0)).isEqualTo(1)
        catCursor.close()

        val indexCursor = v1Db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='transactions'",
        )
        val indices = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(0))
        }
        indexCursor.close()
        assertThat(indices).containsAtLeast(
            "index_transactions_type_categoryId_transactionDate",
            "index_transactions_transactionDate_createdAt_id",
        )

        v1Db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun queryPlansUseCompositeIndicesForKeysetAndBudgetAggregation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SakuGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sqlite = db.openHelper.writableDatabase

        val budgetPlan = explain(
            sqlite,
            """
            SELECT COALESCE(SUM(amount), 0) FROM transactions
            WHERE type = 'EXPENSE' AND categoryId = 'c1' AND transactionDate >= 100 AND transactionDate < 200
            """.trimIndent(),
        )
        assertThat(budgetPlan).contains("USING INDEX")

        val keysetPlan = explain(
            sqlite,
            """
            SELECT * FROM transactions
            ORDER BY transactionDate DESC, createdAt DESC, id DESC
            LIMIT 20
            """.trimIndent(),
        )
        assertThat(keysetPlan).contains("USING INDEX")

        db.close()
    }

    private fun explain(db: SupportSQLiteDatabase, sql: String): String {
        val cursor = db.query("EXPLAIN QUERY PLAN $sql")
        val sb = StringBuilder()
        while (cursor.moveToNext()) {
            sb.append(cursor.getString(cursor.columnCount - 1)).append('\n')
        }
        cursor.close()
        return sb.toString()
    }
}
