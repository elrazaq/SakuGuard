package com.sakuguard.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sakuguard.app.feature.budgets.BudgetDetailScreen
import com.sakuguard.app.feature.budgets.BudgetFormScreen
import com.sakuguard.app.feature.budgets.BudgetsScreen
import com.sakuguard.app.feature.dashboard.DashboardScreen
import com.sakuguard.app.feature.goals.GoalAllocateScreen
import com.sakuguard.app.feature.goals.GoalDetailScreen
import com.sakuguard.app.feature.goals.GoalFormScreen
import com.sakuguard.app.feature.goals.GoalWithdrawScreen
import com.sakuguard.app.feature.goals.GoalsScreen
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.feature.reports.ReportCategoryDetailScreen
import com.sakuguard.app.feature.reports.ReportCategoryDetailViewModel
import com.sakuguard.app.feature.reports.ReportsScreen
import com.sakuguard.app.feature.transactions.TransactionDetailScreen
import com.sakuguard.app.feature.transactions.TransactionFormScreen
import com.sakuguard.app.feature.transactions.TransactionsScreen
import com.sakuguard.app.feature.wallets.WalletDetailScreen
import com.sakuguard.app.feature.wallets.WalletFormScreen
import com.sakuguard.app.feature.wallets.WalletsScreen

object SakuRoutes {
    const val WALLET_DETAIL = "wallet_detail/{walletId}"
    const val WALLET_FORM = "wallet_form?walletId={walletId}"
    const val TRANSACTION_DETAIL = "transaction_detail/{transactionId}"
    const val TRANSACTION_FORM = "transaction_form?transactionId={transactionId}"
    const val GOAL_DETAIL = "goal_detail/{goalId}"
    const val GOAL_FORM = "goal_form?goalId={goalId}"
    const val GOAL_ALLOCATE = "goal_allocate/{goalId}"
    const val GOAL_WITHDRAW = "goal_withdraw/{goalId}"
    const val BUDGETS = "budgets"
    const val BUDGET_DETAIL = "budget_detail/{budgetId}"
    const val BUDGET_FORM = "budget_form?budgetId={budgetId}"
    const val REPORT_CATEGORY_DETAIL =
        "report_category_detail/{type}/{categoryId}/{startDate}/{endDateExclusive}"

    fun walletDetail(walletId: String): String = "wallet_detail/$walletId"
    fun walletCreate(): String = "wallet_form"
    fun walletEdit(walletId: String): String = "wallet_form?walletId=$walletId"

    fun transactionDetail(transactionId: String): String = "transaction_detail/$transactionId"
    fun transactionCreate(): String = "transaction_form"
    fun transactionEdit(transactionId: String): String = "transaction_form?transactionId=$transactionId"

    fun goalDetail(goalId: String): String = "goal_detail/$goalId"
    fun goalCreate(): String = "goal_form"
    fun goalEdit(goalId: String): String = "goal_form?goalId=$goalId"
    fun goalAllocate(goalId: String): String = "goal_allocate/$goalId"
    fun goalWithdraw(goalId: String): String = "goal_withdraw/$goalId"

    fun budgets(): String = BUDGETS
    fun budgetDetail(budgetId: String): String = "budget_detail/$budgetId"
    fun budgetCreate(): String = "budget_form"
    fun budgetEdit(budgetId: String): String = "budget_form?budgetId=$budgetId"

    fun reportCategoryDetail(
        type: TransactionType,
        categoryId: String?,
        startDate: Long,
        endDateExclusive: Long,
    ): String {
        val catKey = categoryId ?: ReportCategoryDetailViewModel.UNCATEGORIZED_KEY
        return "report_category_detail/${type.name}/$catKey/$startDate/$endDateExclusive"
    }
}

@Composable
fun SakuNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SakuDestination.Home.route,
        modifier = modifier,
    ) {
        composable(SakuDestination.Home.route) {
            DashboardScreen(
                onOpenWallets = { navigateToTab(navController, SakuDestination.Wallets.route) },
                onWalletClick = { walletId -> navController.navigate(SakuRoutes.walletDetail(walletId)) },
                onAddWallet = { navController.navigate(SakuRoutes.walletCreate()) },
                onOpenGoals = { navigateToTab(navController, SakuDestination.Goals.route) },
                onGoalClick = { goalId -> navController.navigate(SakuRoutes.goalDetail(goalId)) },
                onAddGoal = { navController.navigate(SakuRoutes.goalCreate()) },
                onOpenTransactions = { navigateToTab(navController, SakuDestination.Transactions.route) },
                onTransactionClick = { txId -> navController.navigate(SakuRoutes.transactionDetail(txId)) },
                onAddTransaction = { navController.navigate(SakuRoutes.transactionCreate()) },
                onOpenBudgets = { navController.navigate(SakuRoutes.budgets()) },
            )
        }
        composable(SakuDestination.Wallets.route) {
            WalletsScreen(
                onCreateWallet = { navController.navigate(SakuRoutes.walletCreate()) },
                onWalletClick = { walletId -> navController.navigate(SakuRoutes.walletDetail(walletId)) },
            )
        }
        composable(
            route = SakuRoutes.WALLET_DETAIL,
            arguments = listOf(navArgument("walletId") { type = NavType.StringType }),
        ) {
            WalletDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { walletId -> navController.navigate(SakuRoutes.walletEdit(walletId)) },
            )
        }
        composable(
            route = SakuRoutes.WALLET_FORM,
            arguments = listOf(
                navArgument("walletId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            WalletFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(SakuDestination.Goals.route) {
            GoalsScreen(
                onCreateGoal = { navController.navigate(SakuRoutes.goalCreate()) },
                onGoalClick = { goalId -> navController.navigate(SakuRoutes.goalDetail(goalId)) },
            )
        }
        composable(
            route = SakuRoutes.GOAL_DETAIL,
            arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
        ) {
            GoalDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { goalId -> navController.navigate(SakuRoutes.goalEdit(goalId)) },
                onAllocate = { goalId -> navController.navigate(SakuRoutes.goalAllocate(goalId)) },
                onWithdraw = { goalId -> navController.navigate(SakuRoutes.goalWithdraw(goalId)) },
            )
        }
        composable(
            route = SakuRoutes.GOAL_FORM,
            arguments = listOf(
                navArgument("goalId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            GoalFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = SakuRoutes.GOAL_ALLOCATE,
            arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
        ) {
            GoalAllocateScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = SakuRoutes.GOAL_WITHDRAW,
            arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
        ) {
            GoalWithdrawScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(SakuDestination.Transactions.route) {
            TransactionsScreen(
                onCreateTransaction = { navController.navigate(SakuRoutes.transactionCreate()) },
                onTransactionClick = { txId -> navController.navigate(SakuRoutes.transactionDetail(txId)) },
            )
        }
        composable(
            route = SakuRoutes.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) {
            TransactionDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { txId -> navController.navigate(SakuRoutes.transactionEdit(txId)) },
            )
        }
        composable(
            route = SakuRoutes.TRANSACTION_FORM,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            TransactionFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(SakuDestination.Reports.route) {
            ReportsScreen(
                onOpenCategoryDetail = { type, categoryId, start, end ->
                    navController.navigate(SakuRoutes.reportCategoryDetail(type, categoryId, start, end))
                },
                onOpenBudgets = { navController.navigate(SakuRoutes.budgets()) },
                onOpenGoals = { navigateToTab(navController, SakuDestination.Goals.route) },
            )
        }
        composable(
            route = SakuRoutes.REPORT_CATEGORY_DETAIL,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("startDate") { type = NavType.LongType },
                navArgument("endDateExclusive") { type = NavType.LongType },
            ),
        ) {
            ReportCategoryDetailScreen(
                onBack = { navController.popBackStack() },
                onTransactionClick = { txId -> navController.navigate(SakuRoutes.transactionDetail(txId)) },
            )
        }
        composable(SakuRoutes.BUDGETS) {
            BudgetsScreen(
                onBack = { navController.popBackStack() },
                onCreateBudget = { navController.navigate(SakuRoutes.budgetCreate()) },
                onBudgetClick = { budgetId -> navController.navigate(SakuRoutes.budgetDetail(budgetId)) },
            )
        }
        composable(
            route = SakuRoutes.BUDGET_DETAIL,
            arguments = listOf(navArgument("budgetId") { type = NavType.StringType }),
        ) {
            BudgetDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { budgetId -> navController.navigate(SakuRoutes.budgetEdit(budgetId)) },
            )
        }
        composable(
            route = SakuRoutes.BUDGET_FORM,
            arguments = listOf(
                navArgument("budgetId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            BudgetFormScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
