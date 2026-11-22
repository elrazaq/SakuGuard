package com.sakuguard.app.feature.transactions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.LocalSakuColors
import com.sakuguard.app.core.designsystem.SakuHeroBalanceCard
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deletedSuccessfully) {
        if (uiState.deletedSuccessfully) onBack()
    }

    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearError()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.tx_delete_confirm_title)) },
            text = { Text(stringResource(R.string.tx_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTransaction()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
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
                title = { Text(stringResource(R.string.tx_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (uiState.canEditOrDelete && uiState.item != null) {
                        IconButton(onClick = { onEdit(uiState.item!!.transaction.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.wallets_edit_action),
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
        val item = uiState.item
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

            item == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.tx_error_generic))
                }
            }

            else -> {
                val tx = item.transaction
                val typeLabelRes = when (tx.type) {
                    TransactionType.INCOME -> R.string.tx_type_income
                    TransactionType.EXPENSE -> R.string.tx_type_expense
                    TransactionType.TRANSFER -> R.string.tx_type_transfer
                    TransactionType.GOAL_ALLOCATION -> R.string.tx_type_goal_allocation
                    TransactionType.GOAL_WITHDRAWAL -> R.string.tx_type_goal_withdrawal
                }
                val signedAmount = when (tx.type) {
                    TransactionType.INCOME -> "+${CurrencyFormatter.formatIdr(tx.amount)}"
                    TransactionType.EXPENSE -> "-${CurrencyFormatter.formatIdr(tx.amount)}"
                    else -> CurrencyFormatter.formatIdr(tx.amount)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(SakuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    item {
                        SakuHeroBalanceCard(
                            title = stringResource(typeLabelRes),
                            amountText = signedAmount,
                            subtitle = DateFormatter.formatShortDate(tx.transactionDate),
                        )
                    }

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
                                verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                            ) {
                                if (tx.type == TransactionType.TRANSFER) {
                                    DetailRow(
                                        label = stringResource(R.string.tx_source_wallet_label),
                                        value = item.wallet?.name ?: "?",
                                    )
                                    DetailRow(
                                        label = stringResource(R.string.tx_destination_wallet_label),
                                        value = item.destinationWallet?.name ?: "?",
                                    )
                                } else {
                                    DetailRow(
                                        label = stringResource(R.string.tx_wallet_label),
                                        value = item.wallet?.name ?: "?",
                                    )
                                    item.category?.let { cat ->
                                        DetailRow(
                                            label = stringResource(R.string.tx_category_label),
                                            value = CategoryVisuals.localizedName(cat),
                                        )
                                    }
                                }
                                DetailRow(
                                    label = stringResource(R.string.tx_date_label),
                                    value = DateFormatter.formatShortDate(tx.transactionDate),
                                )
                                val noteText = tx.notes.ifBlank { tx.description }
                                if (noteText.isNotBlank()) {
                                    DetailRow(
                                        label = stringResource(R.string.tx_note_label),
                                        value = noteText,
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

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalSakuColors.current.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
