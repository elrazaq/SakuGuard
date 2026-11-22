package com.sakuguard.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportQueryPlanTest {

    @Test
    fun reportQueriesUseExistingIndicesWithoutTableScan() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SakuGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sqlite = db.openHelper.writableDatabase

        val categoryPlan = explain(
            sqlite,
            """
            SELECT categoryId AS categoryId, COALESCE(SUM(amount), 0) AS totalAmount
            FROM transactions
            WHERE type = 'EXPENSE' AND transactionDate >= 100 AND transactionDate < 200
            GROUP BY categoryId
            ORDER BY totalAmount DESC
            """.trimIndent(),
        )
        assertThat(categoryPlan).contains("USING INDEX")

        val periodTotalsPlan = explain(
            sqlite,
            """
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
                   COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses
            FROM transactions
            WHERE transactionDate >= 100 AND transactionDate < 200
            """.trimIndent(),
        )
        assertThat(periodTotalsPlan).contains("USING INDEX")

        val goalContribPlan = explain(
            sqlite,
            """
            SELECT ga.entryType AS entryType, COALESCE(SUM(ga.amount), 0) AS totalAmount
            FROM goal_allocations ga
            INNER JOIN transactions t ON ga.transactionId = t.id
            WHERE t.transactionDate >= 100 AND t.transactionDate < 200
            GROUP BY ga.entryType
            """.trimIndent(),
        )
        assertThat(goalContribPlan).contains("USING INDEX")

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
