package com.sakuguard.app.feature.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.BudgetPeriodType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormScreen(
    onBack: () -> Unit,
    viewModel: BudgetFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCategoryMenu by remember { mutableStateOf(false) }
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            onBack()
        }
    }

    if (pickingStartDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = uiState.startDate)
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { viewModel.onStartDateSelect(DateFormatter.startOfDayMillis(it)) }
                        pickingStartDate = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingEndDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = uiState.endDateInclusive)
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { viewModel.onEndDateSelect(DateFormatter.startOfDayMillis(it)) }
                        pickingEndDate = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditMode) R.string.budgets_edit_title else R.string.budgets_create_title,
                        ),
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SakuSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(SakuSpacing.md),
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.budget_name_label)) },
                    placeholder = { Text(stringResource(R.string.budget_name_hint)) },
                    singleLine = true,
                    isError = uiState.nameErrorRes != null,
                    supportingText = uiState.nameErrorRes?.let { res -> { Text(stringResource(res)) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.budget_category_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Box {
                        val selectedCategory = uiState.expenseCategories.firstOrNull {
                            it.id == uiState.selectedCategoryId
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCategoryMenu = true },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (uiState.categoryErrorRes != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = SakuSpacing.md, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedCategory?.name ?: stringResource(R.string.budget_error_category_required),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                        ) {
                            uiState.expenseCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        viewModel.onCategorySelect(category.id)
                                        showCategoryMenu = false
                                    },
                                )
                            }
                        }
                    }
                    uiState.categoryErrorRes?.let { res ->
                        Text(
                            text = stringResource(res),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.limitInput,
                    onValueChange = viewModel::onLimitChange,
                    label = { Text(stringResource(R.string.budget_limit_label)) },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.limitErrorRes != null,
                    supportingText = uiState.limitErrorRes?.let { res -> { Text(stringResource(res)) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.budget_period_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
                    ) {
                        FilterChip(
                            selected = uiState.periodType == BudgetPeriodType.MONTHLY,
                            onClick = { viewModel.onPeriodTypeSelect(BudgetPeriodType.MONTHLY) },
                            label = { Text(stringResource(R.string.budget_period_monthly)) },
                        )
                        FilterChip(
                            selected = uiState.periodType == BudgetPeriodType.WEEKLY,
                            onClick = { viewModel.onPeriodTypeSelect(BudgetPeriodType.WEEKLY) },
                            label = { Text(stringResource(R.string.budget_period_weekly)) },
                        )
                        FilterChip(
                            selected = uiState.periodType == BudgetPeriodType.CUSTOM,
                            onClick = { viewModel.onPeriodTypeSelect(BudgetPeriodType.CUSTOM) },
                            label = { Text(stringResource(R.string.budget_period_custom)) },
                        )
                    }
                }

                DatePickerField(
                    label = stringResource(R.string.budget_start_date_label),
                    value = DateFormatter.formatShortDate(uiState.startDate),
                    onClick = { pickingStartDate = true },
                )

                if (uiState.periodType == BudgetPeriodType.CUSTOM) {
                    DatePickerField(
                        label = stringResource(R.string.budget_end_date_label),
                        value = DateFormatter.formatShortDate(uiState.endDateInclusive),
                        onClick = { pickingEndDate = true },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.budget_alert_80_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uiState.alert80Enabled,
                        onCheckedChange = viewModel::onAlert80Toggle,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.budget_alert_100_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uiState.alert100Enabled,
                        onCheckedChange = viewModel::onAlert100Toggle,
                    )
                }

                uiState.submitErrorRes?.let { res ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = stringResource(res),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(SakuSpacing.md),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SakuSpacing.sm))

                Button(
                    onClick = viewModel::saveBudget,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.budget_save_button))
                }
            }
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SakuSpacing.md, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Outlined.CalendarToday, contentDescription = null)
            }
        }
    }
}
