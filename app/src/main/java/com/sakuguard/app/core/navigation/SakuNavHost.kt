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
import com.sakuguard.app.feature.transactions.TransactionsScreen
import com.sakuguard.app.feature.wallets.WalletDetailScreen
import com.sakuguard.app.feature.wallets.WalletFormScreen
import com.sakuguard.app.feature.wallets.WalletsScreen

object SakuRoutes {
    const val WALLET_DETAIL = "wallet_detail/{walletId}"
    const val WALLET_FORM = "wallet_form?walletId={walletId}"

    fun walletDetail(walletId: String): String = "wallet_detail/$walletId"
    fun walletCreate(): String = "wallet_form"
    fun walletEdit(walletId: String): String = "wallet_form?walletId=$walletId"
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
        composable(SakuDestination.Transactions.route) { TransactionsScreen() }
        composable(SakuDestination.Reports.route) { ReportsScreen() }
    }
}
