package com.apenkoder.niasfood.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.core.Outcome
import com.apenkoder.niasfood.core.attempt
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.repo.Summary
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DashRange { TODAY, YESTERDAY, LAST_7 }

data class DashState(
  val loading: Boolean = true,
  val outlets: List<OutletEntity> = emptyList(),
  val outletId: String? = null,
  val range: DashRange = DashRange.TODAY,
  val summary: Summary? = null,
  val chart: List<Pair<String, Long>> = emptyList(),
  val updatedAt: Long? = null,
  val error: AppError? = null,
)

class DashboardViewModel(
  private val c: AppContainer,
  private val session: Session,
) : ViewModel() {
  private val _state = MutableStateFlow(DashState(outletId = session.outletId))
  val state: StateFlow<DashState> = _state

  init {
    viewModelScope.launch {
      _state.update { it.copy(outlets = c.reports.outlets()) }
      load()
    }
  }

  fun setRange(range: DashRange) {
    _state.update { it.copy(range = range) }
    viewModelScope.launch { load() }
  }

  fun setOutlet(id: String?) {
    if (session.role != Role.OWNER) return
    _state.update { it.copy(outletId = id) }
    viewModelScope.launch { load() }
  }

  fun refresh() {
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    val st = _state.value
    val relevant = if (st.outletId == null) st.outlets else st.outlets.filter { it.id == st.outletId }
    val openMinutes = relevant.minOfOrNull { it.openMinutes } ?: 0
    val now = c.clock.nowMillis()
    val today = NairobiTime.businessDate(now, openMinutes)
    val (from, to) =
      when (st.range) {
        DashRange.TODAY -> today to today
        DashRange.YESTERDAY -> NairobiTime.shiftDate(today, -1) to NairobiTime.shiftDate(today, -1)
        DashRange.LAST_7 -> NairobiTime.shiftDate(today, -6) to today
      }
    val result =
      attempt {
        val summary = c.reports.summary(st.outletId, from, to)
        val weekStart = NairobiTime.shiftDate(today, -6)
        val daily = c.reports.daily(st.outletId, weekStart, today)
        val chart =
          (0..6).map { i ->
            val d = NairobiTime.shiftDate(weekStart, i.toLong())
            NairobiTime.shortDate(d).take(3) to (daily[d]?.grossCents ?: 0L)
          }
        summary to chart
      }
    when (result) {
      is Outcome.Ok ->
        _state.update {
          it.copy(loading = false, summary = result.value.first, chart = result.value.second, updatedAt = now, error = null)
        }
      is Outcome.Fail -> _state.update { it.copy(loading = false, error = result.error) }
    }
  }
}

@Composable
fun DashboardScreen(session: Session) {
  val vm = containerViewModel<DashboardViewModel>("dash-${session.staffId}") { c, _ -> DashboardViewModel(c, session) }
  val state by vm.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { vm.refresh() }

  if (state.loading) {
    LoadingState()
    return
  }
  val names = state.outlets.associate { it.id to it.name }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Column {
        Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.headlineSmall)
        val updated = state.updatedAt
        if (updated != null) {
          Text(
            stringResource(R.string.dash_updated, NairobiTime.time(updated)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    val error = state.error
    if (error != null) item { ErrorBanner(errorText(error)) }
    if (session.role == Role.OWNER) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = state.outletId == null,
              onClick = { vm.setOutlet(null) },
              label = { Text(stringResource(R.string.all_outlets)) },
            )
          }
          items(state.outlets, key = { it.id }) { o ->
            FilterChip(selected = state.outletId == o.id, onClick = { vm.setOutlet(o.id) }, label = { Text(o.name) })
          }
        }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.range == DashRange.TODAY, onClick = {
          vm.setRange(DashRange.TODAY)
        }, label = { Text(stringResource(R.string.range_today)) })
        FilterChip(selected = state.range == DashRange.YESTERDAY, onClick = {
          vm.setRange(DashRange.YESTERDAY)
        }, label = { Text(stringResource(R.string.range_yesterday)) })
        FilterChip(selected = state.range == DashRange.LAST_7, onClick = {
          vm.setRange(DashRange.LAST_7)
        }, label = { Text(stringResource(R.string.range_7_days)) })
      }
    }
    val summary = state.summary
    if (summary != null) {
      item { SummaryCards(summary) }
      if (state.outletId == null) item { OutletPerformanceCard(summary.byOutlet, names) }
      item { PaymentSplitCard(summary) }
      item { TopItemsCard(summary) }
    }
    item {
      SectionCard(title = stringResource(R.string.dash_chart_title)) {
        SalesBarChart(state.chart)
      }
    }
  }
}
