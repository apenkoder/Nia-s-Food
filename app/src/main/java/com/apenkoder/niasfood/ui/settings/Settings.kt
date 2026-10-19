package com.apenkoder.niasfood.ui.settings
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.apenkoder.niasfood.core.Money
import com.apenkoder.niasfood.core.Outcome
import com.apenkoder.niasfood.core.attempt
import com.apenkoder.niasfood.data.local.CategoryEntity
import com.apenkoder.niasfood.data.local.MenuItemEntity
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val categories: List<CategoryEntity> = emptyList(),
    val items: List<MenuItemEntity> = emptyList(),
    val staff: List<StaffEntity> = emptyList(),
    val outlets: List<OutletEntity> = emptyList(),
    val busy: Boolean = false,
    val error: AppError? = null,
    val errorDetail: String? = null
)

class SettingsViewModel(private val c: AppContainer, private val session: Session) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch { c.menu.categories().collect { l -> _state.update { it.copy(categories = l) } } }
        viewModelScope.launch { c.menu.items().collect { l -> _state.update { it.copy(items = l) } } }
        viewModelScope.launch {
            _state.update { it.copy(outlets = c.auth.outlets()) }
            loadStaff()
        }
    }

    private suspend fun loadStaff() {
        when (val r = attempt { c.auth.staffList() }) {
            is Outcome.Ok -> _state.update { it.copy(staff = r.value) }
            is Outcome.Fail -> _state.update { it.copy(error = r.error, errorDetail = r.detail) }
        }
    }

    private fun <T> launchAction(block: suspend () -> T, afterOk: suspend () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val r = attempt(block)) {
                is Outcome.Ok -> {
                    _state.update { it.copy(busy = false) }
                    afterOk()
                }
                is Outcome.Fail -> _state.update { it.copy(busy = false, error = r.error, errorDetail = r.detail) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null, errorDetail = null) }

    private fun reject(error: AppError) = _state.update { it.copy(error = error, errorDetail = null) }

    fun saveItem(
        id: String?, categoryId: String, name: String, description: String,
        priceText: String, halfText: String, active: Boolean
    ) {
        val price = Money.parse(priceText)
        if (price == null) {
            reject(AppError.AMOUNT_INVALID)
            return
        }
        val half = if (halfText.isBlank()) null else Money.parse(halfText)
        if (halfText.isNotBlank() && half == null) {
            reject(AppError.AMOUNT_INVALID)
            return
        }
        launchAction({ c.menu.saveItem(id, categoryId, name, description, price.cents, half?.cents, active) })
    }

    fun addCategory(name: String) = launchAction({ c.menu.addCategory(name) })

    fun addStaff(name: String, role: Role, outletId: String?, pin: String, pinAgain: String) {
        if (pin != pinAgain) {
            reject(AppError.PIN_MISMATCH)
            return
        }
        launchAction({ c.auth.addStaff(name, role, outletId, pin) }, afterOk = { loadStaff() })
    }

    fun resetPin(id: String, pin: String, pinAgain: String) {
        if (pin != pinAgain) {
            reject(AppError.PIN_MISMATCH)
            return
        }
        launchAction({ c.auth.resetPin(id, pin) })
    }

    fun setActive(id: String, active: Boolean) = launchAction({ c.auth.setActive(id, active) }, afterOk = { loadStaff() })
}

@Composable
fun SettingsScreen(session: Session) {
    val vm = containerViewModel<SettingsViewModel>("settings-${session.staffId}") { c, _ -> SettingsViewModel(c, session) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.set_tab_menu)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.set_tab_staff)) })
        }
        val error = state.error
        if (error != null) ErrorBanner(errorText(error, state.errorDetail), onDismiss = vm::dismissError, modifier = Modifier.padding(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tab == 0) menuItems(state, vm) else staffItems(state, vm, session)
        }
    }
}

// ---------------------------------------------------------------- menu

private fun LazyListScope.menuItems(state: SettingsState, vm: SettingsViewModel) {
    item {
        MenuToolbar(state, vm)
    }
    items(state.items, key = { it.id }) { item ->
        var editing by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    val catName = state.categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty()
                    Text(
                        catName + " · " + Money(item.priceCents).format() +
                            (item.halfPriceCents?.let { " / " + Money(it).format() } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!item.active) Text(stringResource(R.string.set_inactive), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.set_edit)) }
            }
        }
        if (editing) {
            ItemDialog(
                existing = item, categories = state.categories,
                onSave = { cat, name, desc, price, half, active ->
                    vm.saveItem(item.id, cat, name, desc, price, half, active)
                    editing = false
                },
                onDismiss = { editing = false }
            )
        }
    }
}

@Composable
private fun MenuToolbar(state: SettingsState, vm: SettingsViewModel) {
    var addingItem by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { addingItem = true }, enabled = state.categories.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.set_add_item))
        }
        OutlinedButton(onClick = { addingCategory = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.set_add_category))
        }
    }
    if (addingItem) {
        ItemDialog(
            existing = null, categories = state.categories,
            onSave = { cat, name, desc, price, half, active ->
                vm.saveItem(null, cat, name, desc, price, half, active)
                addingItem = false
            },
            onDismiss = { addingItem = false }
        )
    }
    if (addingCategory) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingCategory = false },
            title = { Text(stringResource(R.string.set_add_category)) },
            text = {
                OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text(stringResource(R.string.field_name)) })
            },
            confirmButton = {
                TextButton(onClick = { vm.addCategory(name); addingCategory = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { addingCategory = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ItemDialog(
    existing: MenuItemEntity?,
    categories: List<CategoryEntity>,
    onSave: (categoryId: String, name: String, description: String, price: String, half: String, active: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var price by remember { mutableStateOf(existing?.let { (it.priceCents / 100L).toString() }.orEmpty()) }
    var half by remember { mutableStateOf(existing?.halfPriceCents?.let { (it / 100L).toString() }.orEmpty()) }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.set_add_item else R.string.set_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.field_name)) })
                OutlinedTextField(description, { description = it.take(80) }, singleLine = true, label = { Text(stringResource(R.string.set_description)) })
                OutlinedTextField(
                    price, { price = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(8) }, singleLine = true,
                    label = { Text(stringResource(R.string.set_price)) }, keyboardOptions = decimal
                )
                OutlinedTextField(
                    half, { half = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(8) }, singleLine = true,
                    label = { Text(stringResource(R.string.set_half_price)) }, keyboardOptions = decimal
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(cat.name) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.set_active))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(categoryId, name, description, price, half, active) }, enabled = name.isNotBlank() && price.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------- staff

private fun LazyListScope.staffItems(state: SettingsState, vm: SettingsViewModel, session: Session) {
    item { StaffToolbar(state, vm, session) }
    val outletNames = state.outlets.associate { it.id to it.name }
    items(state.staff, key = { it.id }) { person ->
        var resetting by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        roleText(person.role) + (person.outletId?.let { " · " + outletNames[it].orEmpty() } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (person.id != session.staffId && com.apenkoder.niasfood.domain.Permissions.canManageRole(session.role, person.role)) {
                    TextButton(onClick = { resetting = true }) { Text(stringResource(R.string.set_reset_pin)) }
                    TextButton(onClick = { vm.setActive(person.id, false) }) { Text(stringResource(R.string.set_deactivate)) }
                }
            }
        }
        if (resetting) {
            PinDialog(
                title = stringResource(R.string.set_reset_pin_for, person.name),
                pinLength = person.role.pinLength,
                onConfirm = { pin, again ->
                    vm.resetPin(person.id, pin, again)
                    resetting = false
                },
                onDismiss = { resetting = false }
            )
        }
    }
}

@Composable
private fun StaffToolbar(state: SettingsState, vm: SettingsViewModel, session: Session) {
    var adding by remember { mutableStateOf(false) }
    Button(onClick = { adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.set_add_staff)) }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(Role.SERVER) }
        var outletId by remember { mutableStateOf(session.outletId ?: state.outlets.firstOrNull()?.id) }
        var pin by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.set_add_staff)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.field_name)) })
                    if (session.role == Role.OWNER) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = role == Role.SERVER, onClick = { role = Role.SERVER; pin = ""; again = "" }, label = { Text(roleText(Role.SERVER)) })
                            FilterChip(selected = role == Role.MANAGER, onClick = { role = Role.MANAGER; pin = ""; again = "" }, label = { Text(roleText(Role.MANAGER)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.outlets.forEach { o ->
                                FilterChip(selected = outletId == o.id, onClick = { outletId = o.id }, label = { Text(o.name) })
                            }
                        }
                    }
                    PinFields(role.pinLength, pin, { pin = it }, again, { again = it })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addStaff(name, role, outletId, pin, again); adding = false },
                    enabled = name.isNotBlank() && pin.length == role.pinLength
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PinFields(length: Int, pin: String, onPin: (String) -> Unit, again: String, onAgain: (String) -> Unit) {
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    OutlinedTextField(
        pin, { onPin(it.filter { ch -> ch in '0'..'9' }.take(length)) }, singleLine = true,
        label = { Text(stringResource(R.string.set_new_pin, length)) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = keyboard
    )
    OutlinedTextField(
        again, { onAgain(it.filter { ch -> ch in '0'..'9' }.take(length)) }, singleLine = true,
        label = { Text(stringResource(R.string.setup_owner_pin_again)) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = keyboard
    )
}

@Composable
private fun PinDialog(title: String, pinLength: Int, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PinFields(pinLength, pin, { pin = it }, again, { again = it }) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin, again) }, enabled = pin.length == pinLength) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
