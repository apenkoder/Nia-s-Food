package com.apenkoder.niasfood

import com.apenkoder.niasfood.domain.Destination
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {
  private val server = Session("s", "Server", Role.SERVER, "o1")
  private val manager = Session("m", "Manager", Role.MANAGER, "o1")
  private val owner = Session("w", "Owner", Role.OWNER, null)

  @Test fun serverCannotSeeFinancialsOrVoid() {
    assertFalse(Permissions.has(Role.SERVER, Permission.VIEW_FINANCIALS))
    assertFalse(Permissions.has(Role.SERVER, Permission.VOID_ORDER))
    assertFalse(Permissions.has(Role.SERVER, Permission.APPLY_DISCOUNT))
    assertFalse(Permissions.has(Role.SERVER, Permission.RECORD_EXPENSE))
    assertFalse(Permissions.has(Role.SERVER, Permission.MANAGE_PRICES))
    assertTrue(Permissions.has(Role.SERVER, Permission.TAKE_PAYMENT))
    assertTrue(Permissions.has(Role.SERVER, Permission.TOGGLE_SOLD_OUT))
  }

  @Test fun onlyManagerReconcilesTheDrawerAndOnlyOwnerSwitchesOutlets() {
    assertTrue(Permissions.has(Role.MANAGER, Permission.RECONCILE_DRAWER))
    assertFalse(Permissions.has(Role.OWNER, Permission.RECONCILE_DRAWER))
    assertTrue(Permissions.has(Role.OWNER, Permission.SWITCH_OUTLET))
    assertFalse(Permissions.has(Role.MANAGER, Permission.SWITCH_OUTLET))
    assertFalse(Permissions.has(Role.OWNER, Permission.TAKE_PAYMENT))
  }

  @Test fun tabsFollowTheRole() {
    assertEquals(listOf(Destination.POS, Destination.ORDERS), Permissions.tabsFor(Role.SERVER))
    assertFalse(Permissions.canOpen(Role.SERVER, Destination.DASHBOARD))
    assertFalse(Permissions.canOpen(Role.SERVER, Destination.EXPENSES))
    assertFalse(Permissions.canOpen(Role.MANAGER, Destination.DASHBOARD))
    assertTrue(Permissions.canOpen(Role.MANAGER, Destination.REPORTS))
    assertEquals(Destination.DASHBOARD, Permissions.startFor(Role.OWNER))
    assertFalse(Permissions.canOpen(Role.OWNER, Destination.POS))
  }

  @Test fun outletScoping() {
    assertTrue(Permissions.canActInOutlet(server, "o1"))
    assertFalse(Permissions.canActInOutlet(server, "o2"))
    assertFalse(Permissions.canActInOutlet(manager, "o2"))
    assertTrue(Permissions.canActInOutlet(owner, "o2"))
  }

  @Test fun staffManagementRules() {
    assertTrue(Permissions.canManageRole(Role.MANAGER, Role.SERVER))
    assertFalse(Permissions.canManageRole(Role.MANAGER, Role.MANAGER))
    assertFalse(Permissions.canManageRole(Role.SERVER, Role.SERVER))
    assertTrue(Permissions.canManageRole(Role.OWNER, Role.MANAGER))
  }

  @Test fun approvalMustComeFromSameOutletManagerOrOwner() {
    assertTrue(Permissions.canApprove(Role.MANAGER, "o1", "o1", Permission.VOID_ORDER))
    assertFalse(Permissions.canApprove(Role.MANAGER, "o2", "o1", Permission.VOID_ORDER))
    assertTrue(Permissions.canApprove(Role.OWNER, null, "o1", Permission.APPLY_DISCOUNT))
    assertFalse(Permissions.canApprove(Role.SERVER, "o1", "o1", Permission.VOID_ORDER))
  }
}
