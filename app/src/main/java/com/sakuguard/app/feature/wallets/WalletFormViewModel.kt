package com.sakuguard.app.feature.wallets

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.core.formatting.CurrencyInputParser
import com.sakuguard.app.domain.model.DomainException
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletType
import com.sakuguard.app.domain.repository.WalletRepository
import com.sakuguard.app.domain.usecase.CreateWalletUseCase
import com.sakuguard.app.domain.usecase.UpdateWalletUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WalletFormUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val type: WalletType = WalletType.BANK_ACCOUNT,
    val openingBalanceInput: String = "0",
    val description: String = "",
    val iconKey: String = WalletVisuals.defaultIconKey(WalletType.BANK_ACCOUNT),
    val colorHex: Long = WalletVisuals.colorPalette.first(),
    @StringRes val nameErrorRes: Int? = null,
    @StringRes val balanceErrorRes: Int? = null,
    @StringRes val submitErrorRes: Int? = null,
    val savedSuccessfully: Boolean = false,
)

@HiltViewModel
class WalletFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val walletRepository: WalletRepository,
    private val createWalletUseCase: CreateWalletUseCase,
    private val updateWalletUseCase: UpdateWalletUseCase,
) : ViewModel() {

    private val editingWalletId: String? = savedStateHandle["walletId"]
    private var existingWallet: Wallet? = null

    private val _uiState = MutableStateFlow(
        WalletFormUiState(
            isEditMode = editingWalletId != null,
            isLoading = editingWalletId != null,
        ),
    )
    val uiState: StateFlow<WalletFormUiState> = _uiState.asStateFlow()

    init {
        if (editingWalletId != null) {
            loadWallet(editingWalletId)
        }
    }

    private fun loadWallet(walletId: String) {
        viewModelScope.launch {
            val wallet = walletRepository.getWalletById(walletId)
            existingWallet = wallet
            if (wallet != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = wallet.name,
                        type = wallet.type,
                        openingBalanceInput = wallet.openingBalance.toString(),
                        description = wallet.description,
                        iconKey = wallet.iconKey ?: WalletVisuals.defaultIconKey(wallet.type),
                        colorHex = wallet.colorHex ?: WalletVisuals.colorPalette.first(),
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, submitErrorRes = R.string.wallet_error_generic)
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameErrorRes = null, submitErrorRes = null) }
    }

    fun onTypeChange(type: WalletType) {
        _uiState.update { it.copy(type = type, submitErrorRes = null) }
    }

    fun onOpeningBalanceChange(value: String) {
        _uiState.update {
            it.copy(openingBalanceInput = value, balanceErrorRes = null, submitErrorRes = null)
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onIconSelect(iconKey: String) {
        _uiState.update { it.copy(iconKey = iconKey) }
    }

    fun onColorSelect(colorHex: Long) {
        _uiState.update { it.copy(colorHex = colorHex) }
    }

    fun saveWallet() {
        val current = _uiState.value
        if (current.isSaving) return

        val nameValid = current.name.isNotBlank()
        val parsedBalance = CurrencyInputParser.parseNonNegativeIdr(current.openingBalanceInput)

        if (!nameValid || parsedBalance == null) {
            _uiState.update {
                it.copy(
                    nameErrorRes = if (!nameValid) R.string.wallet_error_name_blank else null,
                    balanceErrorRes = if (parsedBalance == null) R.string.wallet_error_invalid_amount else null,
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, submitErrorRes = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                val base = existingWallet
                if (base != null) {
                    updateWalletUseCase(
                        base.copy(
                            name = current.name.trim(),
                            type = current.type,
                            openingBalance = parsedBalance,
                            description = current.description.trim(),
                            iconKey = current.iconKey,
                            colorHex = current.colorHex,
                            updatedAt = now,
                        ),
                    )
                } else {
                    createWalletUseCase(
                        Wallet(
                            id = UUID.randomUUID().toString(),
                            name = current.name.trim(),
                            type = current.type,
                            openingBalance = parsedBalance,
                            trackingStartDate = now,
                            description = current.description.trim(),
                            colorHex = current.colorHex,
                            iconKey = current.iconKey,
                            isArchived = false,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (_: DomainException.InsufficientFreeMoneyException) {
                _uiState.update {
                    it.copy(isSaving = false, submitErrorRes = R.string.wallet_error_insufficient_free)
                }
            } catch (_: DomainException.InvalidInputException) {
                _uiState.update {
                    it.copy(isSaving = false, nameErrorRes = R.string.wallet_error_name_blank)
                }
            } catch (_: DomainException.InvalidAmountException) {
                _uiState.update {
                    it.copy(isSaving = false, balanceErrorRes = R.string.wallet_error_invalid_amount)
                }
            } catch (_: DomainException) {
                _uiState.update {
                    it.copy(isSaving = false, submitErrorRes = R.string.wallet_error_generic)
                }
            }
        }
    }
}
