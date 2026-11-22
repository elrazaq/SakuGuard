package com.sakuguard.app.feature.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.LocalSakuColors
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.BudgetHealthStatus
import com.sakuguard.app.domain.model.BudgetPeriodType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: BudgetDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.deletedSuccessfully) {
        if (uiState.deletedSuccessfully) {
            onBack()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.budget_delete_confirm_title)) },
            text = { Text(stringResource(R.string.budget_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteBudget()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.budgets_detail_title),
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
                actions = {
                    uiState.summary?.let { summary ->
                        IconButton(onClick = { onEdit(summary.budget.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        val summary = uiState.summary
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
            summary == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.budget_error_generic))
                }
            }
            else -> {
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
                val endInclusive = (summary.effectiveEndExclusive - 1L).coerceAtLeast(summary.effectiveStart)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(SakuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.lg),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = summary.budget.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "${summary.category?.name ?: "-"} • $periodLabel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = statusColor.copy(alpha = 0.14f),
                                ) {
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(MaterialTheme.shapes.small),
                                color = statusColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(
                                            R.string.budget_spent_format,
                                            CurrencyFormatter.formatIdr(summary.spentAmount),
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${stringResource(R.string.budget_limit_label)}: ${CurrencyFormatter.formatIdr(summary.budget.limitAmount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                )
                            }

                            Text(
                                text = "${DateFormatter.formatShortDate(summary.effectiveStart)} – ${DateFormatter.formatShortDate(endInclusive)} (${summary.utilizationPermille / 10}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setArchived(!summary.budget.isArchived) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = if (summary.budget.isArchived) {
                                    Icons.Outlined.Unarchive
                                } else {
                                    Icons.Outlined.Archive
                                },
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(
                                text = stringResource(
                                    if (summary.budget.isArchived) R.string.action_restore else R.string.action_archive,
                                ),
                            )
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(text = stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}
