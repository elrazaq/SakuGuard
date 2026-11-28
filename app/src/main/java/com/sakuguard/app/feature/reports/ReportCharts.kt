package com.sakuguard.app.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakuguard.app.R
import com.sakuguard.app.core.designsystem.LocalSakuColors
import com.sakuguard.app.core.designsystem.SakuSpacing
import com.sakuguard.app.core.formatting.CurrencyFormatter
import com.sakuguard.app.domain.model.CategoryBreakdownItem
import com.sakuguard.app.domain.model.ReportCashFlowBucket
import kotlin.math.max

@Composable
internal fun IncomeVsExpenseBarChart(
    buckets: List<ReportCashFlowBucket>,
    amountsHidden: Boolean,
    maskedAmount: String,
    incomeLabel: String,
    expenseLabel: String,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val sakuColors = LocalSakuColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    var selectedIndex by remember(buckets) { mutableIntStateOf(buckets.lastIndex) }

    val maxVal = remember(buckets) {
        max(1L, buckets.maxOf { max(it.income, it.expense) })
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SakuSpacing.sm),
    ) {
        val selected = buckets.getOrNull(selectedIndex)
        if (selected != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakuSpacing.md, vertical = SakuSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = selected.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "$incomeLabel: ${if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(selected.income)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = sakuColors.income,
                    )
                    Text(
                        text = "$expenseLabel: ${if (amountsHidden) maskedAmount else CurrencyFormatter.formatIdr(selected.expense)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = sakuColors.expense,
                    )
                }
            }
        }

        val barCd = stringResource(R.string.reports_chart_income_vs_expense_cd, buckets.size)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics { contentDescription = barCd }
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val groupWidth = size.width / buckets.size.toFloat()
                        val idx = (offset.x / groupWidth).toInt().coerceIn(0, buckets.lastIndex)
                        selectedIndex = idx
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val groupWidth = w / buckets.size.toFloat()
            val barWidth = (groupWidth * 0.28f).coerceIn(6f, 28f)
            val gap = (barWidth * 0.25f).coerceAtLeast(3f)

            for (i in 1..3) {
                val y = h * (i / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                )
            }

            buckets.forEachIndexed { idx, bucket ->
                val centerX = idx * groupWidth + groupWidth / 2f
                val incHeight = (bucket.income.toDouble() / maxVal.toDouble()).toFloat() * (h - 8f)
                val expHeight = (bucket.expense.toDouble() / maxVal.toDouble()).toFloat() * (h - 8f)
                val alpha = if (idx == selectedIndex) 1f else 0.72f

                if (incHeight > 0f) {
                    drawRoundRect(
                        color = sakuColors.income.copy(alpha = alpha),
                        topLeft = Offset(centerX - gap / 2f - barWidth, h - incHeight),
                        size = Size(barWidth, incHeight),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                }
                if (expHeight > 0f) {
                    drawRoundRect(
                        color = sakuColors.expense.copy(alpha = alpha),
                        topLeft = Offset(centerX + gap / 2f, h - expHeight),
                        size = Size(barWidth, expHeight),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val step = (buckets.size / 4).coerceAtLeast(1)
            buckets.forEachIndexed { idx, bucket ->
                if (idx == 0 || idx == buckets.lastIndex || idx % step == 0) {
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpendingTrendLineChart(
    buckets: List<ReportCashFlowBucket>,
    modifier: Modifier = Modifier,
) {
    if (buckets.size < 2) return
    val sakuColors = LocalSakuColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxVal = remember(buckets) { max(1L, buckets.maxOf { it.expense }) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SakuSpacing.xs),
    ) {
        val trendCd = stringResource(R.string.reports_chart_spending_trend_cd, buckets.size)
        val fillColor = sakuColors.expense.copy(alpha = 0.14f)
        val strokeStyle = remember { Stroke(width = 4f, cap = StrokeCap.Round) }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics { contentDescription = trendCd }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val padY = 10f
                    val usableH = (h - padY * 2f).coerceAtLeast(1f)
                    val stepX = w / (buckets.size - 1).coerceAtLeast(1).toFloat()

                    val points = buckets.mapIndexed { idx, bucket ->
                        val x = idx * stepX
                        val ratio = (bucket.expense.toDouble() / maxVal.toDouble()).toFloat()
                        val y = h - padY - ratio * usableH
                        Offset(x, y)
                    }

                    val fillPath = Path().apply {
                        moveTo(points.first().x, h)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, h)
                        close()
                    }
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }

                    onDrawBehind {
                        for (i in 1..2) {
                            val y = h * (i / 3f)
                            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
                        }
                        drawPath(path = fillPath, color = fillColor)
                        drawPath(path = linePath, color = sakuColors.expense, style = strokeStyle)
                        points.forEach { pt ->
                            drawCircle(color = sakuColors.expense, radius = 6f, center = pt)
                        }
                    }
                },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buckets.first().label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buckets.last().label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CategoryProportionBar(
    items: List<CategoryBreakdownItem>,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val totalPermille = items.sumOf { it.percentagePermille }.coerceAtLeast(1)

    val propCd = stringResource(R.string.reports_chart_breakdown_cd, items.size)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { contentDescription = propCd },
    ) {
        val w = size.width
        val h = size.height
        var cursorX = 0f
        items.forEach { item ->
            val segW = (item.percentagePermille / totalPermille.toFloat()) * w
            if (segW > 0f) {
                val segColor = item.colorHex?.let { Color(it) } ?: fallbackColor
                drawRect(
                    color = segColor,
                    topLeft = Offset(cursorX, 0f),
                    size = Size(segW, h),
                )
                cursorX += segW
            }
        }
    }
}
