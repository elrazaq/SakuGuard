package com.sakuguard.app.feature.transactions

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.usecase.TransactionFeedItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onCreateTransaction: () -> Unit = {},
    onTransactionClick: (String) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tx_title),
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
                onClick = onCreateTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.tx_add_button),
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

            !uiState.hasAnyTransactions -> {
                EmptyDestinationScaffold(
                    icon = Icons.Outlined.ReceiptLong,
                    titleRes = R.string.empty_transactions_title,
                    bodyRes = R.string.empty_transactions_body,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    TransactionFilterSection(
                        uiState = uiState,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onTypeFilterChange = viewModel::onTypeFilterChange,
                        onWalletFilterChange = viewModel::onWalletFilterChange,
                        onCategoryFilterChange = viewModel::onCategoryFilterChange,
                        onDateFilterChange = viewModel::onDateFilterChange,
                    )

                    if (uiState.items.isEmpty()) {
                        EmptyDestinationScaffold(
                            icon = Icons.Outlined.Search,
                            titleRes = R.string.tx_filter_empty_title,
                            bodyRes = R.string.tx_filter_empty_body,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val grouped = remember(uiState.items) {
                            uiState.items.groupBy { DateFormatter.formatMonthYear(it.transaction.transactionDate) }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = SakuSpacing.lg,
                                end = SakuSpacing.lg,
                                top = SakuSpacing.sm,
                                bottom = 88.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                        ) {
                            grouped.forEach { (monthLabel, monthItems) ->
                                item(key = "hdr-$monthLabel") {
                                    Text(
                                        text = monthLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalSakuColors.current.textSecondary,
                                        modifier = Modifier.padding(top = SakuSpacing.xs, bottom = SakuSpacing.xs),
                                    )
                                }
                                items(
                                    items = monthItems,
                                    key = { it.transaction.id },
                                ) { item ->
                                    TransactionRowCard(
                                        item = item,
                                        onClick = { onTransactionClick(item.transaction.id) },
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
private fun TransactionFilterSection(
    uiState: TransactionsUiState,
    onSearchQueryChange: (String) -> Unit,
    onTypeFilterChange: (TransactionTypeFilter) -> Unit,
    onWalletFilterChange: (String?) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onDateFilterChange: (TransactionDateFilter) -> Unit,
) {
    var walletMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var dateMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakuSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
    ) {
        OutlinedTextField(
            value = uiState.filter.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.tx_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.filter.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
        ) {
            TransactionTypeFilter.entries.forEach { typeFilter ->
                val labelRes = when (typeFilter) {
                    TransactionTypeFilter.ALL -> R.string.tx_filter_all
                    TransactionTypeFilter.INCOME -> R.string.tx_type_income
                    TransactionTypeFilter.EXPENSE -> R.string.tx_type_expense
                    TransactionTypeFilter.TRANSFER -> R.string.tx_type_transfer
                }
                FilterChip(
                    selected = uiState.filter.typeFilter == typeFilter,
                    onClick = { onTypeFilterChange(typeFilter) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
        ) {
            Box {
                val selectedWallet = uiState.wallets.firstOrNull { it.id == uiState.filter.walletId }
                FilterChip(
                    selected = selectedWallet != null,
                    onClick = { walletMenuOpen = true },
                    label = {
                        Text(selectedWallet?.name ?: stringResource(R.string.tx_filter_wallet))
                    },
                )
                DropdownMenu(expanded = walletMenuOpen, onDismissRequest = { walletMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tx_filter_all_wallets)) },
                        onClick = {
                            onWalletFilterChange(null)
                            walletMenuOpen = false
                        },
                    )
                    uiState.wallets.forEach { w ->
                        DropdownMenuItem(
                            text = { Text(w.name) },
                            onClick = {
                                onWalletFilterChange(w.id)
                                walletMenuOpen = false
                            },
                        )
                    }
                }
            }

            if (uiState.filter.typeFilter != TransactionTypeFilter.TRANSFER) {
                Box {
                    val selectedCategory = uiState.categories.firstOrNull { it.id == uiState.filter.categoryId }
                    FilterChip(
                        selected = selectedCategory != null,
                        onClick = { categoryMenuOpen = true },
                        label = {
                            Text(
                                selectedCategory?.let { CategoryVisuals.localizedName(it) }
                                    ?: stringResource(R.string.tx_filter_category),
                            )
                        },
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tx_filter_all_categories)) },
                            onClick = {
                                onCategoryFilterChange(null)
                                categoryMenuOpen = false
                            },
                        )
                        uiState.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(CategoryVisuals.localizedName(cat)) },
                                onClick = {
                                    onCategoryFilterChange(cat.id)
                                    categoryMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Box {
                val dateLabelRes = when (uiState.filter.dateFilter) {
                    TransactionDateFilter.ALL -> R.string.tx_filter_date
                    TransactionDateFilter.THIS_MONTH -> R.string.tx_filter_this_month
                    TransactionDateFilter.LAST_7_DAYS -> R.string.tx_filter_last_7_days
                }
                FilterChip(
                    selected = uiState.filter.dateFilter != TransactionDateFilter.ALL,
                    onClick = { dateMenuOpen = true },
                    label = { Text(stringResource(dateLabelRes)) },
                )
                DropdownMenu(expanded = dateMenuOpen, onDismissRequest = { dateMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tx_filter_all_dates)) },
                        onClick = {
                            onDateFilterChange(TransactionDateFilter.ALL)
                            dateMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tx_filter_this_month)) },
                        onClick = {
                            onDateFilterChange(TransactionDateFilter.THIS_MONTH)
                            dateMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tx_filter_last_7_days)) },
                        onClick = {
                            onDateFilterChange(TransactionDateFilter.LAST_7_DAYS)
                            dateMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    item: TransactionFeedItem,
    onClick: () -> Unit,
) {
    val tx = item.transaction
    val sakuColors = LocalSakuColors.current

    val (icon, iconTint, iconContainer) = when (tx.type) {
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
        TransactionType.TRANSFER -> {
            val from = item.wallet?.name ?: "?"
            val to = item.destinationWallet?.name ?: "?"
            "$from → $to"
        }
        TransactionType.GOAL_ALLOCATION -> stringResource(R.string.tx_type_goal_allocation)
        TransactionType.GOAL_WITHDRAWAL -> stringResource(R.string.tx_type_goal_withdrawal)
        else -> item.category?.let { CategoryVisuals.localizedName(it) } ?: ""
    }

    val walletText = buildString {
        val wName = item.wallet?.name ?: ""
        append(wName)
        if (item.wallet?.isArchived == true || item.destinationWallet?.isArchived == true) {
            append(" (${stringResource(R.string.wallets_status_archived)})")
        }
        val note = tx.notes.ifBlank { tx.description }
        if (note.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(note)
        }
    }

    val signedAmount = when (tx.type) {
        TransactionType.INCOME -> "+${CurrencyFormatter.formatIdr(tx.amount)}"
        TransactionType.EXPENSE -> "-${CurrencyFormatter.formatIdr(tx.amount)}"
        else -> CurrencyFormatter.formatIdr(tx.amount)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(SakuSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (walletText.isNotBlank()) {
                    Text(
                        text = walletText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakuColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(SakuSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = signedAmount,
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
