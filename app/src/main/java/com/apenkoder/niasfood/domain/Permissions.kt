package com.apenkoder.niasfood.domain

enum class Permission {
  TAKE_PAYMENT,
  VOID_ORDER,
  APPLY_DISCOUNT,
  RECONCILE_DRAWER,
  RECORD_EXPENSE,
  VOID_EXPENSE,
  TOGGLE_SOLD_OUT,
  MANAGE_PRICES,
  MANAGE_STAFF,
  VIEW_FINANCIALS,
  SWITCH_OUTLET,
}

enum class Destination(
  val route: String,
) {
  POS("pos"),
  ORDERS("orders"),
  EXPENSES("expenses"),
  REPORTS("reports"),
  DASHBOARD("dashboard"),
  SETTINGS("settings"),
}

/** The single source of truth for RBAC. Mirrors docs/RBAC_MATRIX.md row by row. */
object Permissions {
  fun has(
    role: Role,
    permission: Permission,
  ): Boolean =
    when (permission) {
      Permission.TAKE_PAYMENT -> role == Role.SERVER || role == Role.MANAGER
      Permission.VOID_ORDER, Permission.APPLY_DISCOUNT -> role == Role.MANAGER || role == Role.OWNER
      Permission.RECONCILE_DRAWER -> role == Role.MANAGER
      Permission.RECORD_EXPENSE, Permission.VOID_EXPENSE -> role == Role.MANAGER || role == Role.OWNER
      Permission.TOGGLE_SOLD_OUT -> true
      Permission.MANAGE_PRICES -> role == Role.MANAGER || role == Role.OWNER
      Permission.MANAGE_STAFF -> role == Role.MANAGER || role == Role.OWNER
      Permission.VIEW_FINANCIALS -> role == Role.MANAGER || role == Role.OWNER
      Permission.SWITCH_OUTLET -> role == Role.OWNER
    }

  /** Which staff roles an actor may create, reset or deactivate. */
  fun canManageRole(
    actor: Role,
    target: Role,
  ): Boolean =
    when (actor) {
      Role.OWNER -> true
      Role.MANAGER -> target == Role.SERVER
      Role.SERVER -> false
    }

  /** Bottom-bar tabs per role, in display order. Lock is a top-bar action, not a tab. */
  fun tabsFor(role: Role): List<Destination> =
    when (role) {
      Role.SERVER -> listOf(Destination.POS, Destination.ORDERS)
      Role.MANAGER ->
        listOf(
          Destination.POS,
          Destination.ORDERS,
          Destination.EXPENSES,
          Destination.REPORTS,
          Destination.SETTINGS,
        )
      Role.OWNER ->
        listOf(
          Destination.DASHBOARD,
          Destination.REPORTS,
          Destination.EXPENSES,
          Destination.SETTINGS,
        )
    }

  fun canOpen(
    role: Role,
    destination: Destination,
  ): Boolean = destination in tabsFor(role)

  fun startFor(role: Role): Destination = tabsFor(role).first()

  /** Outlet scoping: owner may act anywhere, everyone else only in their own outlet. */
  fun canActInOutlet(
    session: Session,
    outletId: String,
  ): Boolean = session.role == Role.OWNER || session.outletId == outletId

  /** Can this staff member approve an action (void, discount) for an outlet? */
  fun canApprove(
    approverRole: Role,
    approverOutletId: String?,
    outletId: String,
    permission: Permission,
  ): Boolean {
    if (!has(approverRole, permission)) return false
    return approverRole == Role.OWNER || approverOutletId == outletId
  }
}
