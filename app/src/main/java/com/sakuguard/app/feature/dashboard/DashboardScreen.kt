package com.sakuguard.app.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.EmptyDestinationScaffold

@Composable
fun DashboardScreen() {
    EmptyDestinationScaffold(
        icon = Icons.Outlined.Home,
        titleRes = R.string.empty_home_title,
        bodyRes = R.string.empty_home_body,
    )
}
