package com.sakuguard.app.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.TransactionPageQuery
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import com.sakuguard.app.domain.usecase.TransactionFeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
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
    val isLoadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
    val hasMore: Boolean = true,
    val hasAnyTransactions: Boolean = false,
    val items: List<TransactionFeedItem> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: TransactionFilterState = TransactionFilterState(),
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    companion object {
        const val BATCH_SIZE = 20
    }

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var walletsMap: Map<String, Wallet> = emptyMap()
    private var categoriesMap: Map<String, Category> = emptyMap()
    private var loadJob: Job? = null

    init {
        combine(
            walletRepository.observeWallets(includeArchived = true),
            categoryRepository.observeCategories(includeArchived = true),
            transactionRepository.observeRecentTransactions(limit = 1),
        ) { wallets, categories, latestList ->
            Triple(wallets, categories, latestList.isNotEmpty())
        }.onEach { (wallets, categories, hasAny) ->
            walletsMap = wallets.associateBy { it.id }
            categoriesMap = categories.associateBy { it.id }
            _uiState.update {
                it.copy(
                    wallets = wallets,
                    categories = categories,
                    hasAnyTransactions = hasAny,
                )
            }
            reloadFirstPage()
        }.launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(filter = it.filter.copy(searchQuery = query)) }
        reloadFirstPage()
    }

    fun onTypeFilterChange(typeFilter: TransactionTypeFilter) {
        _uiState.update {
            val clearedCategory = if (typeFilter == TransactionTypeFilter.TRANSFER) null else it.filter.categoryId
            it.copy(filter = it.filter.copy(typeFilter = typeFilter, categoryId = clearedCategory))
        }
        reloadFirstPage()
    }

    fun onWalletFilterChange(walletId: String?) {
        _uiState.update { it.copy(filter = it.filter.copy(walletId = walletId)) }
        reloadFirstPage()
    }

    fun onCategoryFilterChange(categoryId: String?) {
        _uiState.update { it.copy(filter = it.filter.copy(categoryId = categoryId)) }
        reloadFirstPage()
    }

    fun onDateFilterChange(dateFilter: TransactionDateFilter) {
        _uiState.update { it.copy(filter = it.filter.copy(dateFilter = dateFilter)) }
        reloadFirstPage()
    }

    fun clearFilters() {
        _uiState.update { it.copy(filter = TransactionFilterState()) }
        reloadFirstPage()
    }

    fun reloadFirstPage() {
        loadJob?.cancel()
        _uiState.update {
            it.copy(isLoading = true, isLoadingMore = false, loadMoreError = false, hasMore = true)
        }
        loadJob = viewModelScope.launch {
            try {
                val page = transactionRepository.getTransactionsPage(buildQuery(cursor = null))
                val mapped = page.map { toFeedItem(it) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = mapped,
                        hasMore = page.size >= BATCH_SIZE,
                        loadMoreError = false,
                    )
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoading = false, loadMoreError = true) }
            }
        }
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore || current.loadMoreError) return
        val lastTx = current.items.lastOrNull()?.transaction ?: return

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = false) }
        viewModelScope.launch {
            try {
                val page = transactionRepository.getTransactionsPage(buildQuery(cursor = lastTx))
                val existingIds = _uiState.value.items.mapTo(HashSet()) { it.transaction.id }
                val appended = page.filter { it.id !in existingIds }.map { toFeedItem(it) }
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + appended,
                        hasMore = page.size >= BATCH_SIZE,
                        loadMoreError = false,
                    )
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoadingMore = false, loadMoreError = true) }
            }
        }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreError = false) }
        loadNextPage()
    }

    private fun buildQuery(cursor: Transaction?): TransactionPageQuery {
        val filter = _uiState.value.filter
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val (startDate, endDateExclusive) = when (filter.dateFilter) {
            TransactionDateFilter.ALL -> null to null
            TransactionDateFilter.THIS_MONTH -> {
                val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
            TransactionDateFilter.LAST_7_DAYS -> {
                val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
        }

        val type = when (filter.typeFilter) {
            TransactionTypeFilter.ALL -> null
            TransactionTypeFilter.INCOME -> TransactionType.INCOME
            TransactionTypeFilter.EXPENSE -> TransactionType.EXPENSE
            TransactionTypeFilter.TRANSFER -> TransactionType.TRANSFER
        }

        return TransactionPageQuery(
            type = type,
            walletId = filter.walletId,
            categoryId = filter.categoryId,
            startDate = startDate,
            endDateExclusive = endDateExclusive,
            searchQuery = filter.searchQuery,
            cursorDate = cursor?.transactionDate,
            cursorCreatedAt = cursor?.createdAt,
            cursorId = cursor?.id,
            limit = BATCH_SIZE,
        )
    }

    private fun toFeedItem(tx: Transaction): TransactionFeedItem = TransactionFeedItem(
        transaction = tx,
        wallet = walletsMap[tx.walletId],
        destinationWallet = tx.destinationWalletId?.let { walletsMap[it] },
        category = tx.categoryId?.let { categoriesMap[it] },
    )
}
