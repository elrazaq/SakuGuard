package com.sakuguard.app.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.usecase.ObserveTransactionsFeedUseCase
import com.sakuguard.app.domain.usecase.TransactionFeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

enum class TransactionTypeFilter {
    ALL,
    INCOME,
    EXPENSE,
    TRANSFER,
}

enum class TransactionDateFilter {
    ALL,
    THIS_MONTH,
    LAST_7_DAYS,
}

data class TransactionFilterState(
    val searchQuery: String = "",
    val typeFilter: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val walletId: String? = null,
    val categoryId: String? = null,
    val dateFilter: TransactionDateFilter = TransactionDateFilter.ALL,
)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val hasAnyTransactions: Boolean = false,
    val items: List<TransactionFeedItem> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: TransactionFilterState = TransactionFilterState(),
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    observeTransactionsFeedUseCase: ObserveTransactionsFeedUseCase,
) : ViewModel() {

    private val filterState = MutableStateFlow(TransactionFilterState())

    val uiState: StateFlow<TransactionsUiState> = combine(
        observeTransactionsFeedUseCase(),
        filterState,
    ) { feed, filter ->
        val today = LocalDate.now()
        val filtered = applyFilters(feed.items, filter, today)
        TransactionsUiState(
            isLoading = false,
            hasAnyTransactions = feed.items.isNotEmpty(),
            items = filtered,
            wallets = feed.wallets,
            categories = feed.categories,
            filter = filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(),
    )

    fun onSearchQueryChange(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun onTypeFilterChange(typeFilter: TransactionTypeFilter) {
        filterState.update {
            val clearedCategory = if (typeFilter == TransactionTypeFilter.TRANSFER) null else it.categoryId
            it.copy(typeFilter = typeFilter, categoryId = clearedCategory)
        }
    }

    fun onWalletFilterChange(walletId: String?) {
        filterState.update { it.copy(walletId = walletId) }
    }

    fun onCategoryFilterChange(categoryId: String?) {
        filterState.update { it.copy(categoryId = categoryId) }
    }

    fun onDateFilterChange(dateFilter: TransactionDateFilter) {
        filterState.update { it.copy(dateFilter = dateFilter) }
    }

    fun clearFilters() {
        filterState.value = TransactionFilterState()
    }

    internal fun applyFilters(
        items: List<TransactionFeedItem>,
        filter: TransactionFilterState,
        today: LocalDate,
    ): List<TransactionFeedItem> {
        val query = filter.searchQuery.trim().lowercase()
        return items.filter { item ->
            val tx = item.transaction
            val typeMatches = when (filter.typeFilter) {
                TransactionTypeFilter.ALL -> true
                TransactionTypeFilter.INCOME -> tx.type == TransactionType.INCOME
                TransactionTypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                TransactionTypeFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
            }
            if (!typeMatches) return@filter false

            if (filter.walletId != null &&
                tx.walletId != filter.walletId &&
                tx.destinationWalletId != filter.walletId
            ) {
                return@filter false
            }

            if (filter.categoryId != null && tx.categoryId != filter.categoryId) {
                return@filter false
            }

            val txDate = DateFormatter.toLocalDate(tx.transactionDate)
            val dateMatches = when (filter.dateFilter) {
                TransactionDateFilter.ALL -> true
                TransactionDateFilter.THIS_MONTH ->
                    txDate.year == today.year && txDate.month == today.month
                TransactionDateFilter.LAST_7_DAYS ->
                    !txDate.isBefore(today.minusDays(6)) && !txDate.isAfter(today)
            }
            if (!dateMatches) return@filter false

            if (query.isNotEmpty()) {
                val matchesText = listOfNotNull(
                    tx.description,
                    tx.notes,
                    item.category?.name,
                    item.wallet?.name,
                    item.destinationWallet?.name,
                ).any { it.lowercase().contains(query) }
                if (!matchesText) return@filter false
            }

            true
        }
    }
}
