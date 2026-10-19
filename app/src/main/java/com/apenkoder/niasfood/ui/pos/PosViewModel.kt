package com.apenkoder.niasfood.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.Outcome
import com.apenkoder.niasfood.core.attempt
import com.apenkoder.niasfood.data.local.AddOnEntity
import com.apenkoder.niasfood.data.local.CategoryEntity
import com.apenkoder.niasfood.data.local.MenuItemEntity
import com.apenkoder.niasfood.data.repo.CartLine
import com.apenkoder.niasfood.data.repo.CheckoutRequest
import com.apenkoder.niasfood.data.repo.OrderReceipt
import com.apenkoder.niasfood.data.repo.PaymentInput
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.OrderType
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.domain.Variant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PosStage { BROWSE, CHECKOUT, RECEIPT }

/** One cart line. Encoded to a plain string so the cart survives process death in the SavedStateHandle. */
data class CartEntry(val itemId: String, val variant: Variant, val qty: Int, val addOnIds: List<String>) {
    val key: String get() = itemId + "|" + variant.name + "|" + addOnIds.joinToString(",")

    fun encode(): String = itemId + "|" + variant.name + "|" + qty + "|" + addOnIds.joinToString(",")

    companion object {
        fun decode(raw: String): CartEntry? {
            val p = raw.split("|")
            if (p.size != 4) return null
            val variant = runCatching { Variant.valueOf(p[1]) }.getOrNull() ?: return null
            val qty = p[2].toIntOrNull() ?: return null
            val addOns = if (p[3].isEmpty()) emptyList() else p[3].split(",")
            return CartEntry(p[0], variant, qty, addOns)
        }
    }
}

data class PosState(
    val loading: Boolean = true,
    val categories: List<CategoryEntity> = emptyList(),
    val items: List<MenuItemEntity> = emptyList(),
    val itemsById: Map<String, MenuItemEntity> = emptyMap(),
    val addOns: List<AddOnEntity> = emptyList(),
    val addOnsById: Map<String, AddOnEntity> = emptyMap(),
    val shiftOpen: Boolean = false,
    val selectedCategoryId: String? = null,
    val query: String = "",
    val orderType: OrderType = OrderType.DINE_IN,
    val cart: List<CartEntry> = emptyList(),
    val heldOrderId: String? = null,
    val note: String = "",
    val discountCents: Long = 0L,
    val discountReason: String? = null,
    val discountApproverId: String? = null,
    val stage: PosStage = PosStage.BROWSE,
    val receipt: OrderReceipt? = null,
    val heldSaved: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val errorDetail: String? = null
) {
    fun lineTotal(e: CartEntry): Long {
        val item = itemsById[e.itemId] ?: return 0L
        val base = if (e.variant == Variant.HALF) (item.halfPriceCents ?: item.priceCents) else item.priceCents
        val extras = e.addOnIds.sumOf { addOnsById[it]?.priceCents ?: 0L }
        return (base + extras) * e.qty
    }

    val itemCount: Int get() = cart.sumOf { it.qty }
    val subtotalCents: Long get() = cart.sumOf { lineTotal(it) }
    val totalCents: Long get() = (subtotalCents - discountCents).coerceAtLeast(0L)

    val visibleItems: List<MenuItemEntity>
        get() = items.filter {
            it.active &&
                (selectedCategoryId == null || it.categoryId == selectedCategoryId) &&
                (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true))
        }
}

class PosViewModel(
    private val c: AppContainer,
    private val handle: SavedStateHandle,
    private val session: Session
) : ViewModel() {

    private val _state = MutableStateFlow(PosState())
    val state: StateFlow<PosState> = _state

    init {
        val outletId = session.outletId
        viewModelScope.launch {
            c.menu.categories().collect { list -> _state.update { it.copy(categories = list, loading = false) } }
        }
        viewModelScope.launch {
            c.menu.items().collect { list -> _state.update { it.copy(items = list, itemsById = list.associateBy { i -> i.id }) } }
        }
        viewModelScope.launch {
            c.menu.addOns().collect { list -> _state.update { it.copy(addOns = list, addOnsById = list.associateBy { a -> a.id }) } }
        }
        if (outletId != null) {
            viewModelScope.launch {
                c.sales.observeOpenShift(outletId).collect { shift -> _state.update { it.copy(shiftOpen = shift != null) } }
            }
        }
        viewModelScope.launch {
            handle.getStateFlow<ArrayList<String>>(CART, ArrayList()).collect { raw ->
                _state.update { it.copy(cart = raw.mapNotNull { s -> CartEntry.decode(s) }) }
            }
        }
        viewModelScope.launch {
            handle.getStateFlow(TYPE, OrderType.DINE_IN.name).collect { name ->
                val type = runCatching { OrderType.valueOf(name) }.getOrDefault(OrderType.DINE_IN)
                _state.update { it.copy(orderType = type) }
            }
        }
        viewModelScope.launch {
            handle.getStateFlow<String?>(HELD, null).collect { id -> _state.update { it.copy(heldOrderId = id) } }
        }
        viewModelScope.launch {
            c.pendingHeldOrderId.collect { id ->
                if (id != null) {
                    c.pendingHeldOrderId.value = null
                    loadHeld(id)
                }
            }
        }
    }

    private suspend fun loadHeld(orderId: String) {
        val order = c.sales.order(orderId) ?: return
        if (order.outletId != session.outletId) return
        val lines = c.sales.linesFor(orderId)
        val entries = lines.map { l ->
            CartEntry(l.itemId, l.variant, l.qty, if (l.addOnIds.isEmpty()) emptyList() else l.addOnIds.split(","))
        }
        handle[CART] = ArrayList(entries.map { e -> e.encode() })
        handle[TYPE] = order.type.name
        handle[HELD] = orderId
        _state.update {
            it.copy(
                note = order.note, discountCents = 0L, discountReason = null, discountApproverId = null,
                stage = PosStage.BROWSE, receipt = null
            )
        }
    }

    // ---------- browsing ----------

    fun selectCategory(id: String?) = _state.update { it.copy(selectedCategoryId = id) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setType(type: OrderType) {
        handle[TYPE] = type.name
    }

    fun toggleSoldOut(item: MenuItemEntity) {
        viewModelScope.launch {
            val r = attempt { c.menu.setSoldOut(item.id, !item.soldOut) }
            if (r is Outcome.Fail) _state.update { it.copy(error = r.error, errorDetail = r.detail) }
        }
    }

    // ---------- cart ----------

    private fun setCart(list: List<CartEntry>) {
        handle[CART] = ArrayList(list.map { e -> e.encode() })
        // A changed cart invalidates any discount that was approved for the old total.
        _state.update { it.copy(discountCents = 0L, discountReason = null, discountApproverId = null) }
    }

    fun add(item: MenuItemEntity, variant: Variant = Variant.FULL, addOnIds: List<String> = emptyList(), qty: Int = 1) {
        val entry = CartEntry(item.id, variant, qty, addOnIds.sorted())
        val current = _state.value.cart
        val index = current.indexOfFirst { it.key == entry.key }
        val next = if (index >= 0) {
            current.mapIndexed { i, e -> if (i == index) e.copy(qty = (e.qty + qty).coerceAtMost(MAX_QTY)) else e }
        } else {
            current + entry
        }
        setCart(next)
    }

    fun changeQty(key: String, delta: Int) {
        setCart(
            _state.value.cart.mapNotNull { e ->
                if (e.key != key) e else {
                    val q = e.qty + delta
                    if (q <= 0) null else e.copy(qty = q.coerceAtMost(MAX_QTY))
                }
            }
        )
    }

    fun remove(key: String) = setCart(_state.value.cart.filter { it.key != key })

    fun setNote(text: String) = _state.update { it.copy(note = text.take(120)) }

    fun setDiscount(cents: Long, reason: String, approverId: String?) =
        _state.update { it.copy(discountCents = cents, discountReason = reason, discountApproverId = approverId) }

    fun clearDiscount() = _state.update { it.copy(discountCents = 0L, discountReason = null, discountApproverId = null) }

    // ---------- stages ----------

    fun startCheckout() {
        if (_state.value.cart.isEmpty()) return
        _state.update { it.copy(stage = PosStage.CHECKOUT, error = null) }
    }

    fun backToBrowse() = _state.update { it.copy(stage = PosStage.BROWSE, error = null) }

    fun newOrder() = _state.update { it.copy(stage = PosStage.BROWSE, receipt = null, heldSaved = false, error = null) }

    fun dismissError() = _state.update { it.copy(error = null, errorDetail = null) }

    /** [payment] null means "hold this order unpaid". */
    fun submit(payment: PaymentInput?) {
        val st = _state.value
        val outletId = session.outletId ?: return
        if (st.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val request = CheckoutRequest(
                outletId = outletId, type = st.orderType,
                lines = st.cart.map { CartLine(it.itemId, it.variant, it.qty, it.addOnIds) },
                note = st.note, discountCents = st.discountCents, discountReason = st.discountReason,
                discountApproverId = st.discountApproverId, heldOrderId = st.heldOrderId, payment = payment
            )
            when (val r = attempt { c.sales.submit(request) }) {
                is Outcome.Ok -> {
                    handle[CART] = ArrayList<String>()
                    handle[HELD] = null as String?
                    _state.update {
                        it.copy(
                            busy = false, note = "", discountCents = 0L, discountReason = null, discountApproverId = null,
                            stage = if (payment != null) PosStage.RECEIPT else PosStage.BROWSE,
                            receipt = if (payment != null) r.value else null,
                            heldSaved = payment == null
                        )
                    }
                }
                is Outcome.Fail -> _state.update { it.copy(busy = false, error = r.error, errorDetail = r.detail) }
            }
        }
    }

    private companion object {
        const val CART = "cart"
        const val TYPE = "type"
        const val HELD = "held"
        const val MAX_QTY = 99
    }
}
