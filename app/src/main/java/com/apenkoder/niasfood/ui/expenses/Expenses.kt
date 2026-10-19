package com.apenkoder.niasfood.ui.expenses
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.apenkoder.niasfood.data.local.ExpenseEntity
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.ExpenseCategory
import com.apenkoder.niasfood.domain.ExpenseChannel
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesState(
  val outlets: List<OutletEntity> = emptyList(),
  val outletId: String? = null,
  val recent: List<ExpenseEntity> = emptyList(),
  val todayCents: Long = 0L,
  val savedCount: Int = 0,
  val busy: Boolean = false,
  val error: AppError? = null,
  val errorDetail: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
  private val c: AppContainer,
  private val session: Session,
) : ViewModel() {
  private val _state = MutableStateFlow(ExpensesState(outletId = session.outletId))
  val state: StateFlow<ExpensesState> = _state
  private val selected = MutableStateFlow(session.outletId)

  init {
    viewModelScope.launch {
      val outlets = c.reports.outlets()
      val first = session.outletId ?: outlets.firstOrNull()?.id
      _state.update { it.copy(outlets = outlets, outletId = first) }
      selected.value = first
      refreshToday()
    }
    viewModelScope.launch {
      selected.flatMapLatest { id -> c.sales.observeExpenses(id) }.collect { list ->
        _state.update { it.copy(recent = list) }
        refreshToday()
      }
    }
  }

  private suspend fun refreshToday() {
    val id = selected.value ?: return
    val outlet = _state.value.outlets.firstOrNull { it.id == id }
    val date = NairobiTime.businessDate(c.clock.nowMillis(), outlet?.openMinutes ?: 0)
    val total = attempt { c.reports.summary(id, date, date).expensesCents }
    if (total is Outcome.Ok) _state.update { it.copy(todayCents = total.value) }
  }

  fun selectOutlet(id: String) {
    if (session.role != Role.OWNER) return
    selected.value = id
    _state.update { it.copy(outletId = id) }
  }

  fun save(
    category: ExpenseCategory,
    amountText: String,
    channel: ExpenseChannel,
    description: String,
  ) {
    val outletId = selected.value ?: return
    val amount = Money.parse(amountText)
    if (amount == null) {
      _state.update { it.copy(error = AppError.AMOUNT_INVALID, errorDetail = null) }
      return
    }
    viewModelScope.launch {
      _state.update { it.copy(busy = true, error = null) }
      when (val r = attempt { c.sales.recordExpense(outletId, category, amount.cents, channel, description) }) {
        is Outcome.Ok -> _state.update { it.copy(busy = false, savedCount = it.savedCount + 1) }
        is Outcome.Fail -> _state.update { it.copy(busy = false, error = r.error, errorDetail = r.detail) }
      }
    }
  }

  fun void(
    expense: ExpenseEntity,
    reason: String,
  ) {
    viewModelScope.launch {
      when (val r = attempt { c.sales.voidExpense(expense.id, reason) }) {
        is Outcome.Ok -> refreshToday()
        is Outcome.Fail -> _state.update { it.copy(error = r.error, errorDetail = r.detail) }
      }
    }
  }

  fun dismissError() = _state.update { it.copy(error = null, errorDetail = null) }
}

@Composable
private fun channelText(channel: ExpenseChannel): String =
  stringResource(
    when (channel) {
      ExpenseChannel.MOBILE_MONEY_TILL -> R.string.chan_mm_till
      ExpenseChannel.PETTY_CASH -> R.string.chan_petty
      ExpenseChannel.CASH_FROM_TILL -> R.string.chan_cash_till
    },
  )

@Composable
fun ExpensesScreen(session: Session) {
  val vm = containerViewModel<ExpensesViewModel>("expenses-${session.staffId}") { c, _ -> ExpensesViewModel(c, session) }
  val state by vm.state.collectAsStateWithLifecycle()
  val context = androidx.compose.ui.platform.LocalContext.current

  var category by rememberSaveable { mutableStateOf(ExpenseCategory.INGREDIENTS) }
  var amountText by rememberSaveable { mutableStateOf("") }
  var channel by rememberSaveable { mutableStateOf(ExpenseChannel.PETTY_CASH) }
  var description by rememberSaveable { mutableStateOf("") }
  var voidTarget by remember { mutableStateOf<ExpenseEntity?>(null) }

  LaunchedEffect(state.savedCount) {
    if (state.savedCount > 0) {
      amountText = ""
      description = ""
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (session.role == Role.OWNER) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(state.outlets, key = { it.id }) { o ->
            FilterChip(selected = state.outletId == o.id, onClick = { vm.selectOutlet(o.id) }, label = { Text(o.name) })
          }
        }
      }
    }
    item {
      SectionCard(title = stringResource(R.string.exp_today_outflow)) {
        Text(Money(state.todayCents).format(), style = MaterialTheme.typography.headlineMedium)
      }
    }
    val error = state.error
    if (error != null) item { ErrorBanner(errorText(error, state.errorDetail), onDismiss = vm::dismissError) }

    item {
      SectionCard(title = stringResource(R.string.exp_record)) {
        Text(stringResource(R.string.exp_category), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(ExpenseCategory.values().toList(), key = { it.name }) { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(ZReportPdfWriter.categoryLabel(context, cat)) },
            )
          }
        }
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(10) },
          singleLine = true,
          label = { Text(stringResource(R.string.exp_amount)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.exp_channel), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(ExpenseChannel.values().toList(), key = { it.name }) { ch ->
            FilterChip(selected = channel == ch, onClick = { channel = ch }, label = { Text(channelText(ch)) })
          }
        }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it.take(100) },
          singleLine = true,
          label = { Text(stringResource(R.string.exp_description)) },
          modifier = Modifier.fillMaxWidth(),
        )
        Button(
          onClick = { vm.save(category, amountText, channel, description) },
          enabled = !state.busy && state.outletId != null,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.exp_save)) }
      }
    }

    item { Text(stringResource(R.string.exp_recent), style = MaterialTheme.typography.titleMedium) }
    if (state.recent.isEmpty()) item { EmptyState(stringResource(R.string.exp_none)) }
    items(state.recent, key = { it.id }) { e ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(e.description, style = MaterialTheme.typography.titleSmall)
            Text(
              stringResource(
                R.string.exp_meta,
                ZReportPdfWriter.categoryLabel(context, e.category),
                NairobiTime.shortDate(e.businessDate),
                e.createdByName,
              ),
              style = MaterialTheme.typography.bodySmall,
            )
            if (e.voided) StatusChip(stringResource(R.string.status_voided), Icons.Default.Close, positive = false)
          }
          Text(Money(e.amountCents).format(), style = MaterialTheme.typography.titleMedium)
          if (!e.voided) {
            IconButton(onClick = { voidTarget = e }) {
              Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.exp_void))
            }
          }
        }
      }
    }
  }

  val target = voidTarget
  if (target != null) {
    ReasonDialog(
      title = stringResource(R.string.exp_void),
      confirmLabel = stringResource(R.string.exp_void),
      onConfirm = { reason ->
        vm.void(target, reason)
        voidTarget = null
      },
      onDismiss = { voidTarget = null },
    )
  }
}
