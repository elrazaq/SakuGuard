package com.sakuguard.app.feature.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onSaved()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateFormatter.localMillisToUtcDatePickerMillis(uiState.transactionDate),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            viewModel.onDateSelect(
                                DateFormatter.utcDatePickerMillisToLocalStartOfDay(utcMillis),
                            )
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditMode) R.string.tx_edit_title
                            else R.string.tx_create_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.activeWallets.isEmpty() && !uiState.isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(SakuSpacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tx_no_active_wallets),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        val isTransfer = uiState.selectedType == TransactionType.TRANSFER

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(SakuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SakuSpacing.lg),
        ) {
            // Transaction Type Selector (locked in Edit mode)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
            ) {
                listOf(
                    TransactionType.EXPENSE to R.string.tx_type_expense,
                    TransactionType.INCOME to R.string.tx_type_income,
                    TransactionType.TRANSFER to R.string.tx_type_transfer,
                ).forEach { (type, labelRes) ->
                    FilterChip(
                        selected = uiState.selectedType == type,
                        enabled = !uiState.isEditMode,
                        onClick = { viewModel.onTypeSelect(type) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            OutlinedTextField(
                value = uiState.amountInput,
                onValueChange = viewModel::onAmountChange,
                label = { Text(stringResource(R.string.tx_amount_label)) },
                prefix = { Text("Rp") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.amountErrorRes != null,
                supportingText = uiState.amountErrorRes?.let { { Text(stringResource(it)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Source / Primary Wallet Picker
            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                Text(
                    text = stringResource(
                        if (isTransfer) R.string.tx_source_wallet_label else R.string.tx_wallet_label,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs),
                ) {
                    uiState.activeWallets.forEach { wallet ->
                        FilterChip(
                            selected = uiState.selectedWalletId == wallet.id,
                            onClick = { viewModel.onWalletSelect(wallet.id) },
                            label = { Text(wallet.name) },
                        )
                    }
                }
                uiState.walletErrorRes?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Destination Wallet (Transfer) OR Category Picker (Income/Expense)
            if (isTransfer) {
                Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.tx_destination_wallet_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs),
                    ) {
                        uiState.activeWallets.forEach { wallet ->
                            FilterChip(
                                selected = uiState.selectedDestinationWalletId == wallet.id,
                                onClick = { viewModel.onDestinationWalletSelect(wallet.id) },
                                label = { Text(wallet.name) },
                            )
                        }
                    }
                    uiState.destinationWalletErrorRes?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.tx_category_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs),
                    ) {
                        uiState.availableCategories.forEach { category ->
                            FilterChip(
                                selected = uiState.selectedCategoryId == category.id,
                                onClick = { viewModel.onCategorySelect(category.id) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = CategoryVisuals.resolveIcon(category.iconKey),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text(CategoryVisuals.localizedName(category)) },
                            )
                        }
                    }
                    uiState.categoryErrorRes?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Date Picker Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakuSpacing.lg, vertical = SakuSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(SakuSpacing.md))
                    Column {
                        Text(
                            text = stringResource(R.string.tx_date_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = DateFormatter.formatShortDate(uiState.transactionDate),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.tx_note_label)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.submitErrorRes?.let { errorRes ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SakuSpacing.md),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SakuSpacing.xs))

            Button(
                onClick = viewModel::saveTransaction,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(text = stringResource(R.string.tx_save_button))
            }
        }
    }
}
