package com.apenkoder.niasfood.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.Money
import com.apenkoder.niasfood.data.repo.Summary

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The four headline numbers. Dashboard, Reports and the Z-report all read the same [Summary]. */
@Composable
fun SummaryCards(summary: Summary, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                stringResource(R.string.metric_gross), Money(summary.grossCents).format(), Modifier.weight(1f),
                stringResource(R.string.metric_tickets_note, summary.tickets)
            )
            MetricCard(stringResource(R.string.metric_expenses), Money(summary.expensesCents).format(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                stringResource(R.string.metric_net), Money(summary.netCashflowCents).format(), Modifier.weight(1f),
                stringResource(R.string.metric_net_note)
            )
            MetricCard(
                stringResource(R.string.metric_avg_ticket), Money(summary.avgTicketCents).format(), Modifier.weight(1f),
                stringResource(
                    R.string.metric_avg_note,
                    Money(summary.avgDineInCents).format(), Money(summary.avgTakeoutCents).format()
                )
            )
        }
    }
}

@Composable
fun PaymentSplitCard(summary: Summary) {
    SectionCard(title = stringResource(R.string.payment_split)) {
        SplitRow(stringResource(R.string.pay_cash), summary.cashCents)
        SplitRow(stringResource(R.string.pay_mm_verified), summary.mmVerifiedCents)
        SplitRow(stringResource(R.string.pay_mm_unverified), summary.mmUnverifiedCents)
    }
}

@Composable
private fun SplitRow(label: String, cents: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(Money(cents).format(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OutletPerformanceCard(byOutlet: Map<String, Long>, names: Map<String, String>) {
    val total = byOutlet.values.sum()
    SectionCard(title = stringResource(R.string.outlet_performance)) {
        if (total == 0L) {
            Text(stringResource(R.string.no_sales_in_range), style = MaterialTheme.typography.bodyMedium)
        } else {
            byOutlet.entries.sortedByDescending { it.value }.forEach { (id, cents) ->
                val percent = (cents * 100L) / total
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(names[id].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.outlet_share, Money(cents).format(), percent.toInt()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { cents.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TopItemsCard(summary: Summary) {
    SectionCard(title = stringResource(R.string.top_items)) {
        if (summary.topItems.isEmpty()) {
            Text(stringResource(R.string.no_sales_in_range), style = MaterialTheme.typography.bodyMedium)
        } else {
            summary.topItems.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.top_item_line, item.name, item.qty), style = MaterialTheme.typography.bodyMedium)
                    Text(Money(item.revenueCents).format(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Simple bar chart drawn with Canvas. No chart library. */
@Composable
fun SalesBarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            if (values.isEmpty()) return@Canvas
            val gap = 8.dp.toPx()
            val barWidth = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { index, (_, cents) ->
                val h = size.height * (cents.toFloat() / max.toFloat())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * (barWidth + gap), size.height - h),
                    size = Size(barWidth, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            values.forEach { (label, _) -> Text(label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
