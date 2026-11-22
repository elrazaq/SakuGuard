package com.sakuguard.app.feature.wallets

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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
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
import com.sakuguard.app.domain.model.WalletSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(
    onCreateWallet: () -> Unit = {},
    onWalletClick: (String) -> Unit = {},
    viewModel: WalletsViewModel = hiltViewModel(),
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
                        text = stringResource(R.string.wallets_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateWallet,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.wallets_add_button),
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

            uiState.activeWallets.isEmpty() && uiState.archivedWallets.isEmpty() -> {
                EmptyDestinationScaffold(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    titleRes = R.string.empty_wallets_title,
                    bodyRes = R.string.empty_wallets_body,
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
                            title = stringResource(R.string.wallets_total_assets),
                            amountText = CurrencyFormatter.formatIdr(uiState.overview.totalAssets),
                            subtitle = stringResource(
                                R.string.wallets_active_count_format,
                                uiState.activeWallets.size,
                            ),
                        )
                    }

                    item {
                        SakuKpiPairCard(
                            leftLabel = stringResource(R.string.wallets_free_money),
                            leftAmount = CurrencyFormatter.formatIdr(uiState.overview.freeMoney),
                            leftIcon = Icons.Outlined.Savings,
                            leftTint = sakuColors.income,
                            leftContainer = sakuColors.incomeContainer,
                            rightLabel = stringResource(R.string.wallets_allocated_money),
                            rightAmount = CurrencyFormatter.formatIdr(uiState.overview.allocatedFunds),
                            rightIcon = Icons.Outlined.Lock,
                            rightTint = MaterialTheme.colorScheme.primary,
                            rightContainer = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }

                    if (uiState.activeWallets.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.wallets_active_section),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = SakuSpacing.xs),
                            )
                        }
                        items(
                            items = uiState.activeWallets,
                            key = { it.wallet.id },
                        ) { summary ->
                            WalletSummaryCard(
                                summary = summary,
                                onClick = { onWalletClick(summary.wallet.id) },
                            )
                        }
                    }

                    if (uiState.archivedWallets.isNotEmpty()) {
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
                                        R.string.wallets_archived_section,
                                        uiState.archivedWallets.size,
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
                            items(
                                items = uiState.archivedWallets,
                                key = { it.wallet.id },
                            ) { summary ->
                                WalletSummaryCard(
                                    summary = summary,
                                    onClick = { onWalletClick(summary.wallet.id) },
                                    trailingAction = {
                                        TextButton(onClick = { viewModel.restoreWallet(summary.wallet.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Restore,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = stringResource(R.string.wallets_restore_action))
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
private fun WalletSummaryCard(
    summary: WalletSummary,
    onClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
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
                        imageVector = WalletVisuals.resolveIcon(wallet.iconKey, wallet.type),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(SakuSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(WalletVisuals.typeLabelRes(wallet.type)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakuColors.textSecondary,
                    )
                }
                Text(
                    text = CurrencyFormatter.formatIdr(summary.currentBalance),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(SakuSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stringResource(R.string.wallets_free_money)}: ${CurrencyFormatter.formatIdr(summary.freeMoney)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = sakuColors.income,
                )
                Text(
                    text = "${stringResource(R.string.wallets_allocated_money)}: ${CurrencyFormatter.formatIdr(summary.allocatedFunds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = sakuColors.textSecondary,
                )
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
