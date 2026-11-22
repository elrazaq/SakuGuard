package com.sakuguard.app.feature.wallets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.domain.model.WalletType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: WalletFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            onSaved()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditMode) R.string.wallets_edit_title
                            else R.string.wallets_create_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(SakuSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SakuSpacing.lg),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.wallets_name_label)) },
                placeholder = { Text(stringResource(R.string.wallets_name_hint)) },
                isError = uiState.nameErrorRes != null,
                supportingText = uiState.nameErrorRes?.let { { Text(stringResource(it)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                Text(
                    text = stringResource(R.string.wallets_type_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs),
                ) {
                    WalletType.entries.forEach { type ->
                        FilterChip(
                            selected = uiState.type == type,
                            onClick = { viewModel.onTypeChange(type) },
                            label = { Text(stringResource(WalletVisuals.typeLabelRes(type))) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.openingBalanceInput,
                onValueChange = viewModel::onOpeningBalanceChange,
                label = { Text(stringResource(R.string.wallets_opening_balance)) },
                prefix = { Text("Rp") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.balanceErrorRes != null,
                supportingText = uiState.balanceErrorRes?.let { { Text(stringResource(it)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.wallets_description_label)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                Text(
                    text = stringResource(R.string.wallets_icon_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                ) {
                    WalletVisuals.iconOptions.forEach { option ->
                        val selected = uiState.iconKey == option.key
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.onIconSelect(option.key) },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.key,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm)) {
                Text(
                    text = stringResource(R.string.wallets_color_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SakuSpacing.md),
                ) {
                    WalletVisuals.colorPalette.forEach { hex ->
                        val selected = uiState.colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { viewModel.onColorSelect(hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            uiState.submitErrorRes?.let { errorRes ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SakuSpacing.md),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SakuSpacing.xs))

            Button(
                onClick = viewModel::saveWallet,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(text = stringResource(R.string.wallets_save_button))
            }
        }
    }
}
