package com.sakuguard.app.feature.wallets

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.WalletType

object WalletVisuals {
    data class IconOption(val key: String, val icon: ImageVector)

    val iconOptions: List<IconOption> = listOf(
        IconOption("bank", Icons.Outlined.AccountBalance),
        IconOption("cash", Icons.Outlined.Payments),
        IconOption("ewallet", Icons.Outlined.Smartphone),
        IconOption("wallet", Icons.Outlined.AccountBalanceWallet),
        IconOption("savings", Icons.Outlined.Savings),
        IconOption("card", Icons.Outlined.CreditCard),
        IconOption("other", Icons.Outlined.Folder),
    )

    val colorPalette: List<Long> = listOf(
        0xFF166534L, // Deep Forest Green
        0xFF0F766EL, // Teal
        0xFF039855L, // Emerald
        0xFF1D4ED8L, // Cobalt Blue
        0xFFD97706L, // Amber
        0xFF334155L, // Slate
    )

    @StringRes
    fun typeLabelRes(type: WalletType): Int = when (type) {
        WalletType.BANK_ACCOUNT -> R.string.wallet_type_bank
        WalletType.CASH -> R.string.wallet_type_cash
        WalletType.E_WALLET -> R.string.wallet_type_ewallet
        WalletType.OTHER -> R.string.wallet_type_other
    }

    fun defaultIconKey(type: WalletType): String = when (type) {
        WalletType.BANK_ACCOUNT -> "bank"
        WalletType.CASH -> "cash"
        WalletType.E_WALLET -> "ewallet"
        WalletType.OTHER -> "wallet"
    }

    fun resolveIcon(iconKey: String?, type: WalletType): ImageVector {
        val key = iconKey ?: defaultIconKey(type)
        return iconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Outlined.AccountBalanceWallet
    }

    fun resolveColor(colorHex: Long?): Color = Color(colorHex ?: colorPalette.first())
}
