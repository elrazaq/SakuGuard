package com.sakuguard.app.feature.goals

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.sakuguard.app.R
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode

object GoalVisuals {
    data class IconOption(val key: String, val icon: ImageVector)

    val iconOptions: List<IconOption> = listOf(
        IconOption("savings", Icons.Outlined.Savings),
        IconOption("travel", Icons.Outlined.Flight),
        IconOption("laptop", Icons.Outlined.LaptopMac),
        IconOption("phone", Icons.Outlined.Smartphone),
        IconOption("home", Icons.Outlined.Home),
        IconOption("car", Icons.Outlined.DirectionsCar),
        IconOption("education", Icons.Outlined.School),
        IconOption("shield", Icons.Outlined.Shield),
    )

    val colorPalette: List<Long> = listOf(
        0xFF166534L,
        0xFF0F766EL,
        0xFF039855L,
        0xFF1D4ED8L,
        0xFFD97706L,
        0xFF334155L,
    )

    fun resolveIcon(iconKey: String?): ImageVector =
        iconOptions.firstOrNull { it.key == iconKey }?.icon ?: Icons.Outlined.Savings

    fun resolveColor(colorHex: Long?): Color = Color(colorHex ?: colorPalette.first())

    @StringRes
    fun protectionLabelRes(mode: ProtectionMode): Int = when (mode) {
        ProtectionMode.STANDARD -> R.string.goal_protection_standard
        ProtectionMode.PROTECTED -> R.string.goal_protection_protected
        ProtectionMode.STRICT -> R.string.goal_protection_strict
    }

    @StringRes
    fun protectionDescRes(mode: ProtectionMode): Int = when (mode) {
        ProtectionMode.STANDARD -> R.string.goal_protection_standard_desc
        ProtectionMode.PROTECTED -> R.string.goal_protection_protected_desc
        ProtectionMode.STRICT -> R.string.goal_protection_strict_desc
    }

    fun protectionIcon(mode: ProtectionMode): ImageVector = when (mode) {
        ProtectionMode.STANDARD -> Icons.Outlined.Savings
        ProtectionMode.PROTECTED -> Icons.Outlined.Shield
        ProtectionMode.STRICT -> Icons.Outlined.Lock
    }

    @StringRes
    fun statusLabelRes(status: GoalStatus): Int = when (status) {
        GoalStatus.ACTIVE -> R.string.goal_status_active
        GoalStatus.COMPLETED -> R.string.goal_status_completed
        GoalStatus.ARCHIVED -> R.string.goal_status_archived
    }
}
