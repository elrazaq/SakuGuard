package com.sakuguard.app.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.sakuguard.app.domain.model.CategoryBreakdownItem
import com.sakuguard.app.domain.model.ReportPeriodPreset
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.WalletActivityItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenCategoryDetail: (TransactionType, String?, Long, Long) -> Unit = { _, _, _, _ -> },
    onOpenBudgets: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maskedAmount = stringResource(R.string.dashboard_masked_amount)
    var pickingCustomStart by remember { mutableStateOf(false) }
    var pickingCustomEnd by remember { mutableStateOf(false) }
    var pendingStartMillis by remember { mutableStateOf(uiState.customStartMillis) }

    if (pickingCustomStart) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateFormatter.localMillisToUtcDatePickerMillis(uiState.customStartMillis),
        )
        DatePickerDialog(
            onDismissRequest = { pickingCustomStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            pendingStartMillis = DateFormatter.utcDatePickerMillisToLocalStartOfDay(it)
                            pickingCustomStart = false
                            pickingCustomEnd = true
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingCustomStart = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state, title = {
                Text(
                    text = stringResource(R.string.reports_custom_start),
                    modifier = Modifier.padding(SakuSpacing.lg),
                )
            })
        }
    }

    if (pickingCustomEnd) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateFormatter.localMillisToUtcDatePickerMillis(uiState.customEndInclusiveMillis),
        )
        DatePickerDialog(
            onDismissRequest = { pickingCustomEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            val endMillis = DateFormatter.utcDatePickerMillisToLocalStartOfDay(it)
                            pickingCustomEnd = false
                            viewModel.onCustomDateRangeSelect(pendingStartMillis, endMillis)
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingCustomEnd = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state, title = {
                Text(
                    text = stringResource(R.string.reports_custom_end),
                    modifier = Modifier.padding(SakuSpacing.lg),
                )
            })
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.reports_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::togglePrivacy) {
                        Icon(
                            imageVector = if (uiState.amountsHidden) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(R.string.dashboard_toggle_privacy),
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
        val snapshot = uiState.snapshot
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PeriodSelectorRow(
                selectedPreset = uiState.selectedPreset,
                onSelectPreset = { preset ->
                    if (preset == ReportPeriodPreset.CUSTOM) {
                        pickingCustomStart = true
                    } else {
                        viewModel.onPresetSelect(preset)
                    }
                },
            )

            when {
                uiState.isLoading || snapshot == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !snapshot.hasAnyData && snapshot.budgetPerformance.budgets.isEmpty() && snapshot.goalSummary.goals.isEmpty() -> {
                    EmptyDestinationScaffold(
                        icon = Icons.Outlined.BarChart,
                        titleRes = R.string.reports_empty_period_title,
                        bodyRes = R.string.reports_empty_period_body,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SakuSpacing.lg,
                            end = SakuSpacing.lg,
                            top = SakuSpacing.sm,
                            bottom = LocalSakuBottomBarPadding.current + SakuSpacing.xl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                    ) {
                        // Date range subtitle
                        item(key = "period_caption") {
                            val endInclusive = (snapshot.periodRange.endExclusive - 1L)
                                .coerceAtLeast(snapshot.periodRange.startInclusive)
                            Text(
                                text = "${DateFormatter.formatShortDate(snapshot.periodRange.startInclusive)} – ${DateFormatter.formatShortDate(endInclusive)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // A. Financial Overview
                        item(key = "overview") {
                            FinancialOverviewCard(
                                totalIncome = snapshot.totalIncome,
                                totalExpense = snapshot.totalExpense,
                                netCashFlow = snapshot.netCashFlow,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                            )
                        }

                        // B. Income vs Expense (multi-month)
                        if (snapshot.cashFlowSeries.isNotEmpty()) {
                            item(key = "income_vs_expense") {
                                ReportCard(title = stringResource(R.string.reports_income_vs_expense_title)) {
                                    IncomeVsExpenseBarChart(
                                        buckets = snapshot.cashFlowSeries,
                                        amountsHidden = uiState.amountsHidden,
                                        maskedAmount = maskedAmount,
                                        incomeLabel = stringResource(R.string.reports_total_income),
                                        expenseLabel = stringResource(R.string.reports_total_expense),
                                    )
                                }
                            }
                        }

                        // E. Spending Trend
                        item(key = "spending_trend") {
                            SpendingTrendCard(
                                snapshot = snapshot,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                            )
                        }

                        // C. Expense Breakdown
                        item(key = "expense_breakdown") {
                            CategoryBreakdownCard(
                                title = stringResource(R.string.reports_expense_breakdown_title),
                                items = snapshot.expenseBreakdown,
                                isExpense = true,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                                onCategoryClick = { catId ->
                                    onOpenCategoryDetail(
                                        TransactionType.EXPENSE,
                                        catId,
                                        snapshot.periodRange.startInclusive,
                                        snapshot.periodRange.endExclusive,
                                    )
                                },
                            )
                        }

                        // D. Income Breakdown
                        item(key = "income_breakdown") {
                            CategoryBreakdownCard(
                                title = stringResource(R.string.reports_income_breakdown_title),
                                items = snapshot.incomeBreakdown,
                                isExpense = false,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                                onCategoryClick = { catId ->
                                    onOpenCategoryDetail(
                                        TransactionType.INCOME,
                                        catId,
                                        snapshot.periodRange.startInclusive,
                                        snapshot.periodRange.endExclusive,
                                    )
                                },
                            )
                        }

                        // F. Wallet Activity
                        item(key = "wallet_activity") {
                            WalletActivityCard(
                                items = snapshot.walletActivities,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                            )
                        }

                        // G. Budget Performance
                        item(key = "budget_performance") {
                            BudgetPerformanceCard(
                                performance = snapshot.budgetPerformance,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                                onManageBudgets = onOpenBudgets,
                            )
                        }

                        // H. Savings Goals
                        item(key = "savings_goals") {
                            SavingsGoalsReportCard(
                                goalSummary = snapshot.goalSummary,
                                amountsHidden = uiState.amountsHidden,
                                maskedAmount = maskedAmount,
                                onViewGoals = onOpenGoals,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelectorRow(
    selectedPreset: ReportPeriodPreset,
    onSelectPreset: (ReportPeriodPreset) -> Unit,
) {
    val options = listOf(
        ReportPeriodPreset.THIS_MONTH to R.string.reports_period_this_month,
        ReportPeriodPreset.LAST_MONTH to R.string.reports_period_last_month,
        ReportPeriodPreset.LAST_3_MONTHS to R.string.reports_period_3_months,
        ReportPeriodPreset.LAST_6_MONTHS to R.string.reports_period_6_months,
        ReportPeriodPreset.LAST_12_MONTHS to R.string.reports_period_12_months,
        ReportPeriodPreset.CUSTOM to R.string.reports_period_custom,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SakuSpacing.lg, vertical = SakuSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
    ) {
        options.forEach { (preset, labelRes) ->
            FilterChip(
                selected = selectedPreset == preset,
                onClick = { onSelectPreset(preset) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun FinancialOverviewCard(
    totalIncome: Long,
    totalExpense: Long,
    netCashFlow: Long,
    amountsHidden: Boolean,
    maskedAmount: String,
) {
    val sakuColors = LocalSakuColors.current
    ReportCard(title = stringResource(R.string.reports_overview_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reports_total_income),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(totalIncome),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = sakuColors.income,
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.reports_total_expense),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(totalExpense),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = sakuColors.expense,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reports_net_cash_flow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val netColor = if (netCashFlow >= 0L) sakuColors.income else sakuColors.expense
            val prefix = if (netCashFlow > 0L) "+" else ""
            Text(
                text = if (amountsHidden) maskedAmount else "$prefix${CurrencyFormatter.formatIdr(netCashFlow)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = netColor,
            )
        }
    }
}

@Composable
private fun SpendingTrendCard(
    snapshot: com.sakuguard.app.domain.model.ReportsSnapshot,
    amountsHidden: Boolean,
    maskedAmount: String,
) {
    val sakuColors = LocalSakuColors.current
    val trend = snapshot.spendingTrend
    val comparisonLabel = if (snapshot.periodRange.preset == ReportPeriodPreset.THIS_MONTH) {
        stringResource(R.string.reports_trend_vs_last_month)
    } else {
        stringResource(R.string.reports_trend_vs_previous)
    }

    ReportCard(title = stringResource(R.string.reports_spending_trend_title)) {
        val deltaColor = when {
            trend.absoluteChange > 0L -> sakuColors.expense
            trend.absoluteChange < 0L -> sakuColors.income
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val pctPermille = trend.percentageChangePermille
        val deltaText = when {
            amountsHidden -> maskedAmount
            pctPermille == null && trend.currentExpense == 0L && trend.previousExpense == 0L ->
                stringResource(R.string.reports_trend_no_baseline)
            pctPermille == null -> {
                val sign = if (trend.absoluteChange > 0L) "+" else ""
                "$sign${CurrencyFormatter.formatIdr(trend.absoluteChange)} (${stringResource(R.string.reports_trend_no_baseline)})"
            }
            else -> {
                val sign = if (trend.absoluteChange > 0L) "+" else ""
                val pctWhole = pctPermille / 10.0
                "$sign${CurrencyFormatter.formatIdr(trend.absoluteChange)} (${sign}${"%.1f".format(pctWhole)}% $comparisonLabel)"
            }
        }
        Text(
            text = deltaText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = deltaColor,
        )
        if (trend.buckets.size >= 2 && (trend.currentExpense > 0L || trend.buckets.any { it.expense > 0L })) {
            SpendingTrendLineChart(buckets = trend.buckets)
        } else {
            Text(
                text = stringResource(R.string.reports_trend_insufficient),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    title: String,
    items: List<CategoryBreakdownItem>,
    isExpense: Boolean,
    amountsHidden: Boolean,
    maskedAmount: String,
    onCategoryClick: (String?) -> Unit,
) {
    val sakuColors = LocalSakuColors.current
    val fallbackColor = if (isExpense) sakuColors.expense else sakuColors.income

    ReportCard(title = title) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_breakdown_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CategoryProportionBar(items = items, fallbackColor = fallbackColor)
            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                items.forEach { item ->
                    val dotColor = item.colorHex?.let { Color(it) } ?: fallbackColor
                    val labelBase = item.categoryName ?: stringResource(R.string.reports_uncategorized)
                    val label = if (item.isArchived) {
                        "$labelBase (${stringResource(R.string.reports_archived_tag)})"
                    } else {
                        labelBase
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onCategoryClick(item.categoryId) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SakuSpacing.xs),
                        ) {
                            Text(
                                text = if (amountsHidden) {
                                    maskedAmount
                                } else {
                                    "${CurrencyFormatter.formatIdr(item.amount)} (${item.percentagePermille / 10}%)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletActivityCard(
    items: List<WalletActivityItem>,
    amountsHidden: Boolean,
    maskedAmount: String,
) {
    val sakuColors = LocalSakuColors.current
    ReportCard(title = stringResource(R.string.reports_wallet_activity_title)) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_wallet_activity_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.md)) {
                items.forEachIndexed { idx, item ->
                    if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val name = if (item.wallet.isArchived) {
                            "${item.wallet.name} (${stringResource(R.string.reports_archived_tag)})"
                        } else {
                            item.wallet.name
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${stringResource(R.string.reports_total_income)}: ${if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(item.income)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = sakuColors.income,
                            )
                            Text(
                                text = "${stringResource(R.string.reports_total_expense)}: ${if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(item.expense)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = sakuColors.expense,
                            )
                        }
                        if (item.transferIn > 0L || item.transferOut > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${stringResource(R.string.reports_wallet_transfer_in)}: ${if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(item.transferIn)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${stringResource(R.string.reports_wallet_transfer_out)}: ${if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(item.transferOut)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun BudgetPerformanceCard(
    performance: com.sakuguard.app.domain.model.BudgetPerformanceSummary,
    amountsHidden: Boolean,
    maskedAmount: String,
    onManageBudgets: () -> Unit,
) {
    val sakuColors = LocalSakuColors.current
    val title = if (performance.isCurrentCycleFallback) {
        stringResource(R.string.reports_budget_perf_current_cycle)
    } else {
        stringResource(R.string.reports_budget_perf_title)
    }

    ReportCard(
        title = title,
        actionLabel = stringResource(R.string.reports_manage_budgets),
        onAction = onManageBudgets,
    ) {
        if (performance.isHistoricalMonth) {
            Text(
                text = stringResource(R.string.reports_budget_historical_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (performance.budgets.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_budget_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SakuSpacing.md),
            ) {
                Text(
                    text = "${stringResource(R.string.budget_status_on_track)}: ${performance.onTrackCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sakuColors.income,
                )
                Text(
                    text = "${stringResource(R.string.budget_status_warning)}: ${performance.warningCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sakuColors.warning,
                )
                Text(
                    text = "${stringResource(R.string.budget_status_exceeded)}: ${performance.exceededCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                performance.budgets.take(5).forEach { summary ->
                    val statusColor = when (summary.healthStatus) {
                        BudgetHealthStatus.ON_TRACK -> sakuColors.income
                        BudgetHealthStatus.WARNING -> sakuColors.warning
                        BudgetHealthStatus.EXCEEDED -> MaterialTheme.colorScheme.error
                    }
                    val progress = (summary.utilizationPermille / 1000f).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = summary.budget.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (amountsHidden) {
                                    maskedAmount
                                } else {
                                    "${CurrencyFormatter.formatIdr(summary.spentAmount)} / ${CurrencyFormatter.formatIdr(summary.budget.limitAmount)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.small),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsGoalsReportCard(
    goalSummary: com.sakuguard.app.domain.model.GoalPeriodSummary,
    amountsHidden: Boolean,
    maskedAmount: String,
    onViewGoals: () -> Unit,
) {
    ReportCard(
        title = stringResource(R.string.reports_goals_title),
        actionLabel = stringResource(R.string.reports_view_goals),
        onAction = onViewGoals,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reports_goals_allocated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(goalSummary.totalAllocated),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.reports_goals_protected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(goalSummary.protectedFunds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.reports_goals_period_in,
                    if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(goalSummary.periodAllocated),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reports_goals_completed, goalSummary.completedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (goalSummary.periodWithdrawn > 0L) {
            Text(
                text = stringResource(
                    R.string.reports_goals_period_out,
                    if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(goalSummary.periodWithdrawn),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
