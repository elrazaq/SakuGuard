package com.sakuguard.app.feature.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.EmptyDestinationScaffold

@Composable
fun TransactionsScreen() {
    EmptyDestinationScaffold(
        icon = Icons.Outlined.ReceiptLong,
        titleRes = R.string.empty_transactions_title,
        bodyRes = R.string.empty_transactions_body,
    )
}
