package com.sakuguard.app.feature.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.sakuguard.app.core.designsystem.SakuHeroBalanceCard
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.WithdrawalTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAllocate: (String) -> Unit,
    onWithdraw: (String) -> Unit,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(R.string.goal_archive_confirm_title)) },
            text = { Text(stringResource(R.string.goal_archive_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveDialog = false
                        viewModel.archiveGoal()
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text(stringResource(R.string.goal_complete_confirm_title)) },
            text = { Text(stringResource(R.string.goal_complete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCompleteDialog = false
                        viewModel.markCompleted()
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val detail = uiState.detail

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (detail != null) {
                        val goal = detail.summary.goal
                        if (goal.status != GoalStatus.ARCHIVED) {
                            IconButton(onClick = { onEdit(goal.id) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.wallets_edit_action))
                            }
                            if (goal.status == GoalStatus.ACTIVE) {
                                IconButton(onClick = { showCompleteDialog = true }) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.goal_complete_action))
                                }
                            } else {
                                IconButton(onClick = { viewModel.reopenGoal() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.goal_reopen_action))
                                }
                            }
                            IconButton(onClick = { showArchiveDialog = true }) {
                                Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.goal_archive_action))
                            }
                        } else {
                            IconButton(onClick = { viewModel.restoreGoal() }) {
                                Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.goal_restore_action))
                            }
                        }
                    }
                },
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
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.goal_error_generic)) }
            }

            else -> {
                val goal = detail.summary.goal
                val accent = GoalVisuals.resolveColor(goal.colorHex)
                val sakuColors = LocalSakuColors.current
                val percent = detail.progressPermille / 10

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(SakuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(SakuSpacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = GoalVisuals.resolveIcon(goal.iconKey),
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(SakuSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(goal.name, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = stringResource(GoalVisuals.statusLabelRes(goal.status)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = sakuColors.textSecondary,
                                    )
                                }
                                ProtectionBadge(goal.protectionMode)
                            }
                        }
                    }

                    item {
                        SakuHeroBalanceCard(
                            title = stringResource(
                                R.string.goal_target_of_format,
                                CurrencyFormatter.formatIdr(detail.summary.allocatedAmount),
                                CurrencyFormatter.formatIdr(goal.targetAmount),
                            ),
                            amountText = CurrencyFormatter.formatIdr(detail.summary.allocatedAmount),
                            subtitle = "${stringResource(R.string.goal_remaining_format, CurrencyFormatter.formatIdr(detail.summary.remainingAmount))} · $percent%",
                        )
                    }

                    item {
                        LinearProgressIndicator(
                            progress = { detail.progressPermille / 1000f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    if (goal.status != GoalStatus.ARCHIVED) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                            ) {
                                if (goal.status == GoalStatus.ACTIVE) {
                                    Button(
                                        onClick = { onAllocate(goal.id) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.goal_allocate_action))
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onWithdraw(goal.id) },
                                    enabled = detail.summary.allocatedAmount > 0L,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.goal_withdraw_action))
                                }
                            }
                        }
                    }

                    if (goal.description.isNotBlank()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SakuSpacing.lg),
                                ) {
                                    Text(
                                        text = stringResource(R.string.goal_description_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = sakuColors.textSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(SakuSpacing.xs))
                                    Text(goal.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.goal_wallet_distribution_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = SakuSpacing.xs),
                        )
                    }

                    if (detail.walletShares.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Text(
                                    text = stringResource(R.string.goal_no_wallet_funds),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sakuColors.textSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SakuSpacing.lg),
                                )
                            }
                        }
                    } else {
                        items(detail.walletShares, key = { it.wallet.id }) { share ->
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
                                ) {
                                    Text(share.wallet.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = CurrencyFormatter.formatIdr(share.allocatedAmount),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.goal_history_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = SakuSpacing.xs),
                        )
                    }

                    if (detail.history.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Text(
                                    text = stringResource(R.string.goal_no_history),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sakuColors.textSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SakuSpacing.lg),
                                )
                            }
                        }
                    } else {
                        items(detail.history, key = { it.entry.id }) { item ->
                            val entry = item.entry
                            val isAlloc = entry.entryType == GoalLedgerType.ALLOCATION
                            val titleRes = when {
                                isAlloc -> R.string.goal_history_allocated
                                entry.withdrawalTarget == WithdrawalTarget.DIRECT_EXPENSE -> R.string.goal_history_withdrawn_expense
                                else -> R.string.goal_history_withdrawn_free
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SakuSpacing.lg),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isAlloc) sakuColors.incomeContainer else sakuColors.expenseContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isAlloc) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                                            contentDescription = null,
                                            tint = if (isAlloc) sakuColors.income else sakuColors.expense,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(SakuSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
                                        val sub = listOfNotNull(item.wallet?.name, entry.reason).joinToString(" · ")
                                        if (sub.isNotBlank()) {
                                            Text(sub, style = MaterialTheme.typography.bodySmall, color = sakuColors.textSecondary)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (isAlloc) "+${CurrencyFormatter.formatIdr(entry.amount)}" else "-${CurrencyFormatter.formatIdr(entry.amount)}",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isAlloc) sakuColors.income else sakuColors.expense,
                                        )
                                        Text(
                                            text = DateFormatter.formatShortDate(entry.createdAt),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = sakuColors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
