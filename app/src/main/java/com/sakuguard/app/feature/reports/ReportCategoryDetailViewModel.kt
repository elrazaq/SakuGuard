package com.sakuguard.app.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

data class ReportCategoryDetailUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
    val hasMore: Boolean = true,
    val categoryName: String? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val startDate: Long = 0L,
    val endDateExclusive: Long = 0L,
    val items: List<TransactionFeedItem> = emptyList(),
)

@HiltViewModel
class ReportCategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    companion object {
        const val BATCH_SIZE = 20
        const val UNCATEGORIZED_KEY = "__UNCATEGORIZED__"
    }

    private val txType: TransactionType = runCatching {
        TransactionType.valueOf(checkNotNull(savedStateHandle.get<String>("type")))
    }.getOrDefault(TransactionType.EXPENSE)

    private val rawCategoryId: String = checkNotNull(savedStateHandle.get<String>("categoryId"))
    private val isUncategorized: Boolean = rawCategoryId == UNCATEGORIZED_KEY
    private val categoryId: String? = if (isUncategorized) null else rawCategoryId
    private val startDate: Long = savedStateHandle.get<Long>("startDate") ?: 0L
    private val endDateExclusive: Long = savedStateHandle.get<Long>("endDateExclusive") ?: Long.MAX_VALUE

    private val _uiState = MutableStateFlow(
        ReportCategoryDetailUiState(
            transactionType = txType,
            startDate = startDate,
            endDateExclusive = endDateExclusive,
        ),
    )
    val uiState: StateFlow<ReportCategoryDetailUiState> = _uiState.asStateFlow()

    private var walletsMap: Map<String, Wallet> = emptyMap()
    private var categoriesMap: Map<String, Category> = emptyMap()
    private var loadJob: Job? = null

    init {
        combine(
            walletRepository.observeWallets(includeArchived = true),
            categoryRepository.observeCategories(includeArchived = true),
            transactionRepository.observeRecentTransactions(limit = 1),
        ) { wallets, categories, _ ->
            walletsMap = wallets.associateBy { it.id }
            categoriesMap = categories.associateBy { it.id }
        }.onEach {
            loadInitial()
        }.launchIn(viewModelScope)
    }

    private fun loadInitial() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val catName = categoryId?.let { categoriesMap[it]?.name }
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadMoreError = false,
                    categoryName = catName,
                )
            }
            val page = fetchPage(cursor = null)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasMore = page.size >= BATCH_SIZE,
                    items = page.map(::toFeedItem),
                )
            }
        }
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        val last = current.items.lastOrNull()?.transaction ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = false) }
            try {
                val nextPage = fetchPage(cursor = last)
                val existingIds = current.items.mapTo(HashSet(current.items.size)) { it.transaction.id }
                val fresh = nextPage.filter { it.id !in existingIds }.map(::toFeedItem)
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = nextPage.size >= BATCH_SIZE,
                        items = it.items + fresh,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false, loadMoreError = true) }
            }
        }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreError = false) }
        loadNextPage()
    }

    private suspend fun fetchPage(cursor: Transaction?): List<Transaction> {
        val page = transactionRepository.getTransactionsPage(
            TransactionPageQuery(
                type = txType,
                categoryId = categoryId,
                startDate = startDate,
                endDateExclusive = endDateExclusive,
                cursorDate = cursor?.transactionDate,
                cursorCreatedAt = cursor?.createdAt,
                cursorId = cursor?.id,
                limit = BATCH_SIZE,
            ),
        )
        return if (isUncategorized) page.filter { it.categoryId == null } else page
    }

    private fun toFeedItem(tx: Transaction): TransactionFeedItem = TransactionFeedItem(
        transaction = tx,
        wallet = walletsMap[tx.walletId],
        destinationWallet = tx.destinationWalletId?.let { walletsMap[it] },
        category = tx.categoryId?.let { categoriesMap[it] },
    )
}
