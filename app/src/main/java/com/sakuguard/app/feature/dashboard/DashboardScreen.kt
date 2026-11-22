package com.sakuguard.app.feature.dashboard

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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sakuguard.app.core.designsystem.SakuHeroBalanceCard
import com.sakuguard.app.core.designsystem.SakuKpiPairCard
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.usecase.DashboardGoalItem
import com.sakuguard.app.domain.usecase.TransactionFeedItem
import com.sakuguard.app.feature.goals.GoalVisuals
import com.sakuguard.app.feature.goals.ProtectionBadge
import com.sakuguard.app.feature.transactions.CategoryVisuals
import com.sakuguard.app.feature.wallets.WalletVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenWallets: () -> Unit = {},
    onWalletClick: (String) -> Unit = {},
    onAddWallet: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onGoalClick: (String) -> Unit = {},
    onAddGoal: () -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    onTransactionClick: (String) -> Unit = {},
    onAddTransaction: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenBackupRestore: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val masked = stringResource(R.string.dashboard_masked_amount)
    val formatMoney: (Long) -> String = { amount ->
        if (uiState.amountsHidden) masked else CurrencyFormatter.formatIdr(amount)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(onClick = onOpenBudgets) {
                        Text(text = stringResource(R.string.dashboard_budgets_cta))
                    }
                    IconButton(onClick = onOpenBackupRestore) {
                        Icon(
                            imageVector = Icons.Outlined.Backup,
                            contentDescription = stringResource(R.string.backup_title),
                        )
                    }
                },
                expandedHeight = 48.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            !uiState.snapshot.hasAnyData -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding(), bottom = LocalSakuBottomBarPadding.current + 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyDestinationScaffold(
                        icon = Icons.Outlined.Home,
                        titleRes = R.string.empty_home_title,
                        bodyRes = R.string.empty_home_body,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.height(SakuSpacing.lg))
                    Button(onClick = onAddWallet) {
                        Text(stringResource(R.string.dashboard_create_wallet_cta))
                    }
                }
            }

            else -> {
                val snapshot = uiState.snapshot
                val sakuColors = LocalSakuColors.current

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    contentPadding = PaddingValues(
                        start = SakuSpacing.lg,
                        end = SakuSpacing.lg,
                        top = SakuSpacing.sm,
                        bottom = LocalSakuBottomBarPadding.current + 36.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    item {
                        SakuHeroBalanceCard(
                            title = stringResource(R.string.wallets_total_assets),
                            amountText = formatMoney(snapshot.overview.totalAssets),
                            subtitle = "${stringResource(R.string.wallets_allocated_money)}: ${
                                formatMoney(snapshot.overview.allocatedFunds)
                            }",
                            trailingAction = {
                                IconButton(
                                    onClick = viewModel::togglePrivacy,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = if (uiState.amountsHidden) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = stringResource(R.string.dashboard_toggle_privacy),
                                        tint = sakuColors.onHero,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }

                    item {
                        SakuKpiPairCard(
                            leftLabel = stringResource(R.string.wallets_free_money),
                            leftAmount = formatMoney(snapshot.overview.freeMoney),
                            leftIcon = Icons.Outlined.Savings,
                            leftTint = sakuColors.income,
                            leftContainer = sakuColors.incomeContainer,
                            rightLabel = stringResource(R.string.goals_protected_funds),
                            rightAmount = formatMoney(snapshot.overview.protectedFunds),
                            rightIcon = Icons.Outlined.Lock,
                            rightTint = MaterialTheme.colorScheme.primary,
                            rightContainer = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.dashboard_monthly_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = SakuSpacing.xs),
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                            SakuKpiPairCard(
                                leftLabel = stringResource(R.string.dashboard_monthly_income),
                                leftAmount = formatMoney(snapshot.monthlyActivity.income),
                                leftIcon = Icons.Outlined.SouthWest,
                                leftTint = sakuColors.income,
                                leftContainer = sakuColors.incomeContainer,
                                rightLabel = stringResource(R.string.dashboard_monthly_expenses),
                                rightAmount = formatMoney(snapshot.monthlyActivity.expenses),
                                rightIcon = Icons.Outlined.NorthEast,
                                rightTint = sakuColors.expense,
                                rightContainer = sakuColors.expenseContainer,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = SakuSpacing.lg, vertical = SakuSpacing.md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_net_cash_flow),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = sakuColors.textSecondary,
                                    )
                                    val net = snapshot.monthlyActivity.netCashFlow
                                    Text(
                                        text = formatMoney(net),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (net >= 0L) sakuColors.income else sakuColors.expense,
                                    )
                                }
                            }
                        }
                    }

                    // Wallets Section
                    item {
                        SectionHeader(
                            title = stringResource(R.string.wallets_active_section),
                            onViewAll = onOpenWallets,
                        )
                    }

                    if (snapshot.activeWallets.isEmpty()) {
                        item {
                            SectionEmptyCard(
                                message = stringResource(R.string.dashboard_no_active_wallets),
                                actionLabel = stringResource(R.string.wallets_add_button),
                                onAction = onAddWallet,
                            )
                        }
                    } else {
                        items(snapshot.activeWallets.take(4), key = { "dw-${it.wallet.id}" }) { summary ->
                            DashboardWalletRow(
                                summary = summary,
                                formatMoney = formatMoney,
                                onClick = { onWalletClick(summary.wallet.id) },
                            )
                        }
                    }

                    // Goals Section
                    item {
                        SectionHeader(
                            title = stringResource(R.string.goals_active_section),
                            onViewAll = onOpenGoals,
                        )
                    }

                    if (snapshot.activeGoals.isEmpty()) {
                        item {
                            SectionEmptyCard(
                                message = stringResource(R.string.dashboard_no_active_goals),
                                actionLabel = stringResource(R.string.goals_add_button),
                                onAction = onAddGoal,
                            )
                        }
                    } else {
                        items(snapshot.activeGoals.take(3), key = { "dg-${it.summary.goal.id}" }) { item ->
                            DashboardGoalRow(
                                item = item,
                                formatMoney = formatMoney,
                                onClick = { onGoalClick(item.summary.goal.id) },
                            )
                        }
                    }

                    // Recent Transactions Section
                    item {
                        SectionHeader(
                            title = stringResource(R.string.dashboard_recent_transactions),
                            onViewAll = onOpenTransactions,
                        )
                    }

                    if (snapshot.recentTransactions.isEmpty()) {
                        item {
                            SectionEmptyCard(
                                message = stringResource(R.string.dashboard_no_recent_tx),
                                actionLabel = stringResource(R.string.tx_add_button),
                                onAction = onAddTransaction,
                            )
                        }
                    } else {
                        items(snapshot.recentTransactions, key = { "dt-${it.transaction.id}" }) { item ->
                            DashboardTransactionRow(
                                item = item,
                                amountsHidden = uiState.amountsHidden,
                                maskedText = masked,
                                onClick = { onTransactionClick(item.transaction.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onViewAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = onViewAll) {
            Text(stringResource(R.string.dashboard_view_all))
        }
    }
}

@Composable
private fun SectionEmptyCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakuSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalSakuColors.current.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SakuSpacing.sm))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DashboardWalletRow(
    summary: WalletSummary,
    formatMoney: (Long) -> String,
    onClick: () -> Unit,
) {
    val wallet = summary.wallet
    val accent = WalletVisuals.resolveColor(wallet.colorHex)
    val sakuColors = LocalSakuColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakuSpacing.lg, vertical = SakuSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = WalletVisuals.resolveIcon(wallet.iconKey, wallet.type),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(SakuSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wallet.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${stringResource(R.string.wallets_free_money)}: ${formatMoney(summary.freeMoney)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = sakuColors.income,
                )
            }
            Text(
                text = formatMoney(summary.currentBalance),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun DashboardGoalRow(
    item: DashboardGoalItem,
    formatMoney: (Long) -> String,
    onClick: () -> Unit,
) {
    val goal = item.summary.goal
    val accent = GoalVisuals.resolveColor(goal.colorHex)
    val percent = item.progressPermille / 10

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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(SakuSpacing.sm))
                ProtectionBadge(goal.protectionMode)
            }
            Spacer(Modifier.height(SakuSpacing.xs))
            Text(
                text = stringResource(
                    R.string.goal_target_of_format,
                    formatMoney(item.summary.allocatedAmount),
                    formatMoney(goal.targetAmount),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSakuColors.current.textSecondary,
            )
            Spacer(Modifier.height(SakuSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { item.progressPermille / 1000f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(SakuSpacing.sm))
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun DashboardTransactionRow(
    item: TransactionFeedItem,
    amountsHidden: Boolean,
    maskedText: String,
    onClick: () -> Unit,
) {
    val tx = item.transaction
    val sakuColors = LocalSakuColors.current

    val (icon, tint, container) = when (tx.type) {
        TransactionType.INCOME -> Triple(Icons.Outlined.SouthWest, sakuColors.income, sakuColors.incomeContainer)
        TransactionType.EXPENSE -> Triple(Icons.Outlined.NorthEast, sakuColors.expense, sakuColors.expenseContainer)
        TransactionType.TRANSFER -> Triple(
            Icons.Outlined.SwapHoriz,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
        )
        TransactionType.GOAL_ALLOCATION,
        TransactionType.GOAL_WITHDRAWAL -> Triple(
            Icons.Outlined.Savings,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer,
        )
    }

    val titleText = when (tx.type) {
        TransactionType.TRANSFER -> "${item.wallet?.name ?: "?"} → ${item.destinationWallet?.name ?: "?"}"
        TransactionType.GOAL_ALLOCATION -> stringResource(R.string.tx_type_goal_allocation)
        TransactionType.GOAL_WITHDRAWAL -> stringResource(R.string.tx_type_goal_withdrawal)
        else -> item.category?.let { CategoryVisuals.localizedName(it) } ?: ""
    }

    val amountText = if (amountsHidden) {
        maskedText
    } else {
        when (tx.type) {
            TransactionType.INCOME -> "+${CurrencyFormatter.formatIdr(tx.amount)}"
            TransactionType.EXPENSE -> "-${CurrencyFormatter.formatIdr(tx.amount)}"
            else -> CurrencyFormatter.formatIdr(tx.amount)
        }
    }

    val amountColor = when (tx.type) {
        TransactionType.INCOME -> sakuColors.income
        TransactionType.EXPENSE -> sakuColors.expense
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakuSpacing.lg, vertical = SakuSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(SakuSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.wallet?.name ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = sakuColors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = amountColor,
                )
                Text(
                    text = DateFormatter.formatShortDate(tx.transactionDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = sakuColors.textSecondary,
                )
            }
        }
    }
}
