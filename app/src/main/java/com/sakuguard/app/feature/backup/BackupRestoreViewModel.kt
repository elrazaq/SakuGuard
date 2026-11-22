package com.sakuguard.app.feature.backup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupManifest
import com.sakuguard.app.domain.usecase.ExportBackupUseCase
import com.sakuguard.app.domain.usecase.InspectBackupUseCase
import com.sakuguard.app.domain.usecase.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingRestorePreview(
    val sourceUri: String,
    val manifest: BackupManifest,
)

data class BackupRestoreUiState(
    val isBusy: Boolean = false,
    val encryptExport: Boolean = true,
    val exportPassword: String = "",
    val exportPasswordConfirm: String = "",
    @StringRes val exportPasswordErrorRes: Int? = null,
    val pendingRestore: PendingRestorePreview? = null,
    val restorePassword: String = "",
    @StringRes val restorePasswordErrorRes: Int? = null,
    @StringRes val successMessageRes: Int? = null,
    val successTransactionCount: Int = 0,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val exportBackupUseCase: ExportBackupUseCase,
    private val inspectBackupUseCase: InspectBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun onEncryptToggle(enabled: Boolean) {
        _uiState.update {
            it.copy(
                encryptExport = enabled,
                exportPassword = if (enabled) it.exportPassword else "",
                exportPasswordConfirm = if (enabled) it.exportPasswordConfirm else "",
                exportPasswordErrorRes = null,
                errorMessageRes = null,
            )
        }
    }

    fun onExportPasswordChange(value: String) {
        _uiState.update { it.copy(exportPassword = value, exportPasswordErrorRes = null, errorMessageRes = null) }
    }

    fun onExportPasswordConfirmChange(value: String) {
        _uiState.update { it.copy(exportPasswordConfirm = value, exportPasswordErrorRes = null, errorMessageRes = null) }
    }

    fun validateExportBeforePicker(): Boolean {
        val current = _uiState.value
        if (!current.encryptExport) return true
        if (current.exportPassword.isBlank()) {
            _uiState.update { it.copy(exportPasswordErrorRes = R.string.backup_error_password_blank) }
            return false
        }
        if (current.exportPassword != current.exportPasswordConfirm) {
            _uiState.update { it.copy(exportPasswordErrorRes = R.string.backup_error_password_mismatch) }
            return false
        }
        return true
    }

    fun exportToUri(destinationUri: String) {
        val current = _uiState.value
        if (current.isBusy || !validateExportBeforePicker()) return

        val passwordChars = if (current.encryptExport) current.exportPassword.toCharArray() else null
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isBusy = true, errorMessageRes = null, successMessageRes = null)
            }
            try {
                val result = exportBackupUseCase(destinationUri, passwordChars)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        exportPassword = "",
                        exportPasswordConfirm = "",
                        successMessageRes = R.string.backup_export_success,
                        successTransactionCount = result.recordCounts.transactions,
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isBusy = false) }
            } catch (e: BackupException) {
                _uiState.update { it.copy(isBusy = false, errorMessageRes = mapError(e)) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBusy = false, errorMessageRes = R.string.backup_error_io) }
            } finally {
                passwordChars?.fill('\u0000')
            }
        }
    }

    fun inspectSourceUri(sourceUri: String) {
        if (_uiState.value.isBusy) return
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    errorMessageRes = null,
                    successMessageRes = null,
                    pendingRestore = null,
                    restorePassword = "",
                    restorePasswordErrorRes = null,
                )
            }
            try {
                val inspection = inspectBackupUseCase(sourceUri)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingRestore = PendingRestorePreview(sourceUri, inspection.manifest),
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isBusy = false) }
            } catch (e: BackupException) {
                _uiState.update { it.copy(isBusy = false, errorMessageRes = mapError(e)) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBusy = false, errorMessageRes = R.string.backup_error_io) }
            }
        }
    }

    fun onRestorePasswordChange(value: String) {
        _uiState.update { it.copy(restorePassword = value, restorePasswordErrorRes = null) }
    }

    fun dismissRestorePreview() {
        _uiState.update {
            it.copy(pendingRestore = null, restorePassword = "", restorePasswordErrorRes = null)
        }
    }

    fun confirmRestore() {
        val current = _uiState.value
        val preview = current.pendingRestore ?: return
        if (current.isBusy) return

        if (preview.manifest.encrypted && current.restorePassword.isBlank()) {
            _uiState.update { it.copy(restorePasswordErrorRes = R.string.backup_error_password_required) }
            return
        }

        val passwordChars = if (preview.manifest.encrypted) current.restorePassword.toCharArray() else null
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessageRes = null, restorePasswordErrorRes = null) }
            try {
                val result = restoreBackupUseCase(preview.sourceUri, passwordChars)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingRestore = null,
                        restorePassword = "",
                        successMessageRes = R.string.backup_restore_success,
                        successTransactionCount = result.recordCounts.transactions,
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isBusy = false) }
            } catch (e: BackupException.WrongPasswordOrCorruptedException) {
                _uiState.update {
                    it.copy(isBusy = false, restorePasswordErrorRes = R.string.backup_error_wrong_password)
                }
            } catch (e: BackupException.PasswordRequiredException) {
                _uiState.update {
                    it.copy(isBusy = false, restorePasswordErrorRes = R.string.backup_error_password_required)
                }
            } catch (e: BackupException) {
                _uiState.update {
                    it.copy(isBusy = false, pendingRestore = null, restorePassword = "", errorMessageRes = mapError(e))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, pendingRestore = null, restorePassword = "", errorMessageRes = R.string.backup_error_io)
                }
            } finally {
                passwordChars?.fill('\u0000')
            }
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(isBusy = false) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(successMessageRes = null, errorMessageRes = null) }
    }

    private fun mapError(e: BackupException): Int = when (e) {
        is BackupException.SizeLimitExceededException -> R.string.backup_error_size_limit
        is BackupException.UnsupportedVersionException -> R.string.backup_error_unsupported_version
        is BackupException.PasswordRequiredException -> R.string.backup_error_password_required
        is BackupException.WrongPasswordOrCorruptedException -> R.string.backup_error_wrong_password
        is BackupException.IntegrityValidationException -> R.string.backup_error_integrity
        is BackupException.ChecksumMismatchException,
        is BackupException.InvalidArchiveException -> R.string.backup_error_invalid_archive
        is BackupException.IoFailureException -> R.string.backup_error_io
    }
}
