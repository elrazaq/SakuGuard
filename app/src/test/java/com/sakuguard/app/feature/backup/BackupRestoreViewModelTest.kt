package com.sakuguard.app.feature.backup

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupExportResult
import com.sakuguard.app.domain.model.BackupInspectionResult
import com.sakuguard.app.domain.model.BackupManifest
import com.sakuguard.app.domain.model.BackupRecordCounts
import com.sakuguard.app.domain.model.BackupRestoreResult
import com.sakuguard.app.domain.repository.BackupRepository
import com.sakuguard.app.domain.usecase.ExportBackupUseCase
import com.sakuguard.app.domain.usecase.InspectBackupUseCase
import com.sakuguard.app.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeBackupRepository
    private lateinit var viewModel: BackupRestoreViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeBackupRepository()
        viewModel = BackupRestoreViewModel(
            exportBackupUseCase = ExportBackupUseCase(fakeRepo),
            inspectBackupUseCase = InspectBackupUseCase(fakeRepo),
            restoreBackupUseCase = RestoreBackupUseCase(fakeRepo),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `export validates matching password and reports success`() = runTest {
        viewModel.onExportPasswordChange("abc")
        viewModel.onExportPasswordConfirmChange("xyz")
        assertThat(viewModel.validateExportBeforePicker()).isFalse()
        assertThat(viewModel.uiState.value.exportPasswordErrorRes).isEqualTo(R.string.backup_error_password_mismatch)

        viewModel.onExportPasswordConfirmChange("abc")
        assertThat(viewModel.validateExportBeforePicker()).isTrue()

        viewModel.exportToUri("content://test/out.saku")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.successMessageRes).isEqualTo(R.string.backup_export_success)
        assertThat(viewModel.uiState.value.successTransactionCount).isEqualTo(12)
        assertThat(viewModel.uiState.value.exportPassword).isEmpty()
    }

    @Test
    fun `restore inspects manifest, handles wrong password, then succeeds`() = runTest {
        viewModel.inspectSourceUri("content://test/in.saku")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingRestore).isNotNull()

        fakeRepo.nextRestoreError = BackupException.WrongPasswordOrCorruptedException("bad pass")
        viewModel.onRestorePasswordChange("wrong")
        viewModel.confirmRestore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.restorePasswordErrorRes).isEqualTo(R.string.backup_error_wrong_password)
        assertThat(viewModel.uiState.value.pendingRestore).isNotNull()

        fakeRepo.nextRestoreError = null
        viewModel.onRestorePasswordChange("right")
        viewModel.confirmRestore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingRestore).isNull()
        assertThat(viewModel.uiState.value.successMessageRes).isEqualTo(R.string.backup_restore_success)
    }

    private class FakeBackupRepository : BackupRepository {
        var nextRestoreError: Throwable? = null
        private val counts = BackupRecordCounts(2, 16, 1, 12, 2, 1)

        override suspend fun exportBackup(destinationUri: String, password: CharArray?): BackupExportResult =
            BackupExportResult(1_700_000_000_000L, counts, password != null)

        override suspend fun inspectBackup(sourceUri: String): BackupInspectionResult =
            BackupInspectionResult(
                BackupManifest(
                    backupFormatVersion = 1,
                    appVersionCode = 1,
                    appVersionName = "0.1.0",
                    databaseSchemaVersion = 2,
                    createdAtEpochMillis = 1_700_000_000_000L,
                    encrypted = true,
                    payloadSha256 = "abc",
                    recordCounts = counts,
                ),
            )

        override suspend fun restoreBackup(sourceUri: String, password: CharArray?): BackupRestoreResult {
            nextRestoreError?.let { throw it }
            return BackupRestoreResult(counts, 1_700_000_000_000L)
        }
    }
}
