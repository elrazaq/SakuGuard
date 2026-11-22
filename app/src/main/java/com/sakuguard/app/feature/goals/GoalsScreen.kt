package com.sakuguard.app.feature.goals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.runtime.Composable
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.EmptyDestinationScaffold

@Composable
fun GoalsScreen() {
    EmptyDestinationScaffold(
        icon = Icons.Outlined.Savings,
        titleRes = R.string.empty_goals_title,
        bodyRes = R.string.empty_goals_body,
    )
}
