package com.apenkoder.niasfood.ui.reports
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.Money
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.core.Outcome
import com.apenkoder.niasfood.core.attempt
import com.apenkoder.niasfood.data.export.ZReportPdfWriter
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.PaymentEntity
import com.apenkoder.niasfood.data.local.ShiftEntity
import com.apenkoder.niasfood.data.repo.ShiftOverview
import com.apenkoder.niasfood.data.repo.Summary
import com.apenkoder.niasfood.data.repo.ZReportData
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.domain.Verification
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ReportRange { TODAY, LAST_7, LAST_30 }
enum class ReportTab { SHIFT, Z_REPORTS, SUMMARY }

data class ReportsState(
    val outlets: List<OutletEntity> = emptyList(),
    val outletId: String? = null,
    val overview: ShiftOverview? = null,
    val closed: List<ShiftEntity> = emptyList(),
    val selectedZ: ZReportData? = null,
    val range: ReportRange = ReportRange.TODAY,
    val summary: Summary? = null,
    val daily: List<Pair<String, Summary>> = emptyList(),
    val busy: Boolean = false,
    val error: AppError? = null,
    val errorDetail: String? = null
)

private class Loaded(
    val closed: List<ShiftEntity>,
    val summary: Summary,
    val daily: List<Pair<String, Summary>>,
    val overview: ShiftOverview?
)

class ReportsViewModel(private val c: AppContainer, private val session: Session) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState(outletId = session.outletId))
    val state: StateFlow<ReportsState> = _state

    init {
        viewModelScope.launch {
            _state.update { it.copy(outlets = c.reports.outlets()) }
            refresh()
        }
    }

    fun setOutlet(id: String?) {
        if (session.role != Role.OWNER) return
        _state.update { it.copy(outletId = id, selectedZ = null) }
        viewModelScope.launch { refresh() }
    }

    fun setRange(range: ReportRange) {
        _state.update { it.copy(range = range) }
        viewModelScope.launch { refresh() }
    }

    fun clearZ() = _state.update { it.copy(selectedZ = null) }
    fun dismissError() = _state.update { it.copy(error = null, errorDetail = null) }

    fun reload() {
        viewModelScope.launch { refresh() }
    }

    private fun fail(f: Outcome.Fail) = _state.update { it.copy(busy = false, error = f.error, errorDetail = f.detail) }

    private suspend fun refresh() {
        val st = _state.value
        val relevant = if (st.outletId == null) st.outlets else st.outlets.filter { it.id == st.outletId }
        val today = NairobiTime.businessDate(c.clock.nowMillis(), relevant.minOfOrNull { it.openMinutes } ?: 0)
        val from = when (st.range) {
            ReportRange.TODAY -> today
            ReportRange.LAST_7 -> NairobiTime.shiftDate(today, -6)
            ReportRange.LAST_30 -> NairobiTime.shiftDate(today, -29)
        }
        val result = attempt {
            val closed = c.reports.closedShifts(st.outletId)
            val summary = c.reports.summary(st.outletId, from, today)
            val daily = c.reports.daily(st.outletId, from, today).entries
                .sortedByDescending { it.key }
                .map { it.key to it.value }
            val ownOutlet = session.outletId
            val overview = if (session.role == Role.MANAGER && ownOutlet != null) {
                c.sales.currentShift(ownOutlet)?.let { shift -> c.sales.overview(shift.id) }
            } else {
                null
            }
            Loaded(closed, summary, daily, overview)
        }
        when (result) {
            is Outcome.Ok -> _state.update {
                it.copy(
                    closed = result.value.closed, summary = result.value.summary,
                    daily = result.value.daily, overview = result.value.overview
                )
            }
            is Outcome.Fail -> fail(result)
        }
    }

    // ---------- shift ----------

    fun openShift(floatText: String) {
        val outletId = session.outletId ?: return
        val money = Money.parse(floatText)
        if (money == null) {
            _state.update { it.copy(error = AppError.AMOUNT_INVALID, errorDetail = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val r = attempt { c.sales.openShift(outletId, money.cents) }) {
                is Outcome.Ok -> { _state.update { it.copy(busy = false) }; refresh() }
                is Outcome.Fail -> fail(r)
            }
        }
    }

    fun verify(payment: PaymentEntity, verified: Boolean, reason: String?) {
        viewModelScope.launch {
            when (val r = attempt { c.sales.verifyPayment(payment.id, verified, reason) }) {
                is Outcome.Ok -> refresh()
                is Outcome.Fail -> fail(r)
            }
        }
    }

    fun closeShift(shiftId: String, countedText: String, reason: String) {
        val counted = Money.parse(countedText)
        if (counted == null) {
            _state.update { it.copy(error = AppError.AMOUNT_INVALID, errorDetail = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val r = attempt { c.sales.closeShift(shiftId, counted.cents, reason) }) {
                is Outcome.Ok -> {
                    val zData = when (val z = attempt { c.reports.zReport(shiftId) }) {
                        is Outcome.Ok -> z.value
                        is Outcome.Fail -> null
                    }
                    _state.update { it.copy(busy = false, selectedZ = zData) }
                    refresh()
                }
                is Outcome.Fail -> fail(r)
            }
        }
    }

    fun selectZ(shiftId: String) {
        viewModelScope.launch {
            when (val r = attempt { c.reports.zReport(shiftId) }) {
                is Outcome.Ok -> _state.update { it.copy(selectedZ = r.value) }
                is Outcome.Fail -> fail(r)
            }
        }
    }

    suspend fun buildPdf(shiftId: String): File? {
        val r = attempt {
            withContext(Dispatchers.IO) {
                val file = ZReportPdfWriter.write(c.appContext, c.reports.zReport(shiftId))
                c.reports.recordExport(shiftId)
                file
            }
        }
        return when (r) {
            is Outcome.Ok -> r.value
            is Outcome.Fail -> { fail(r); null }
        }
    }
}

@Composable
fun ReportsScreen(session: Session) {
    val vm = containerViewModel<ReportsViewModel>("reports-${session.staffId}") { c, _ -> ReportsViewModel(c, session) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reload() }

    val tabs = if (session.role == Role.MANAGER) {
        listOf(ReportTab.SHIFT, ReportTab.Z_REPORTS, ReportTab.SUMMARY)
    } else {
        listOf(ReportTab.Z_REPORTS, ReportTab.SUMMARY)
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val zTabIndex = tabs.indexOf(ReportTab.Z_REPORTS)
    // A freshly closed shift opens its Z-report straight away.
    LaunchedEffect(state.selectedZ?.shift?.id) { if (state.selectedZ != null) tabIndex = zTabIndex }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex.coerceIn(0, tabs.size - 1)) {
            tabs.forEachIndexed { i, tab ->
                Tab(
                    selected = tabIndex == i,
                    onClick = { tabIndex = i },
                    text = {
                        Text(
                            stringResource(
                                when (tab) {
                                    ReportTab.SHIFT -> R.string.rep_tab_shift
                                    ReportTab.Z_REPORTS -> R.string.rep_tab_z
                                    ReportTab.SUMMARY -> R.string.rep_tab_summary
                                }
                            )
                        )
                    }
                )
            }
        }
        val error = state.error
        if (error != null) ErrorBanner(errorText(error, state.errorDetail), onDismiss = vm::dismissError, modifier = Modifier.padding(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (session.role == Role.OWNER && tabs[tabIndex.coerceIn(0, tabs.size - 1)] != ReportTab.SHIFT) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = state.outletId == null, onClick = { vm.setOutlet(null) }, label = { Text(stringResource(R.string.all_outlets)) })
                        }
                        items(state.outlets, key = { it.id }) { o ->
                            FilterChip(selected = state.outletId == o.id, onClick = { vm.setOutlet(o.id) }, label = { Text(o.name) })
                        }
                    }
                }
            }
            when (tabs[tabIndex.coerceIn(0, tabs.size - 1)]) {
                ReportTab.SHIFT -> shiftItems(state, vm)
                ReportTab.Z_REPORTS -> zItems(state, vm)
                ReportTab.SUMMARY -> summaryItems(state, vm)
            }
        }
    }
}

// ---------------------------------------------------------------- shift tab

private fun androidx.compose.foundation.lazy.LazyListScope.shiftItems(state: ReportsState, vm: ReportsViewModel) {
    val overview = state.overview
    if (overview == null) {
        item { OpenShiftCard(state.busy, onOpen = vm::openShift) }
    } else {
        item { ShiftCard(state, overview, vm) }
    }
}

@Composable
private fun OpenShiftCard(busy: Boolean, onOpen: (String) -> Unit) {
    var floatText by rememberSaveable { mutableStateOf("") }
    SectionCard(title = stringResource(R.string.shift_none_title)) {
        Text(stringResource(R.string.shift_none_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = floatText,
            onValueChange = { floatText = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(10) },
            singleLine = true, label = { Text(stringResource(R.string.shift_opening_float)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onOpen(floatText) },
            enabled = !busy && floatText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(stringResource(R.string.shift_open_action)) }
    }
}

@Composable
private fun ShiftCard(state: ReportsState, overview: ShiftOverview, vm: ReportsViewModel) {
    var countedText by rememberSaveable { mutableStateOf("") }
    var varianceReason by rememberSaveable { mutableStateOf("") }
    var flagTarget by remember { mutableStateOf<PaymentEntity?>(null) }
    val counted = Money.parse(countedText)?.cents
    val variance = if (counted != null) counted - overview.expectedCashCents else null
    val unverified = overview.mobileMoney.count { it.verification == Verification.UNVERIFIED }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionCard(title = stringResource(R.string.shift_current, overview.shift.number)) {
            Text(
                stringResource(R.string.shift_opened_at, NairobiTime.time(overview.shift.openedAt), overview.shift.openedByName),
                style = MaterialTheme.typography.bodySmall
            )
            FigureRow(stringResource(R.string.pdf_opening_float), overview.shift.openingFloatCents)
            FigureRow(stringResource(R.string.shift_cash_sales), overview.cashSalesCents)
            FigureRow(stringResource(R.string.shift_cash_expenses), -overview.cashExpensesCents)
            FigureRow(stringResource(R.string.pdf_expected_cash), overview.expectedCashCents, bold = true)
        }

        SectionCard(title = stringResource(R.string.shift_verify_title)) {
            if (overview.mobileMoney.isEmpty()) {
                Text(stringResource(R.string.shift_no_mm), style = MaterialTheme.typography.bodyMedium)
            }
            overview.mobileMoney.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // Full code is shown here so the manager can match it to the till statement.
                        Text(p.reference.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text(Money(p.amountCents).format(), style = MaterialTheme.typography.bodySmall)
                    }
                    when (p.verification) {
                        Verification.VERIFIED -> StatusChip(stringResource(R.string.verified), Icons.Default.CheckCircle, positive = true)
                        Verification.FLAGGED -> StatusChip(stringResource(R.string.flagged), Icons.Default.Warning, positive = false)
                        else -> {
                            TextButton(onClick = { vm.verify(p, true, null) }) { Text(stringResource(R.string.verify)) }
                            TextButton(onClick = { flagTarget = p }) { Text(stringResource(R.string.flag)) }
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.shift_close_title)) {
            OutlinedTextField(
                value = countedText,
                onValueChange = { countedText = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(10) },
                singleLine = true, label = { Text(stringResource(R.string.shift_counted_cash)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (variance != null) {
                FigureRow(stringResource(R.string.pdf_variance), variance, bold = true)
                if (variance != 0L) {
                    OutlinedTextField(
                        value = varianceReason, onValueChange = { varianceReason = it.take(100) }, singleLine = true,
                        label = { Text(stringResource(R.string.shift_variance_reason)) }, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (unverified > 0) {
                Text(stringResource(R.string.shift_unverified_hint, unverified), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { vm.closeShift(overview.shift.id, countedText, varianceReason) },
                enabled = !state.busy && counted != null && unverified == 0 &&
                    (variance == 0L || varianceReason.isNotBlank()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(stringResource(R.string.shift_close_action)) }
        }
    }

    val flag = flagTarget
    if (flag != null) {
        ReasonDialog(
            title = stringResource(R.string.flag),
            confirmLabel = stringResource(R.string.flag),
            onConfirm = { reason ->
                vm.verify(flag, false, reason)
                flagTarget = null
            },
            onDismiss = { flagTarget = null }
        )
    }
}

@Composable
private fun FigureRow(label: String, cents: Long, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text(Money(cents).format(), style = style)
    }
}

// ---------------------------------------------------------------- Z-report tab

private fun androidx.compose.foundation.lazy.LazyListScope.zItems(state: ReportsState, vm: ReportsViewModel) {
    val z = state.selectedZ
    if (z != null) {
        item { ZDetail(z, onBack = vm::clearZ, vm = vm) }
        return
    }
    if (state.closed.isEmpty()) {
        item { EmptyState(stringResource(R.string.z_none)) }
        return
    }
    val names = state.outlets.associate { it.id to it.name }
    items(state.closed, key = { it.id }) { s ->
        Card(
            modifier = Modifier.fillMaxWidth().clickable { vm.selectZ(s.id) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.z_list_title, s.zNumber ?: 0, names[s.outletId].orEmpty()), style = MaterialTheme.typography.titleMedium)
                    Text(NairobiTime.shortDate(s.businessDate), style = MaterialTheme.typography.bodySmall)
                }
                val v = s.varianceCents ?: 0L
                if (v == 0L) {
                    StatusChip(stringResource(R.string.z_balanced), Icons.Default.CheckCircle, positive = true)
                } else {
                    StatusChip(Money(v).format(), Icons.Default.Warning, positive = false)
                }
            }
        }
    }
}

@Composable
private fun ZDetail(z: ZReportData, onBack: () -> Unit, vm: ReportsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = z.shift
    val sum = z.summary
    val variance = s.varianceCents ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.pdf_title, z.outletName), style = MaterialTheme.typography.titleMedium)
        }
        SectionCard {
            Text(
                stringResource(R.string.pdf_subtitle, s.zNumber ?: 0, NairobiTime.shortDate(s.businessDate)),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.pdf_manager, s.closedByName ?: s.openedByName), style = MaterialTheme.typography.bodySmall)
            if (variance == 0L) {
                StatusChip(stringResource(R.string.z_balanced), Icons.Default.CheckCircle, positive = true)
            } else {
                StatusChip(stringResource(R.string.z_variance_chip, Money(variance).format()), Icons.Default.Warning, positive = false)
            }
        }
        SectionCard(title = stringResource(R.string.pdf_section_sales)) {
            Text(stringResource(R.string.pdf_tickets) + ": " + sum.tickets, style = MaterialTheme.typography.bodyMedium)
            FigureRow(stringResource(R.string.pdf_cash), sum.cashCents)
            FigureRow(stringResource(R.string.pdf_mm_verified), sum.mmVerifiedCents)
            FigureRow(stringResource(R.string.pdf_mm_unverified), sum.mmUnverifiedCents)
            FigureRow(stringResource(R.string.pdf_gross), sum.grossCents, bold = true)
            FigureRow(stringResource(R.string.pdf_voids, sum.voidedCount), sum.voidedCents)
        }
        SectionCard(title = stringResource(R.string.pdf_section_expenses)) {
            ExpenseCategory.values().forEach { cat ->
                val amount = sum.expensesByCategory[cat] ?: return@forEach
                FigureRow(ZReportPdfWriter.categoryLabel(context, cat), amount)
            }
            FigureRow(stringResource(R.string.pdf_expenses_total), sum.expensesCents, bold = true)
        }
        SectionCard(title = stringResource(R.string.pdf_section_cash)) {
            FigureRow(stringResource(R.string.pdf_opening_float), s.openingFloatCents)
            FigureRow(stringResource(R.string.pdf_expected_cash), z.expectedCashCents)
            FigureRow(stringResource(R.string.pdf_counted_cash), s.countedCashCents ?: 0L)
            FigureRow(stringResource(R.string.pdf_variance), variance, bold = true)
            val reason = s.varianceReason
            if (reason != null) Text(stringResource(R.string.pdf_variance_reason, reason), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.pdf_flagged) + ": " + z.flaggedCount, style = MaterialTheme.typography.bodySmall)
        }
        SectionCard {
            FigureRow(stringResource(R.string.pdf_net_label), sum.netCashflowCents, bold = true)
            Text(stringResource(R.string.pdf_footer), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                scope.launch {
                    val file = vm.buildPdf(s.id)
                    if (file != null) context.startActivity(ZReportPdfWriter.shareIntent(context, file))
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.share_report))
        }
    }
}

// ---------------------------------------------------------------- summary tab

private fun androidx.compose.foundation.lazy.LazyListScope.summaryItems(state: ReportsState, vm: ReportsViewModel) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.range == ReportRange.TODAY, onClick = { vm.setRange(ReportRange.TODAY) }, label = { Text(stringResource(R.string.range_today)) })
            FilterChip(selected = state.range == ReportRange.LAST_7, onClick = { vm.setRange(ReportRange.LAST_7) }, label = { Text(stringResource(R.string.range_7_days)) })
            FilterChip(selected = state.range == ReportRange.LAST_30, onClick = { vm.setRange(ReportRange.LAST_30) }, label = { Text(stringResource(R.string.range_30_days)) })
        }
    }
    val summary = state.summary
    if (summary == null) {
        item { LoadingState() }
        return
    }
    item { SummaryCards(summary) }
    item { PaymentSplitCard(summary) }
    item {
        SectionCard(title = stringResource(R.string.rep_by_day)) {
            if (state.daily.isEmpty()) Text(stringResource(R.string.no_sales_in_range), style = MaterialTheme.typography.bodyMedium)
            state.daily.forEach { (date, s) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(NairobiTime.shortDate(date), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.rep_day_line, Money(s.grossCents).format(), Money(s.expensesCents).format()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
