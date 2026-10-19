package com.apenkoder.niasfood.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.data.repo.ApprovalResult
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.ui.app.LocalContainer
import kotlinx.coroutines.launch

@Composable
fun LoadingState(message: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorBanner(text: String, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
                }
            }
        }
    }
}

/** State is always shown with an icon AND text, never by colour alone. */
@Composable
fun StatusChip(text: String, icon: ImageVector, positive: Boolean, modifier: Modifier = Modifier) {
    val container = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier = modifier.padding(end = 4.dp), shape = CircleShape, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun DemoBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.demo_banner), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Asks for a mandatory reason (void, flag, variance...). */
@Composable
fun ReasonDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.reason)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun QuantityStepper(qty: Int, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.qty_decrease))
        }
        Text(
            qty.toString(),
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        FilledTonalIconButton(onClick = onPlus) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.qty_increase))
        }
    }
}

@Composable
fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0 until length) {
            val on = i < filled
            Box(
                Modifier
                    .size(16.dp)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

/** Digits only. No letters under the numbers. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val rows = listOf("123", "456", "789")
    val backspace = stringResource(R.string.pin_backspace)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { digits ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digits.forEach { ch ->
                    FilledTonalButton(
                        onClick = { onDigit(ch) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text(ch.toString(), style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = { onDigit('0') },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("0", style = MaterialTheme.typography.titleLarge) }
            FilledTonalButton(
                onClick = onBackspace,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = backspace }
            ) { Icon(Icons.Default.Clear, contentDescription = null) }
        }
    }
}

@Composable
fun errorText(error: AppError, detail: String? = null): String {
    if (error == AppError.SOLD_OUT && detail != null) return stringResource(R.string.err_sold_out_named, detail)
    return stringResource(
        when (error) {
            AppError.NOT_ALLOWED -> R.string.err_not_allowed
            AppError.SHIFT_CLOSED -> R.string.err_shift_closed
            AppError.EMPTY_CART -> R.string.err_empty_cart
            AppError.SOLD_OUT -> R.string.err_sold_out
            AppError.CASH_SHORT -> R.string.err_cash_short
            AppError.BAD_MM_CODE -> R.string.err_bad_mm_code
            AppError.DUPLICATE_MM_CODE -> R.string.err_duplicate_mm_code
            AppError.AMOUNT_INVALID -> R.string.err_amount_invalid
            AppError.REASON_REQUIRED -> R.string.err_reason_required
            AppError.DESCRIPTION_REQUIRED -> R.string.err_description_required
            AppError.WEAK_PIN -> R.string.err_weak_pin
            AppError.BAD_PIN_FORMAT -> R.string.err_bad_pin_format
            AppError.SHIFT_ALREADY_OPEN -> R.string.err_shift_already_open
            AppError.UNVERIFIED_PAYMENTS -> R.string.err_unverified_payments
            AppError.VARIANCE_REASON_REQUIRED -> R.string.err_variance_reason
            AppError.ORDER_CLOSED_SHIFT -> R.string.err_closed_shift
            AppError.ALREADY_VOIDED -> R.string.err_already_voided
            AppError.DISCOUNT_INVALID -> R.string.err_discount_invalid
            AppError.APPROVAL_REQUIRED -> R.string.err_approval_required
            AppError.NOT_FOUND -> R.string.err_not_found
            AppError.NAME_REQUIRED -> R.string.err_name_required
            AppError.BAD_TIME -> R.string.err_bad_time
            AppError.PIN_MISMATCH -> R.string.err_pin_mismatch
            AppError.STORAGE -> R.string.err_storage
        }
    )
}

@Composable
fun roleText(role: Role): String = stringResource(
    when (role) {
        Role.SERVER -> R.string.role_server
        Role.MANAGER -> R.string.role_manager
        Role.OWNER -> R.string.role_owner
    }
)

/** Manager/owner PIN approval for sensitive actions (discount, void). */
@Composable
fun ManagerApprovalDialog(
    outletId: String,
    permission: Permission,
    onApproved: (StaffEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var approvers by remember { mutableStateOf<List<StaffEntity>>(emptyList()) }
    var selected by remember { mutableStateOf<StaffEntity?>(null) }
    var pin by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ApprovalResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(outletId) {
        approvers = container.auth.approvers(outletId)
        selected = approvers.firstOrNull()
    }

    fun submit(who: StaffEntity, digits: String) {
        scope.launch {
            busy = true
            val r = container.auth.approve(outletId, who.id, digits, permission)
            busy = false
            pin = ""
            if (r is ApprovalResult.Approved) onApproved(r.approver) else result = r
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.approval_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.approval_pick), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    approvers.forEach { a ->
                        FilterChip(
                            selected = selected?.id == a.id,
                            onClick = { selected = a; pin = ""; result = null },
                            label = { Text(a.name) }
                        )
                    }
                }
                val who = selected
                if (who != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PinDots(length = who.role.pinLength, filled = pin.length)
                    }
                    when (val r = result) {
                        is ApprovalResult.WrongPin ->
                            Text(stringResource(R.string.login_wrong, r.attemptsLeft), color = MaterialTheme.colorScheme.error)
                        is ApprovalResult.Locked ->
                            Text(stringResource(R.string.login_locked, ((r.remainingMs + 59_999L) / 60_000L).toInt()), color = MaterialTheme.colorScheme.error)
                        is ApprovalResult.NotAllowed ->
                            Text(stringResource(R.string.err_not_allowed), color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                    PinPad(
                        onDigit = { d ->
                            if (!busy && pin.length < who.role.pinLength) {
                                pin += d
                                result = null
                                if (pin.length == who.role.pinLength) submit(who, pin)
                            }
                        },
                        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        enabled = !busy
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
