package com.sakuguard.app.feature.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.EmptyDestinationScaffold
import com.sakuguard.app.core.designsystem.LocalSakuBottomBarPadding
import com.sakuguard.app.core.designsystem.LocalSakuColors
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.BudgetHealthStatus
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.BudgetSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onCreateBudget: () -> Unit,
    onBudgetClick: (String) -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessageRes?.let { stringResource(it) }

    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.budgets_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateBudget,
                modifier = Modifier.padding(bottom = LocalSakuBottomBarPadding.current),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.budgets_add_button),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.activeBudgets.isEmpty() && uiState.archivedBudgets.isEmpty() -> {
                EmptyDestinationScaffold(
                    icon = Icons.Outlined.PieChart,
                    titleRes = R.string.budgets_empty_title,
                    bodyRes = R.string.budgets_empty_body,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = SakuSpacing.lg,
                        end = SakuSpacing.lg,
                        top = SakuSpacing.sm,
                        bottom = LocalSakuBottomBarPadding.current + 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    if (uiState.thresholdAlerts.isNotEmpty()) {
                        item(key = "threshold_alerts") {
                            ThresholdAlertBanner(
                                alerts = uiState.thresholdAlerts,
                                onDismiss = viewModel::dismissAlerts,
                            )
                        }
                    }

                    if (uiState.activeBudgets.isNotEmpty()) {
                        item(key = "active_header") {
                            Text(
                                text = stringResource(R.string.budgets_active_section),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(
                            items = uiState.activeBudgets,
                            key = { it.budget.id },
                        ) { summary ->
                            BudgetSummaryCard(
                                summary = summary,
                                onClick = { onBudgetClick(summary.budget.id) },
                            )
                        }
                    }

                    if (uiState.archivedBudgets.isNotEmpty()) {
                        item(key = "archived_toggle") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = viewModel::toggleShowArchived)
                                    .padding(vertical = SakuSpacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.budgets_archived_section,
                                        uiState.archivedBudgets.size,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = if (uiState.showArchived) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (uiState.showArchived) {
                            items(
                                items = uiState.archivedBudgets,
                                key = { "archived_${it.budget.id}" },
                            ) { summary ->
                                BudgetSummaryCard(
                                    summary = summary,
                                    onClick = { onBudgetClick(summary.budget.id) },
                                    trailingAction = {
                                        TextButton(
                                            onClick = { viewModel.restoreBudget(summary.budget.id) },
                                        ) {
                                            Text(text = stringResource(R.string.goal_restore_action))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdAlertBanner(
    alerts: List<com.sakuguard.app.domain.model.BudgetAlertEvent>,
    onDismiss: () -> Unit,
) {
    val sakuColors = LocalSakuColors.current
    val hasExceeded = alerts.any { it.level >= 100 }
    val accent = if (hasExceeded) MaterialTheme.colorScheme.error else sakuColors.warning

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakuSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = accent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                alerts.forEach { alert ->
                    val text = if (alert.level >= 100) {
                        stringResource(R.string.budget_alert_100_banner, alert.budgetName)
                    } else {
                        stringResource(R.string.budget_alert_80_banner, alert.budgetName)
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
internal fun BudgetSummaryCard(
    summary: BudgetSummary,
    onClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val sakuColors = LocalSakuColors.current
    val statusColor = when (summary.healthStatus) {
        BudgetHealthStatus.ON_TRACK -> sakuColors.income
        BudgetHealthStatus.WARNING -> sakuColors.warning
        BudgetHealthStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }
    val statusLabel = when (summary.healthStatus) {
        BudgetHealthStatus.ON_TRACK -> stringResource(R.string.budget_status_on_track)
        BudgetHealthStatus.WARNING -> stringResource(R.string.budget_status_warning)
        BudgetHealthStatus.EXCEEDED -> stringResource(R.string.budget_status_exceeded)
    }
    val periodLabel = when (summary.budget.periodType) {
        BudgetPeriodType.MONTHLY -> stringResource(R.string.budget_period_monthly)
        BudgetPeriodType.WEEKLY -> stringResource(R.string.budget_period_weekly)
        BudgetPeriodType.CUSTOM -> stringResource(R.string.budget_period_custom)
    }
    val progress = (summary.utilizationPermille / 1000f).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(SakuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${summary.category?.name ?: "-"} • $periodLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.budget_spent_format,
                        CurrencyFormatter.formatIdr(summary.spentAmount),
                    ) + " / " + CurrencyFormatter.formatIdr(summary.budget.limitAmount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (summary.overspentAmount > 0L) {
                        stringResource(
                            R.string.budget_overspent_format,
                            CurrencyFormatter.formatIdr(summary.overspentAmount),
                        )
                    } else {
                        stringResource(
                            R.string.budget_remaining_format,
                            CurrencyFormatter.formatIdr(summary.remainingAmount),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val endInclusive = (summary.effectiveEndExclusive - 1L).coerceAtLeast(summary.effectiveStart)
                Text(
                    text = "${DateFormatter.formatShortDate(summary.effectiveStart)} – ${DateFormatter.formatShortDate(endInclusive)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trailingAction != null) {
                    trailingAction()
                }
            }
        }
    }
}
