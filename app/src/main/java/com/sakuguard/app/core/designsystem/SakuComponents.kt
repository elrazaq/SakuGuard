package com.sakuguard.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Figma-inspired Deep Forest Green hero summary card.
 */
@Composable
fun SakuHeroBalanceCard(
    title: String,
    amountText: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val sakuColors = LocalSakuColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(sakuColors.heroStart, sakuColors.heroEnd),
                ),
            ),
    ) {
        // Subtle decorative circles echoing the Figma hero card without clutter
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = (-24).dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(sakuColors.onHero.copy(alpha = 0.08f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = (-40).dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(sakuColors.onHero.copy(alpha = 0.06f)),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakuSpacing.xl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = sakuColors.onHero.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(SakuSpacing.xs))
            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = sakuColors.onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(SakuSpacing.sm))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = sakuColors.onHero.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Two-column KPI summary strip matching the Figma Wallet Details KPI layout.
 */
@Composable
fun SakuKpiPairCard(
    leftLabel: String,
    leftAmount: String,
    leftIcon: ImageVector,
    leftTint: Color,
    leftContainer: Color,
    rightLabel: String,
    rightAmount: String,
    rightIcon: ImageVector,
    rightTint: Color,
    rightContainer: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SakuSpacing.md, horizontal = SakuSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpiCell(
                label = leftLabel,
                amount = leftAmount,
                icon = leftIcon,
                tint = leftTint,
                container = leftContainer,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier
                    .height(40.dp)
                    .padding(horizontal = SakuSpacing.sm),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            )
            KpiCell(
                label = rightLabel,
                amount = rightAmount,
                icon = rightIcon,
                tint = rightTint,
                container = rightContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiCell(
    label: String,
    amount: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(SakuSpacing.sm))
        Column {
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = LocalSakuColors.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
