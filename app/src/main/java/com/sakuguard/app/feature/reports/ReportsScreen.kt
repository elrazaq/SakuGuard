package com.sakuguard.app.feature.reports

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.runtime.Composable
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.EmptyDestinationScaffold

@Composable
fun ReportsScreen() {
    EmptyDestinationScaffold(
        icon = Icons.Outlined.BarChart,
        titleRes = R.string.empty_reports_title,
        bodyRes = R.string.empty_reports_body,
    )
}
