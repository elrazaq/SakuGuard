package com.sakuguard.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.graphics.vector.ImageVector
import com.sakuguard.app.R

enum class SakuDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(
        route = "home",
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.Home,
    ),
    Wallets(
        route = "wallets",
        labelRes = R.string.nav_wallets,
        icon = Icons.Outlined.AccountBalanceWallet,
    ),
    Goals(
        route = "goals",
        labelRes = R.string.nav_goals,
        icon = Icons.Outlined.Savings,
    ),
    Transactions(
        route = "transactions",
        labelRes = R.string.nav_transactions,
        icon = Icons.Outlined.ReceiptLong,
    ),
    Reports(
        route = "reports",
        labelRes = R.string.nav_reports,
        icon = Icons.Outlined.BarChart,
    ),
}
