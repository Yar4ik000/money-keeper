package com.moneykeeper.feature.analytics.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp
import com.moneykeeper.feature.analytics.ui.analytics.CategoryExpense

@Composable
fun ExpensesPieChart(data: List<CategoryExpense>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    Canvas(modifier = modifier) {
        val strokeWidth = 40.dp.toPx()
        var startAngle = -90f
        // inset by half the stroke width so the arc stays fully within the canvas bounds
        inset(strokeWidth / 2f) {
            data.forEach { item ->
                val sweep = item.percentage / 100f * 360f
                val color = try {
                    val hex = item.category.colorHex
                    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
                } catch (_: IllegalArgumentException) {
                    Color.Gray
                }
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
    }
}
