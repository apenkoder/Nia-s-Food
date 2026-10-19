package com.apenkoder.niasfood.ui.login

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apenkoder.niasfood.BuildConfig
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.data.repo.LoginResult
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.DemoBanner
import com.apenkoder.niasfood.ui.components.LoadingState
import com.apenkoder.niasfood.ui.components.PinDots
import com.apenkoder.niasfood.ui.components.PinPad
import com.apenkoder.niasfood.ui.components.roleText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginMessage {
  data class Wrong(
    val attemptsLeft: Int,
  ) : LoginMessage

  data class Locked(
    val minutes: Int,
  ) : LoginMessage

  data object Unavailable : LoginMessage
}

data class LoginState(
  val loading: Boolean = true,
  val outlets: List<OutletEntity> = emptyList(),
  val profiles: List<StaffEntity> = emptyList(),
  val selected: StaffEntity? = null,
  val pin: String = "",
  val busy: Boolean = false,
  val message: LoginMessage? = null,
)

class LoginViewModel(
  private val c: AppContainer,
) : ViewModel() {
  private val _state = MutableStateFlow(LoginState())
  val state: StateFlow<LoginState> = _state

  fun reset() {
    viewModelScope.launch {
      _state.value = LoginState(outlets = c.auth.outlets(), profiles = c.auth.profiles(), loading = false)
    }
  }

  fun select(staff: StaffEntity?) {
    _state.update { it.copy(selected = staff, pin = "", message = null) }
  }

  fun digit(d: Char) {
    val st = _state.value
    val staff = st.selected ?: return
    if (st.busy || st.pin.length >= staff.role.pinLength) return
    val pin = st.pin + d
    _state.update { it.copy(pin = pin, message = null) }
    if (pin.length == staff.role.pinLength) submit(staff, pin)
  }

  fun backspace() {
    _state.update { if (it.busy || it.pin.isEmpty()) it else it.copy(pin = it.pin.dropLast(1)) }
  }

  private fun submit(
    staff: StaffEntity,
    pin: String,
  ) {
    viewModelScope.launch {
      _state.update { it.copy(busy = true) }
      val result = c.auth.login(staff.id, pin)
      _state.update {
        when (result) {
          is LoginResult.Success -> it.copy(busy = false, pin = "")
          is LoginResult.WrongPin -> it.copy(busy = false, pin = "", message = LoginMessage.Wrong(result.attemptsLeft))
          is LoginResult.Locked ->
            it.copy(
              busy = false,
              pin = "",
              message = LoginMessage.Locked(((result.remainingMs + 59_999L) / 60_000L).toInt()),
            )
          is LoginResult.Unavailable -> it.copy(busy = false, pin = "", message = LoginMessage.Unavailable)
        }
      }
    }
  }
}

@Composable
fun LoginScreen() {
  val vm = containerViewModel<LoginViewModel>("login") { c, _ -> LoginViewModel(c) }
  val state by vm.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { vm.reset() }

  val selected = state.selected
  BackHandler(enabled = selected != null) { vm.select(null) }

  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    if (BuildConfig.DEMO_SEED) DemoBanner()
    when {
      state.loading -> LoadingState()
      selected == null -> ProfilePicker(state, onPick = vm::select)
      else -> PinEntry(state, selected, vm)
    }
  }
}

@Composable
private fun ProfilePicker(
  state: LoginState,
  onPick: (StaffEntity) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.login_welcome), style = MaterialTheme.typography.bodyLarge)
        Text(
          stringResource(R.string.login_pick_profile),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    val owners = state.profiles.filter { it.role == Role.OWNER }
    if (owners.isNotEmpty()) {
      item { SectionLabel(stringResource(R.string.role_owner)) }
      items(owners, key = { it.id }) { ProfileRow(it, onPick) }
    }
    state.outlets.forEach { outlet ->
      val people = state.profiles.filter { it.outletId == outlet.id }
      if (people.isNotEmpty()) {
        item(key = "h-${outlet.id}") { SectionLabel("${outlet.name} · ${outlet.area}") }
        items(people, key = { it.id }) { ProfileRow(it, onPick) }
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp),
  )
}

@Composable
private fun ProfileRow(
  staff: StaffEntity,
  onPick: (StaffEntity) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable { onPick(staff) },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Default.Person, contentDescription = null)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(staff.name, style = MaterialTheme.typography.titleMedium)
        Text(roleText(staff.role), style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun PinEntry(
  state: LoginState,
  staff: StaffEntity,
  vm: LoginViewModel,
) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = { vm.select(null) }) { Text(stringResource(R.string.back)) }
    }
    Text(staff.name, style = MaterialTheme.typography.headlineSmall)
    Text(roleText(staff.role), style = MaterialTheme.typography.bodyMedium)
    PinDots(length = staff.role.pinLength, filled = state.pin.length)
    val message = state.message
    val text =
      when (message) {
        is LoginMessage.Wrong -> stringResource(R.string.login_wrong, message.attemptsLeft)
        is LoginMessage.Locked -> stringResource(R.string.login_locked, message.minutes)
        is LoginMessage.Unavailable -> stringResource(R.string.err_not_found)
        null -> stringResource(R.string.login_enter_pin, staff.role.pinLength)
      }
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = if (message == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    PinPad(onDigit = vm::digit, onBackspace = vm::backspace, enabled = !state.busy)
  }
}
