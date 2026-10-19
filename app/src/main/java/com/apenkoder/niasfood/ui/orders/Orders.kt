package com.apenkoder.niasfood.ui.orders
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.apenkoder.niasfood.data.local.OrderEntity
import com.apenkoder.niasfood.data.local.OrderLineEntity
import com.apenkoder.niasfood.data.local.PaymentEntity
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.OrderStatus
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.domain.Variant
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersState(
    val held: List<OrderEntity> = emptyList(),
    val day: List<OrderEntity> = emptyList(),
    val expandedId: String? = null,
    val lines: List<OrderLineEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val error: AppError? = null,
    val errorDetail: String? = null
)

class OrdersViewModel(private val c: AppContainer, private val session: Session) : ViewModel() {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state

    init {
        val outletId = session.outletId
        if (outletId != null) {
            viewModelScope.launch { c.sales.observeHeld(outletId).collect { l -> _state.update { it.copy(held = l) } } }
            viewModelScope.launch {
                val outlet = c.db.outletDao().byId(outletId)
                val date = NairobiTime.businessDate(c.clock.nowMillis(), outlet?.openMinutes ?: 0)
                c.sales.observeDay(outletId, date).collect { l -> _state.update { it.copy(day = l) } }
            }
        }
    }

    fun toggle(order: OrderEntity) {
        if (_state.value.expandedId == order.id) {
            _state.update { it.copy(expandedId = null) }
            return
        }
        viewModelScope.launch {
            val lines = c.sales.linesFor(order.id)
            val pays = c.sales.paymentsFor(order.id)
            _state.update { it.copy(expandedId = order.id, lines = lines, payments = pays) }
        }
    }

    fun resume(order: OrderEntity) {
        c.pendingHeldOrderId.value = order.id
    }

    fun void(order: OrderEntity, reason: String, approverId: String?) {
        viewModelScope.launch {
            when (val r = attempt { c.sales.voidOrder(order.id, reason, approverId) }) {
                is Outcome.Ok -> _state.update { it.copy(expandedId = null, error = null) }
                is Outcome.Fail -> _state.update { it.copy(error = r.error, errorDetail = r.detail) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null, errorDetail = null) }
}

@Composable
fun OrdersScreen(session: Session, onResume: () -> Unit) {
    val vm = containerViewModel<OrdersViewModel>("orders-${session.staffId}") { c, _ -> OrdersViewModel(c, session) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var voidTarget by remember { mutableStateOf<OrderEntity?>(null) }
    var approverId by remember { mutableStateOf<String?>(null) }
    var needApproval by remember { mutableStateOf(false) }
    var needReason by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.orders_held, state.held.size)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.orders_today, state.day.size)) })
        }
        val error = state.error
        if (error != null) ErrorBanner(errorText(error, state.errorDetail), onDismiss = vm::dismissError, modifier = Modifier.padding(12.dp))

        val list = if (tab == 0) state.held else state.day
        if (list.isEmpty()) {
            EmptyState(stringResource(if (tab == 0) R.string.orders_no_held else R.string.orders_no_today))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        expanded = state.expandedId == order.id,
                        lines = state.lines,
                        payments = state.payments,
                        onToggle = { vm.toggle(order) },
                        onResume = {
                            vm.resume(order)
                            onResume()
                        },
                        onVoid = {
                            voidTarget = order
                            approverId = null
                            if (Permissions.has(session.role, Permission.VOID_ORDER)) needReason = true else needApproval = true
                        }
                    )
                }
            }
        }
    }

    val target = voidTarget
    if (target != null && needApproval) {
        ManagerApprovalDialog(
            outletId = session.outletId.orEmpty(),
            permission = Permission.VOID_ORDER,
            onApproved = { approver ->
                approverId = approver.id
                needApproval = false
                needReason = true
            },
            onDismiss = { needApproval = false; voidTarget = null }
        )
    }
    if (target != null && needReason) {
        ReasonDialog(
            title = stringResource(R.string.orders_void_title, target.token),
            confirmLabel = stringResource(R.string.orders_void),
            onConfirm = { reason ->
                vm.void(target, reason, approverId)
                needReason = false
                voidTarget = null
            },
            onDismiss = { needReason = false; voidTarget = null }
        )
    }
}

@Composable
private fun OrderCard(
    order: OrderEntity,
    expanded: Boolean,
    lines: List<OrderLineEntity>,
    payments: List<PaymentEntity>,
    onToggle: () -> Unit,
    onResume: () -> Unit,
    onVoid: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.receipt_token, order.token), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.orders_meta,
                            NairobiTime.time(order.createdAt),
                            stringResource(if (order.type == OrderType.DINE_IN) R.string.type_dine_in else R.string.type_takeout),
                            order.createdByName
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money(order.totalCents).format(), style = MaterialTheme.typography.titleMedium)
                    when (order.status) {
                        OrderStatus.PAID -> StatusChip(stringResource(R.string.status_paid), Icons.Default.CheckCircle, positive = true)
                        OrderStatus.VOIDED -> StatusChip(stringResource(R.string.status_voided), Icons.Default.Close, positive = false)
                        OrderStatus.HELD -> StatusChip(stringResource(R.string.status_held), Icons.Default.Info, positive = false)
                    }
                }
            }
            if (expanded) {
                HorizontalDivider()
                lines.filter { it.orderId == order.id }.forEach { l ->
                    val tag = if (l.variant == Variant.HALF) " (" + stringResource(R.string.variant_half_short) + ")" else ""
                    Text("${l.qty} x ${l.name}$tag  ${Money(l.lineTotalCents).format()}", style = MaterialTheme.typography.bodyMedium)
                    if (l.addOnsText.isNotEmpty()) Text("+ ${l.addOnsText}", style = MaterialTheme.typography.bodySmall)
                }
                if (order.note.isNotBlank()) Text(order.note, style = MaterialTheme.typography.bodySmall)
                payments.filter { it.orderId == order.id }.forEach { p ->
                    val label = if (p.method == PaymentMethod.MOBILE_MONEY) {
                        // Payment codes are masked everywhere except the manager's verification list.
                        stringResource(R.string.pay_mobile_money) + " ····" + (p.reference?.takeLast(4) ?: "")
                    } else {
                        stringResource(R.string.pay_cash)
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
                if (order.status == OrderStatus.VOIDED) {
                    Text(stringResource(R.string.orders_void_reason, order.voidReason.orEmpty()), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (order.status == OrderStatus.HELD) {
                        Button(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.orders_resume)) }
                    }
                    if (order.status != OrderStatus.VOIDED) {
                        OutlinedButton(onClick = onVoid, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (order.status == OrderStatus.HELD) R.string.orders_discard else R.string.orders_void))
                        }
                    }
                }
            }
        }
    }
}
