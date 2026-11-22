package com.sakuguard.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sakuguard.app.feature.dashboard.DashboardScreen
import com.sakuguard.app.feature.goals.GoalsScreen
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

    fun walletDetail(walletId: String): String = "wallet_detail/$walletId"
    fun walletCreate(): String = "wallet_form"
    fun walletEdit(walletId: String): String = "wallet_form?walletId=$walletId"

    fun transactionDetail(transactionId: String): String = "transaction_detail/$transactionId"
    fun transactionCreate(): String = "transaction_form"
    fun transactionEdit(transactionId: String): String = "transaction_form?transactionId=$transactionId"
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
        composable(SakuDestination.Home.route) { DashboardScreen() }
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
        composable(SakuDestination.Goals.route) { GoalsScreen() }
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
        composable(SakuDestination.Reports.route) { ReportsScreen() }
    }
}
