package com.sakuguard.app.feature.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.sakuguard.app.core.designsystem.LocalSakuColors
import com.sakuguard.app.core.designsystem.SakuHeroBalanceCard
import com.sakuguard.app.core.designsystem.SakuKpiPairCard
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.model.GoalSummary
import com.sakuguard.app.domain.model.ProtectionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onCreateGoal: () -> Unit = {},
    onGoalClick: (String) -> Unit = {},
    viewModel: GoalsViewModel = hiltViewModel(),
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
                        text = stringResource(R.string.goals_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                expandedHeight = 48.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGoal,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.goals_add_button),
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

            uiState.activeGoals.isEmpty() &&
                uiState.completedGoals.isEmpty() &&
                uiState.archivedGoals.isEmpty() -> {
                EmptyDestinationScaffold(
                    icon = Icons.Outlined.Savings,
                    titleRes = R.string.empty_goals_title,
                    bodyRes = R.string.empty_goals_body,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                val sakuColors = LocalSakuColors.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = SakuSpacing.lg,
                        end = SakuSpacing.lg,
                        top = SakuSpacing.sm,
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    item {
                        SakuHeroBalanceCard(
                            title = stringResource(R.string.goals_total_allocated),
                            amountText = CurrencyFormatter.formatIdr(uiState.overview.allocatedFunds),
                            subtitle = "${stringResource(R.string.wallets_total_assets)}: ${
                                CurrencyFormatter.formatIdr(uiState.overview.totalAssets)
                            }",
                        )
                    }

                    item {
                        SakuKpiPairCard(
                            leftLabel = stringResource(R.string.goals_protected_funds),
                            leftAmount = CurrencyFormatter.formatIdr(uiState.overview.protectedFunds),
                            leftIcon = Icons.Outlined.Lock,
                            leftTint = MaterialTheme.colorScheme.primary,
                            leftContainer = MaterialTheme.colorScheme.primaryContainer,
                            rightLabel = stringResource(R.string.wallets_free_money),
                            rightAmount = CurrencyFormatter.formatIdr(uiState.overview.freeMoney),
                            rightIcon = Icons.Outlined.Savings,
                            rightTint = sakuColors.income,
                            rightContainer = sakuColors.incomeContainer,
                        )
                    }

                    if (uiState.activeGoals.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.goals_active_section),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = SakuSpacing.xs),
                            )
                        }
                        items(uiState.activeGoals, key = { it.goal.id }) { summary ->
                            GoalSummaryCard(
                                summary = summary,
                                onClick = { onGoalClick(summary.goal.id) },
                            )
                        }
                    }

                    if (uiState.completedGoals.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.goals_completed_section,
                                    uiState.completedGoals.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = sakuColors.income,
                                modifier = Modifier.padding(top = SakuSpacing.xs),
                            )
                        }
                        items(uiState.completedGoals, key = { it.goal.id }) { summary ->
                            GoalSummaryCard(
                                summary = summary,
                                onClick = { onGoalClick(summary.goal.id) },
                            )
                        }
                    }

                    if (uiState.archivedGoals.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { viewModel.toggleShowArchived() }
                                    .padding(vertical = SakuSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.goals_archived_section,
                                        uiState.archivedGoals.size,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = sakuColors.textSecondary,
                                )
                                Icon(
                                    imageVector = if (uiState.showArchived) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = sakuColors.textSecondary,
                                )
                            }
                        }

                        if (uiState.showArchived) {
                            items(uiState.archivedGoals, key = { it.goal.id }) { summary ->
                                GoalSummaryCard(
                                    summary = summary,
                                    onClick = { onGoalClick(summary.goal.id) },
                                    trailingAction = {
                                        TextButton(onClick = { viewModel.restoreGoal(summary.goal.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Restore,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.goal_restore_action))
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
private fun GoalSummaryCard(
    summary: GoalSummary,
    onClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val goal = summary.goal
    val accent = GoalVisuals.resolveColor(goal.colorHex)
    val sakuColors = LocalSakuColors.current
    val permille = FinancialEngine.calculateGoalProgressPermille(summary.allocatedAmount, goal.targetAmount)
    val percent = permille / 10

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(SakuSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = GoalVisuals.resolveIcon(goal.iconKey),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(SakuSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.goal_target_of_format,
                            CurrencyFormatter.formatIdr(summary.allocatedAmount),
                            CurrencyFormatter.formatIdr(goal.targetAmount),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakuColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProtectionBadge(mode = goal.protectionMode)
            }

            Spacer(modifier = Modifier.height(SakuSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { permille / 1000f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.width(SakuSpacing.sm))
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                )
            }

            Spacer(modifier = Modifier.height(SakuSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.goal_remaining_format,
                        CurrencyFormatter.formatIdr(summary.remainingAmount),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = sakuColors.textSecondary,
                )
                goal.deadlineEpochMillis?.let { deadline ->
                    Text(
                        text = DateFormatter.formatShortDate(deadline),
                        style = MaterialTheme.typography.labelMedium,
                        color = sakuColors.textSecondary,
                    )
                }
            }

            if (trailingAction != null) {
                Spacer(modifier = Modifier.height(SakuSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    trailingAction()
                }
            }
        }
    }
}

@Composable
fun ProtectionBadge(mode: ProtectionMode) {
    val sakuColors = LocalSakuColors.current
    val (bg, fg) = when (mode) {
        ProtectionMode.STANDARD -> MaterialTheme.colorScheme.surfaceVariant to sakuColors.textSecondary
        ProtectionMode.PROTECTED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        ProtectionMode.STRICT -> sakuColors.warningContainer to sakuColors.warning
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = GoalVisuals.protectionIcon(mode),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(GoalVisuals.protectionLabelRes(mode)),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }
}
