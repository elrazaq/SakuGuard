package com.sakuguard.app.feature.wallets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.Composable
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.EmptyDestinationScaffold

@Composable
fun WalletsScreen() {
    EmptyDestinationScaffold(
        icon = Icons.Outlined.AccountBalanceWallet,
        titleRes = R.string.empty_wallets_title,
        bodyRes = R.string.empty_wallets_body,
    )
}
