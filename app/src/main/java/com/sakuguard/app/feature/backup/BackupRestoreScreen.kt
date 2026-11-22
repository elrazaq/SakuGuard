package com.sakuguard.app.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.LocalSakuColors
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.DateFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sakuColors = LocalSakuColors.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let { viewModel.exportToUri(it.toString()) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.inspectSourceUri(it.toString()) }
    }

    uiState.pendingRestore?.let { preview ->
        val m = preview.manifest
        AlertDialog(
            onDismissRequest = { if (!uiState.isBusy) viewModel.dismissRestorePreview() },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.backup_restore_confirm_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(
                            R.string.backup_restore_meta_format,
                            DateFormatter.formatShortDate(m.createdAtEpochMillis),
                            m.appVersionName,
                            m.databaseSchemaVersion,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.backup_restore_counts_format,
                            m.recordCounts.wallets,
                            m.recordCounts.transactions,
                            m.recordCounts.goals,
                            m.recordCounts.budgets,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (m.encrypted) {
                        OutlinedTextField(
                            value = uiState.restorePassword,
                            onValueChange = viewModel::onRestorePasswordChange,
                            label = { Text(stringResource(R.string.backup_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            isError = uiState.restorePasswordErrorRes != null,
                            supportingText = uiState.restorePasswordErrorRes?.let { res ->
                                { Text(stringResource(res)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRestore,
                    enabled = !uiState.isBusy,
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_confirm_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissRestorePreview,
                    enabled = !uiState.isBusy,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.backup_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(SakuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SakuSpacing.lg),
        ) {
            if (uiState.isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::cancelActiveOperation) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }

            uiState.successMessageRes?.let { res ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = sakuColors.income.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, sakuColors.income.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = stringResource(res, uiState.successTransactionCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakuColors.income,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SakuSpacing.md),
                    )
                }
            }

            uiState.errorMessageRes?.let { res ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SakuSpacing.md),
                    )
                }
            }

            // Export Card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SakuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.backup_export_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.backup_export_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.backup_encrypt_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = uiState.encryptExport,
                            onCheckedChange = viewModel::onEncryptToggle,
                            enabled = !uiState.isBusy,
                        )
                    }

                    if (uiState.encryptExport) {
                        OutlinedTextField(
                            value = uiState.exportPassword,
                            onValueChange = viewModel::onExportPasswordChange,
                            label = { Text(stringResource(R.string.backup_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            isError = uiState.exportPasswordErrorRes != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = uiState.exportPasswordConfirm,
                            onValueChange = viewModel::onExportPasswordConfirmChange,
                            label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            isError = uiState.exportPasswordErrorRes != null,
                            supportingText = uiState.exportPasswordErrorRes?.let { res ->
                                { Text(stringResource(res)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = sakuColors.warning,
                            )
                            Text(
                                text = stringResource(R.string.backup_unencrypted_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = sakuColors.warning,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (viewModel.validateExportBeforePicker()) {
                                val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.now())
                                exportLauncher.launch("sakuguard-backup-$stamp.saku")
                            }
                        },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.backup_export_button))
                    }
                }
            }

            // Restore Card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SakuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.backup_restore_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.backup_restore_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !uiState.isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.backup_restore_button))
                    }
                }
            }
        }
    }
}
