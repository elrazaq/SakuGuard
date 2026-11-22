package com.sakuguard.app.feature.transactions

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.core.formatting.CurrencyInputParser
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryType
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import com.sakuguard.app.domain.usecase.RecordTransactionUseCase
import com.sakuguard.app.domain.usecase.UpdateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TransactionFormUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val selectedWalletId: String? = null,
    val selectedDestinationWalletId: String? = null,
    val selectedCategoryId: String? = null,
    val transactionDate: Long = DateFormatter.startOfDayMillis(System.currentTimeMillis()),
    val notes: String = "",
    val activeWallets: List<Wallet> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    @StringRes val amountErrorRes: Int? = null,
    @StringRes val walletErrorRes: Int? = null,
    @StringRes val destinationWalletErrorRes: Int? = null,
    @StringRes val categoryErrorRes: Int? = null,
    @StringRes val submitErrorRes: Int? = null,
    val savedSuccessfully: Boolean = false,
)

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val recordTransactionUseCase: RecordTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
) : ViewModel() {

    private val editingTransactionId: String? = savedStateHandle["transactionId"]
    private var existingTransaction: Transaction? = null
    private var allCategories: List<Category> = emptyList()

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(isEditMode = editingTransactionId != null),
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    init {
        loadFormData()
    }

    private fun loadFormData() {
        viewModelScope.launch {
            val activeWallets = walletRepository.getAllWallets().filterNot { it.isArchived }
            allCategories = categoryRepository.getAllCategories().filterNot { it.isArchived }

            val existing = editingTransactionId?.let { transactionRepository.getTransactionById(it) }
            existingTransaction = existing

            if (editingTransactionId != null && existing == null) {
                _uiState.update {
                    it.copy(isLoading = false, submitErrorRes = R.string.tx_error_generic)
                }
                return@launch
            }

            if (existing != null &&
                (existing.type == TransactionType.GOAL_ALLOCATION || existing.type == TransactionType.GOAL_WITHDRAWAL)
            ) {
                _uiState.update {
                    it.copy(isLoading = false, submitErrorRes = R.string.tx_error_goal_ledger_locked)
                }
                return@launch
            }

            val initialType = existing?.type ?: TransactionType.EXPENSE
            val catsForType = categoriesForType(initialType)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    selectedType = initialType,
                    amountInput = existing?.amount?.toString() ?: "",
                    selectedWalletId = existing?.walletId ?: activeWallets.firstOrNull()?.id,
                    selectedDestinationWalletId = existing?.destinationWalletId
                        ?: activeWallets.getOrNull(1)?.id,
                    selectedCategoryId = existing?.categoryId ?: catsForType.firstOrNull()?.id,
                    transactionDate = existing?.transactionDate
                        ?: DateFormatter.startOfDayMillis(System.currentTimeMillis()),
                    notes = existing?.notes?.ifBlank { existing.description } ?: "",
                    activeWallets = activeWallets,
                    availableCategories = catsForType,
                )
            }
        }
    }

    private fun categoriesForType(type: TransactionType): List<Category> = when (type) {
        TransactionType.INCOME -> allCategories.filter { it.type == CategoryType.INCOME }
        TransactionType.EXPENSE -> allCategories.filter { it.type == CategoryType.EXPENSE }
        else -> emptyList()
    }

    fun onTypeSelect(type: TransactionType) {
        if (_uiState.value.isEditMode) return
        if (type != TransactionType.INCOME &&
            type != TransactionType.EXPENSE &&
            type != TransactionType.TRANSFER
        ) {
            return
        }
        val cats = categoriesForType(type)
        _uiState.update {
            it.copy(
                selectedType = type,
                availableCategories = cats,
                selectedCategoryId = cats.firstOrNull()?.id,
                amountErrorRes = null,
                walletErrorRes = null,
                destinationWalletErrorRes = null,
                categoryErrorRes = null,
                submitErrorRes = null,
            )
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountInput = value, amountErrorRes = null, submitErrorRes = null) }
    }

    fun onWalletSelect(walletId: String) {
        _uiState.update {
            it.copy(
                selectedWalletId = walletId,
                walletErrorRes = null,
                destinationWalletErrorRes = null,
                submitErrorRes = null,
            )
        }
    }

    fun onDestinationWalletSelect(walletId: String) {
        _uiState.update {
            it.copy(
                selectedDestinationWalletId = walletId,
                destinationWalletErrorRes = null,
                submitErrorRes = null,
            )
        }
    }

    fun onCategorySelect(categoryId: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, categoryErrorRes = null, submitErrorRes = null) }
    }

    fun onDateSelect(localStartOfDayMillis: Long) {
        _uiState.update { it.copy(transactionDate = localStartOfDayMillis, submitErrorRes = null) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun saveTransaction() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val parsedAmount = CurrencyInputParser.parseNonNegativeIdr(current.amountInput)
            ?.takeIf { it > 0L }
        val walletId = current.selectedWalletId
        val isTransfer = current.selectedType == TransactionType.TRANSFER
        val destWalletId = if (isTransfer) current.selectedDestinationWalletId else null
        val categoryId = if (!isTransfer) current.selectedCategoryId else null

        val sameWalletError = isTransfer && walletId != null && walletId == destWalletId
        val destError = when {
            isTransfer && destWalletId == null -> R.string.tx_error_dest_wallet_required
            sameWalletError -> R.string.tx_error_same_wallet
            else -> null
        }

        if (parsedAmount == null ||
            walletId == null ||
            destError != null ||
            (!isTransfer && categoryId == null)
        ) {
            _uiState.update {
                it.copy(
                    amountErrorRes = if (parsedAmount == null) R.string.tx_error_amount else null,
                    walletErrorRes = if (walletId == null) R.string.tx_error_wallet_required else null,
                    destinationWalletErrorRes = destError,
                    categoryErrorRes = if (!isTransfer && categoryId == null) R.string.tx_error_category_required else null,
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, submitErrorRes = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleanNotes = current.notes.trim()
            try {
                val existing = existingTransaction
                if (existing != null) {
                    updateTransactionUseCase(
                        existing.copy(
                            amount = parsedAmount,
                            walletId = walletId,
                            destinationWalletId = destWalletId,
                            categoryId = categoryId,
                            description = cleanNotes,
                            notes = cleanNotes,
                            transactionDate = current.transactionDate,
                            updatedAt = now,
                        ),
                    )
                } else {
                    recordTransactionUseCase(
                        Transaction(
                            id = UUID.randomUUID().toString(),
                            type = current.selectedType,
                            amount = parsedAmount,
                            walletId = walletId,
                            destinationWalletId = destWalletId,
                            categoryId = categoryId,
                            goalId = null,
                            description = cleanNotes,
                            notes = cleanNotes,
                            transactionDate = current.transactionDate,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: DomainException) {
                val errorRes = mapDomainError(e)
                _uiState.update { it.copy(isSaving = false, submitErrorRes = errorRes) }
            }
        }
    }

    @StringRes
    private fun mapDomainError(e: DomainException): Int = when (e) {
        is DomainException.InsufficientFreeMoneyException -> R.string.tx_error_insufficient_free
        is DomainException.ArchivedEntityException -> R.string.tx_error_archived_wallet
        is DomainException.PreTrackingDateException -> R.string.tx_error_pre_tracking_date
        is DomainException.ArithmeticOverflowException -> R.string.tx_error_overflow
        is DomainException.InvalidTransferException -> R.string.tx_error_same_wallet
        is DomainException.InvalidAmountException -> R.string.tx_error_amount
        is DomainException.InvalidTransactionMutationException -> R.string.tx_error_goal_ledger_locked
        else -> R.string.tx_error_generic
    }
}
