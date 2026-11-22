package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.model.BackupExportResult
import com.sakuguard.app.domain.model.BackupInspectionResult
import com.sakuguard.app.domain.model.BackupRestoreResult
import com.sakuguard.app.domain.repository.BackupRepository
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(destinationUri: String, password: CharArray?): BackupExportResult =
        backupRepository.exportBackup(destinationUri, password)
}

class InspectBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(sourceUri: String): BackupInspectionResult =
        backupRepository.inspectBackup(sourceUri)
}

class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(sourceUri: String, password: CharArray?): BackupRestoreResult =
        backupRepository.restoreBackup(sourceUri, password)
}
