package com.apenkoder.niasfood.ui.pos
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.Money
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.data.local.AddOnEntity
import com.apenkoder.niasfood.data.local.MenuItemEntity
import com.apenkoder.niasfood.data.repo.OrderReceipt
import com.apenkoder.niasfood.data.repo.PaymentInput
import com.apenkoder.niasfood.data.security.ApprovalGrant
import com.apenkoder.niasfood.domain.MobileMoneyCode
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.PaymentMethod
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.domain.Variant
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.*

@Composable
fun PosScreen(session: Session) {
  val vm = containerViewModel<PosViewModel>("pos-${session.staffId}") { c, h -> PosViewModel(c, h, session) }
  val state by vm.state.collectAsStateWithLifecycle()

  BackHandler(enabled = state.stage == PosStage.CHECKOUT) { vm.backToBrowse() }

  when {
    state.loading -> LoadingState()
    state.stage == PosStage.BROWSE -> BrowseContent(state, vm, session)
    state.stage == PosStage.CHECKOUT -> CheckoutContent(state, vm, session)
    else -> {
      val receipt = state.receipt
      if (receipt != null) ReceiptContent(receipt, onNewOrder = vm::newOrder) else LoadingState()
    }
  }
}

// ---------------------------------------------------------------- browse

@Composable
private fun BrowseContent(
  state: PosState,
  vm: PosViewModel,
  session: Session,
) {
  var optionsFor by remember { mutableStateOf<MenuItemEntity?>(null) }

  Column(Modifier.fillMaxSize()) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (!state.shiftOpen) {
        ErrorBanner(stringResource(R.string.pos_shift_closed_hint))
      }
      val error = state.error
      if (error != null) ErrorBanner(errorText(error, state.errorDetail), onDismiss = vm::dismissError)
      if (state.heldSaved) {
        Text(stringResource(R.string.pos_held_saved), color = MaterialTheme.colorScheme.primary)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = state.orderType == OrderType.DINE_IN,
          onClick = { vm.setType(OrderType.DINE_IN) },
          label = { Text(stringResource(R.string.type_dine_in)) },
        )
        FilterChip(
          selected = state.orderType == OrderType.TAKEOUT,
          onClick = { vm.setType(OrderType.TAKEOUT) },
          label = { Text(stringResource(R.string.type_takeout)) },
        )
      }
      OutlinedTextField(
        value = state.query,
        onValueChange = vm::setQuery,
        singleLine = true,
        label = { Text(stringResource(R.string.pos_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
      )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(
            selected = state.selectedCategoryId == null,
            onClick = { vm.selectCategory(null) },
            label = { Text(stringResource(R.string.pos_all)) },
          )
        }
        items(state.categories, key = { it.id }) { category ->
          FilterChip(
            selected = state.selectedCategoryId == category.id,
            onClick = { vm.selectCategory(category.id) },
            label = { Text(category.name) },
          )
        }
      }
    }

    val visible = state.visibleItems
    if (visible.isEmpty()) {
      EmptyState(stringResource(R.string.pos_no_items), Modifier.weight(1f))
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(visible, key = { it.id }) { item ->
          ItemRow(
            item = item,
            hasOptions = item.halfPriceCents != null || state.addOns.isNotEmpty(),
            onAdd = { vm.add(item) },
            onOptions = { optionsFor = item },
            onToggleSoldOut = { vm.toggleSoldOut(item) },
          )
        }
      }
    }

    if (state.cart.isNotEmpty()) CartBar(state, onReview = vm::startCheckout)
  }

  val target = optionsFor
  if (target != null) {
    OptionsDialog(
      item = target,
      addOns = state.addOns,
      onAdd = { variant, addOnIds, qty ->
        vm.add(target, variant, addOnIds, qty)
        optionsFor = null
      },
      onDismiss = { optionsFor = null },
    )
  }
}

@Composable
private fun ItemRow(
  item: MenuItemEntity,
  hasOptions: Boolean,
  onAdd: () -> Unit,
  onOptions: () -> Unit,
  onToggleSoldOut: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.name, style = MaterialTheme.typography.titleMedium)
        if (item.description.isNotBlank()) {
          Text(
            item.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
        Text(
          stringResource(R.string.pos_price_per_plate, Money(item.priceCents).format()),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
        val half = item.halfPriceCents
        if (half != null) {
          Text(
            stringResource(R.string.pos_price_half, Money(half).format()),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (item.soldOut) {
          StatusChip(stringResource(R.string.pos_sold_out), Icons.Default.Warning, positive = false)
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        if (!item.soldOut) {
          Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.pos_add))
          }
          if (hasOptions) {
            TextButton(onClick = onOptions) { Text(stringResource(R.string.pos_options)) }
          }
        }
      }
      Box {
        IconButton(onClick = { menuOpen = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.pos_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = {
              Text(stringResource(if (item.soldOut) R.string.pos_back_in_stock else R.string.pos_mark_sold_out))
            },
            onClick = {
              menuOpen = false
              onToggleSoldOut()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun CartBar(
  state: PosState,
  onReview: () -> Unit,
) {
  Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.primaryContainer) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(
            R.string.pos_cart_summary,
            state.itemCount,
            stringResource(if (state.orderType == OrderType.DINE_IN) R.string.type_dine_in else R.string.type_takeout),
          ),
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(Money(state.subtotalCents).format(), style = MaterialTheme.typography.titleLarge)
      }
      Button(onClick = onReview, modifier = Modifier.heightIn(min = 52.dp)) {
        Text(stringResource(R.string.pos_review_pay))
      }
    }
  }
}

@Composable
private fun OptionsDialog(
  item: MenuItemEntity,
  addOns: List<AddOnEntity>,
  onAdd: (Variant, List<String>, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var variant by remember { mutableStateOf(Variant.FULL) }
  var qty by remember { mutableIntStateOf(1) }
  var chosen by remember { mutableStateOf(setOf<String>()) }
  val half = item.halfPriceCents
  val base = if (variant == Variant.HALF && half != null) half else item.priceCents
  val extras = addOns.filter { it.id in chosen }.sumOf { it.priceCents }
  val total = (base + extras) * qty

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(item.name) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (half != null) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = variant == Variant.FULL,
              onClick = { variant = Variant.FULL },
              label = { Text(stringResource(R.string.variant_full, Money(item.priceCents).format())) },
            )
            FilterChip(
              selected = variant == Variant.HALF,
              onClick = { variant = Variant.HALF },
              label = { Text(stringResource(R.string.variant_half, Money(half).format())) },
            )
          }
        }
        if (addOns.isNotEmpty()) {
          Text(stringResource(R.string.pos_add_ons), style = MaterialTheme.typography.titleSmall)
          addOns.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = a.id in chosen,
                onCheckedChange = { on -> chosen = if (on) chosen + a.id else chosen - a.id },
              )
              Text(
                stringResource(R.string.pos_add_on_line, a.name, Money(a.priceCents).format()),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
        QuantityStepper(
          qty = qty,
          onMinus = { if (qty > 1) qty -= 1 },
          onPlus = { if (qty < 99) qty += 1 },
        )
      }
    },
    confirmButton = {
      Button(onClick = { onAdd(variant, chosen.toList(), qty) }) {
        Text(stringResource(R.string.pos_add_total, Money(total).format()))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

// ---------------------------------------------------------------- checkout

@Composable
private fun CheckoutContent(
  state: PosState,
  vm: PosViewModel,
  session: Session,
) {
  var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
  var tenderedText by rememberSaveable { mutableStateOf("") }
  var codeText by rememberSaveable { mutableStateOf("") }
  var showApproval by remember { mutableStateOf(false) }
  var showDiscount by remember { mutableStateOf(false) }
  var approval by remember { mutableStateOf<ApprovalGrant?>(null) }

  val total = state.totalCents
  val tendered = Money.parse(tenderedText)?.cents
  val cashOk = tendered != null && tendered >= total
  val codeOk = MobileMoneyCode.isValid(codeText)
  val canPay =
    state.shiftOpen &&
      !state.busy &&
      total > 0L &&
      (if (method == PaymentMethod.CASH) cashOk else codeOk)

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = vm::backToBrowse) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.pos_review_pay), style = MaterialTheme.typography.titleLarge)
      }
    }
    val error = state.error
    if (error != null) item { ErrorBanner(errorText(error, state.errorDetail), onDismiss = vm::dismissError) }
    if (!state.shiftOpen) item { ErrorBanner(stringResource(R.string.pos_shift_closed_hint)) }

    items(state.cart, key = { it.key }) { entry ->
      val item = state.itemsById[entry.itemId]
      SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            val variantText =
              if (entry.variant ==
                Variant.HALF
              ) {
                " (" + stringResource(R.string.variant_half_short) + ")"
              } else {
                ""
              }
            Text((item?.name ?: "?") + variantText, style = MaterialTheme.typography.titleMedium)
            val addOnNames = entry.addOnIds.mapNotNull { state.addOnsById[it]?.name }
            if (addOnNames.isNotEmpty()) {
              Text(addOnNames.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
            Text(Money(state.lineTotal(entry)).format(), style = MaterialTheme.typography.bodyMedium)
          }
          QuantityStepper(
            qty = entry.qty,
            onMinus = { vm.changeQty(entry.key, -1) },
            onPlus = { vm.changeQty(entry.key, 1) },
          )
          IconButton(onClick = { vm.remove(entry.key) }) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pos_remove_line))
          }
        }
      }
    }

    item {
      OutlinedTextField(
        value = state.note,
        onValueChange = vm::setNote,
        singleLine = true,
        label = { Text(stringResource(R.string.pos_note)) },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item {
      SectionCard {
        MoneyRow(stringResource(R.string.pos_subtotal), state.subtotalCents)
        if (state.discountCents > 0L) {
          MoneyRow(stringResource(R.string.pos_discount), -state.discountCents)
          Text(state.discountReason.orEmpty(), style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = vm::clearDiscount) { Text(stringResource(R.string.pos_remove_discount)) }
        } else {
          OutlinedButton(
            onClick = {
              if (Permissions.has(session.role, Permission.APPLY_DISCOUNT)) {
                approval = null
                showDiscount = true
              } else {
                showApproval = true
              }
            },
            modifier = Modifier.heightIn(min = 48.dp),
          ) { Text(stringResource(R.string.pos_add_discount)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(stringResource(R.string.pos_total), style = MaterialTheme.typography.titleLarge)
          Text(Money(total).format(), style = MaterialTheme.typography.titleLarge)
        }
      }
    }

    item {
      SectionCard(title = stringResource(R.string.pos_payment)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = method == PaymentMethod.CASH,
            onClick = { method = PaymentMethod.CASH },
            label = { Text(stringResource(R.string.pay_cash)) },
          )
          FilterChip(
            selected = method == PaymentMethod.MOBILE_MONEY,
            onClick = { method = PaymentMethod.MOBILE_MONEY },
            label = { Text(stringResource(R.string.pay_mobile_money)) },
          )
        }
        if (method == PaymentMethod.CASH) {
          OutlinedTextField(
            value = tenderedText,
            onValueChange = { tenderedText = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(10) },
            singleLine = true,
            label = { Text(stringResource(R.string.pos_tendered)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
              onClick = { tenderedText = (total / 100L).toString() },
              label = { Text(stringResource(R.string.pos_exact)) },
            )
            listOf(500L, 1000L).forEach { amount ->
              AssistChip(onClick = { tenderedText = amount.toString() }, label = { Text(Money.ofShillings(amount).format()) })
            }
          }
          if (tendered != null && tendered >= total) {
            Text(
              stringResource(R.string.pos_change_due, Money(tendered - total).format()),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        } else {
          OutlinedTextField(
            value = codeText,
            onValueChange = { codeText = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10) },
            singleLine = true,
            label = { Text(stringResource(R.string.pos_mm_code)) },
            supportingText = { Text(stringResource(R.string.pos_mm_code_hint, codeText.length)) },
            isError = codeText.isNotEmpty() && !codeOk && codeText.length == 10,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = { vm.submit(null) },
          enabled = !state.busy,
          modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.pos_hold)) }
        Button(
          onClick = {
            val input =
              if (method == PaymentMethod.CASH) {
                PaymentInput(PaymentMethod.CASH, tendered, null)
              } else {
                PaymentInput(PaymentMethod.MOBILE_MONEY, null, codeText)
              }
            vm.submit(input)
          },
          enabled = canPay,
          modifier = Modifier.weight(2f).heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.pos_confirm_payment)) }
      }
    }
  }

  if (showApproval) {
    ManagerApprovalDialog(
      outletId = session.outletId.orEmpty(),
      permission = Permission.APPLY_DISCOUNT,
      onApproved = { _, grant ->
        approval = grant
        showApproval = false
        showDiscount = true
      },
      onDismiss = { showApproval = false },
    )
  }
  if (showDiscount) {
    DiscountDialog(
      subtotalCents = state.subtotalCents,
      onApply = { cents, reason ->
        vm.setDiscount(cents, reason, approval)
        showDiscount = false
      },
      onDismiss = { showDiscount = false },
    )
  }
}

@Composable
private fun MoneyRow(
  label: String,
  cents: Long,
) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Text(Money(cents).format(), style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun DiscountDialog(
  subtotalCents: Long,
  onApply: (Long, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var amountText by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  val cents = Money.parse(amountText)?.cents
  val valid = cents != null && cents > 0L && cents < subtotalCents && reason.isNotBlank()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.pos_add_discount)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it.filter { ch -> ch in '0'..'9' || ch == '.' }.take(10) },
          singleLine = true,
          label = { Text(stringResource(R.string.pos_discount_amount)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it.take(80) },
          singleLine = true,
          label = { Text(stringResource(R.string.reason)) },
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { if (cents != null) onApply(cents, reason.trim()) }, enabled = valid) {
        Text(stringResource(R.string.pos_apply))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

// ---------------------------------------------------------------- receipt

@Composable
private fun ReceiptContent(
  receipt: OrderReceipt,
  onNewOrder: () -> Unit,
) {
  val context = LocalContext.current
  val order = receipt.order
  val methodText =
    when (receipt.payment?.method) {
      PaymentMethod.MOBILE_MONEY -> stringResource(R.string.pay_mobile_money)
      PaymentMethod.CASH -> stringResource(R.string.pay_cash)
      null -> ""
    }
  val typeText = stringResource(if (order.type == OrderType.DINE_IN) R.string.type_dine_in else R.string.type_takeout)

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    StatusChip(stringResource(R.string.receipt_paid), Icons.Default.CheckCircle, positive = true)
    Text(stringResource(R.string.receipt_token, order.token), style = MaterialTheme.typography.displayMedium)
    Text(
      stringResource(R.string.receipt_meta, receipt.outletName, typeText, NairobiTime.time(order.paidAt ?: order.createdAt)),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
    SectionCard {
      receipt.lines.forEach { l ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          val halfTag = if (l.variant == Variant.HALF) " (" + stringResource(R.string.variant_half_short) + ")" else ""
          Text("${l.qty} x ${l.name}$halfTag", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
          Text(Money(l.lineTotalCents).format(), style = MaterialTheme.typography.bodyMedium)
        }
        if (l.addOnsText.isNotEmpty()) Text("+ " + l.addOnsText, style = MaterialTheme.typography.bodySmall)
      }
      if (order.discountCents > 0L) MoneyRow(stringResource(R.string.pos_discount), -order.discountCents)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.pos_total), style = MaterialTheme.typography.titleLarge)
        Text(Money(order.totalCents).format(), style = MaterialTheme.typography.titleLarge)
      }
      Text(methodText, style = MaterialTheme.typography.bodyMedium)
      if (receipt.changeCents > 0L) {
        Text(
          stringResource(R.string.pos_change_due, Money(receipt.changeCents).format()),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    OutlinedButton(
      onClick = {
        val text = buildReceiptText(context, receipt, typeText, methodText)
        val send =
          Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
          }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_receipt)))
      },
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
      Icon(Icons.Default.Share, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.share_receipt))
    }
    Button(onClick = onNewOrder, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
      Text(stringResource(R.string.receipt_new_order))
    }
  }
}

private fun buildReceiptText(
  context: android.content.Context,
  r: OrderReceipt,
  typeText: String,
  methodText: String,
): String {
  val sb = StringBuilder()
  sb.appendLine(context.getString(R.string.receipt_text_header, r.outletName))
  sb.appendLine(context.getString(R.string.receipt_token, r.order.token) + " · " + typeText)
  sb.appendLine(NairobiTime.shortDate(r.order.businessDate) + " " + NairobiTime.time(r.order.paidAt ?: r.order.createdAt))
  sb.appendLine()
  r.lines.forEach { l ->
    sb.appendLine("${l.qty} x ${l.name}  ${Money(l.lineTotalCents).format()}")
    if (l.addOnsText.isNotEmpty()) sb.appendLine("  + ${l.addOnsText}")
  }
  if (r.order.discountCents > 0L) sb.appendLine(context.getString(R.string.pos_discount) + " -" + Money(r.order.discountCents).format())
  sb.appendLine(context.getString(R.string.pos_total) + ": " + Money(r.order.totalCents).format())
  sb.appendLine(methodText)
  sb.appendLine()
  sb.appendLine(context.getString(R.string.receipt_text_footer))
  return sb.toString()
}
