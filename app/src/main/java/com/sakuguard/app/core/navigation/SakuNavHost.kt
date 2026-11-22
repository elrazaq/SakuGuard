package com.sakuguard.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sakuguard.app.feature.dashboard.DashboardScreen
import com.sakuguard.app.feature.goals.GoalsScreen
import com.sakuguard.app.feature.reports.ReportsScreen
import com.sakuguard.app.feature.transactions.TransactionsScreen
import com.sakuguard.app.feature.wallets.WalletsScreen

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
        composable(SakuDestination.Wallets.route) { WalletsScreen() }
        composable(SakuDestination.Goals.route) { GoalsScreen() }
        composable(SakuDestination.Transactions.route) { TransactionsScreen() }
        composable(SakuDestination.Reports.route) { ReportsScreen() }
    }
}
