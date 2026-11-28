package com.sakuguard.app.feature.wallets

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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.sakuguard.app.core.designsystem.SakuKpiPairCard
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: WalletDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deletedSuccessfully) {
        if (uiState.deletedSuccessfully) {
            onBack()
        }
    }

    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    val detail = uiState.detail

    if (showArchiveConfirm && detail != null) {
        val isArchived = detail.summary.wallet.isArchived
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = {
                Text(
                    stringResource(
                        if (isArchived) R.string.wallets_restore_confirm_title
                        else R.string.wallets_archive_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isArchived) R.string.wallets_restore_confirm_body
                        else R.string.wallets_archive_confirm_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveConfirm = false
                        viewModel.setArchived(!isArchived)
                    },
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm && detail != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.wallets_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wallets_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteWallet()
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallets_detail_title)) },
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
                        IconButton(onClick = { onEdit(detail.summary.wallet.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.wallets_edit_action),
                            )
                        }
                        IconButton(onClick = { showArchiveConfirm = true }) {
                            Icon(
                                imageVector = if (detail.summary.wallet.isArchived) {
                                    Icons.Outlined.Restore
                                } else {
                                    Icons.Outlined.Archive
                                },
                                contentDescription = stringResource(
                                    if (detail.summary.wallet.isArchived) {
                                        R.string.wallets_restore_action
                                    } else {
                                        R.string.wallets_archive_action
                                    },
                                ),
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.common_delete),
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

            detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.wallet_error_generic))
                }
            }

            else -> {
                val wallet = detail.summary.wallet
                val accent = WalletVisuals.resolveColor(wallet.colorHex)
                val sakuColors = LocalSakuColors.current

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
                                        imageVector = WalletVisuals.resolveIcon(wallet.iconKey, wallet.type),
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(SakuSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = wallet.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(WalletVisuals.typeLabelRes(wallet.type)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = sakuColors.textSecondary,
                                    )
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (wallet.isArchived) {
                                        sakuColors.warningContainer
                                    } else {
                                        sakuColors.incomeContainer
                                    },
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (wallet.isArchived) R.string.wallets_status_archived
                                            else R.string.wallets_status_active,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (wallet.isArchived) sakuColors.warning else sakuColors.income,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SakuHeroBalanceCard(
                            title = stringResource(R.string.wallets_current_balance),
                            amountText = CurrencyFormatter.formatIdr(detail.summary.currentBalance),
                            subtitle = "${stringResource(R.string.wallets_opening_balance)}: ${
                                CurrencyFormatter.formatIdr(wallet.openingBalance)
                            }",
                        )
                    }

                    item {
                        SakuKpiPairCard(
                            leftLabel = stringResource(R.string.wallets_free_money),
                            leftAmount = CurrencyFormatter.formatIdr(detail.summary.freeMoney),
                            leftIcon = Icons.Outlined.Savings,
                            leftTint = sakuColors.income,
                            leftContainer = sakuColors.incomeContainer,
                            rightLabel = stringResource(R.string.wallets_allocated_money),
                            rightAmount = CurrencyFormatter.formatIdr(detail.summary.allocatedFunds),
                            rightIcon = Icons.Outlined.Lock,
                            rightTint = MaterialTheme.colorScheme.primary,
                            rightContainer = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }

                    if (wallet.description.isNotBlank()) {
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
                                        text = stringResource(R.string.wallets_description_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = sakuColors.textSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(SakuSpacing.xs))
                                    Text(
                                        text = wallet.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.wallets_goal_breakdown_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = SakuSpacing.xs),
                        )
                    }

                    if (detail.goalAllocations.isEmpty()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                            ) {
                                Text(
                                    text = stringResource(R.string.wallets_no_goal_allocations),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sakuColors.textSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SakuSpacing.lg),
                                )
                            }
                        }
                    } else {
                        items(
                            items = detail.goalAllocations,
                            key = { it.goal.id },
                        ) { item ->
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
                                        text = item.goal.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatIdr(item.allocatedAmount),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
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
