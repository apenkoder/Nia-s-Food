package com.apenkoder.niasfood.ui.setup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.core.Outcome
import com.apenkoder.niasfood.core.attempt
import com.apenkoder.niasfood.data.repo.AuthService
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.ErrorBanner
import com.apenkoder.niasfood.ui.components.errorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupState(
  val busy: Boolean = false,
  val error: AppError? = null,
  val done: Boolean = false,
)

class SetupViewModel(
  private val c: AppContainer,
) : ViewModel() {
  private val _state = MutableStateFlow(SetupState())
  val state: StateFlow<SetupState> = _state

  fun submit(
    ownerName: String,
    pin: String,
    pinAgain: String,
    outlets: List<AuthService.OutletInput?>,
  ) {
    if (pin != pinAgain) {
      _state.update { it.copy(error = AppError.PIN_MISMATCH) }
      return
    }
    val valid = outlets.filterNotNull()
    if (valid.size != outlets.size) {
      _state.update { it.copy(error = AppError.BAD_TIME) }
      return
    }
    viewModelScope.launch {
      _state.update { it.copy(busy = true, error = null) }
      when (val r = attempt { c.auth.completeSetup(ownerName, pin, valid) }) {
        is Outcome.Ok -> _state.update { it.copy(busy = false, done = true) }
        is Outcome.Fail -> _state.update { it.copy(busy = false, error = r.error) }
      }
    }
  }
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
  val vm = containerViewModel<SetupViewModel>("setup") { c, _ -> SetupViewModel(c) }
  val state by vm.state.collectAsStateWithLifecycle()
  LaunchedEffect(state.done) { if (state.done) onDone() }

  var ownerName by rememberSaveable { mutableStateOf("") }
  var pin by rememberSaveable { mutableStateOf("") }
  var pinAgain by rememberSaveable { mutableStateOf("") }
  var name1 by rememberSaveable { mutableStateOf("") }
  var area1 by rememberSaveable { mutableStateOf("") }
  var open1 by rememberSaveable { mutableStateOf("06:00") }
  var close1 by rememberSaveable { mutableStateOf("22:00") }
  var second by rememberSaveable { mutableStateOf(false) }
  var name2 by rememberSaveable { mutableStateOf("") }
  var area2 by rememberSaveable { mutableStateOf("") }
  var open2 by rememberSaveable { mutableStateOf("06:00") }
  var close2 by rememberSaveable { mutableStateOf("22:00") }

  fun outletInput(
    name: String,
    area: String,
    open: String,
    close: String,
  ): AuthService.OutletInput? {
    val o = NairobiTime.parseMinutes(open) ?: return null
    val c = NairobiTime.parseMinutes(close) ?: return null
    return AuthService.OutletInput(name, area, o, c)
  }

  Column(
    Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)

    Text(stringResource(R.string.setup_owner), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(ownerName, {
      ownerName = it
    }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
      pin,
      { pin = it.filter { ch -> ch in '0'..'9' }.take(6) },
      label = { Text(stringResource(R.string.setup_owner_pin)) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      pinAgain,
      { pinAgain = it.filter { ch -> ch in '0'..'9' }.take(6) },
      label = { Text(stringResource(R.string.setup_owner_pin_again)) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
      modifier = Modifier.fillMaxWidth(),
    )

    Text(stringResource(R.string.setup_outlet_n, 1), style = MaterialTheme.typography.titleMedium)
    OutletFields(name1, { name1 = it }, area1, { area1 = it }, open1, { open1 = it }, close1, { close1 = it })

    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = second, onCheckedChange = { second = it })
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.setup_second_outlet))
    }
    if (second) {
      Text(stringResource(R.string.setup_outlet_n, 2), style = MaterialTheme.typography.titleMedium)
      OutletFields(name2, { name2 = it }, area2, { area2 = it }, open2, { open2 = it }, close2, { close2 = it })
    }

    val error = state.error
    if (error != null) ErrorBanner(errorText(error))

    Button(
      onClick = {
        val list =
          buildList<AuthService.OutletInput?> {
            add(outletInput(name1, area1, open1, close1))
            if (second) add(outletInput(name2, area2, open2, close2))
          }
        vm.submit(ownerName, pin, pinAgain, list)
      },
      enabled = !state.busy,
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(if (state.busy) R.string.setup_working else R.string.setup_finish)) }
  }
}

@Composable
private fun OutletFields(
  name: String,
  onName: (String) -> Unit,
  area: String,
  onArea: (String) -> Unit,
  open: String,
  onOpen: (String) -> Unit,
  close: String,
  onClose: (String) -> Unit,
) {
  OutlinedTextField(name, onName, label = {
    Text(stringResource(R.string.field_outlet_name))
  }, singleLine = true, modifier = Modifier.fillMaxWidth())
  OutlinedTextField(area, onArea, label = {
    Text(stringResource(R.string.field_area))
  }, singleLine = true, modifier = Modifier.fillMaxWidth())
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      open,
      onOpen,
      label = { Text(stringResource(R.string.field_opens)) },
      singleLine = true,
      modifier = Modifier.weight(1f),
    )
    OutlinedTextField(close, onClose, label = {
      Text(stringResource(R.string.field_closes))
    }, singleLine = true, modifier = Modifier.weight(1f))
  }
}
