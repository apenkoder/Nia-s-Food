# RBAC matrix

Enforced in three places: `domain/Permissions.kt` (rules), the services in `data/repo` (every action re-checks the
role and the outlet), and `ui/navigation/Nav.kt` (only permitted destinations are registered, so a route outside
the role cannot be reached). Hiding a button is never the only control.

| Capability | Server | Manager | Owner |
|---|---|---|---|
| Create/hold orders, take payment | own outlet | own outlet | no |
| Void a paid order | no (needs manager/owner PIN approval) | yes + reason | yes + reason (via approval PIN on an outlet device) |
| Apply discount | no (needs manager/owner PIN approval) | yes + reason | yes + reason |
| Open/close shift, count cash, verify mobile money | no | own outlet | no |
| Record / void expenses | no | own outlet | any outlet |
| Mark item sold out / back in stock | yes | yes | yes |
| Add items, change prices | no | yes (audited) | yes (audited) |
| Manage staff and PINs | no | servers of own outlet | all |
| Dashboard and reports | no | own outlet (Reports tab) | all outlets on this device |
| Switch outlet | no | no | yes |

## Bottom bar per role

- Server: POS, Orders
- Manager: POS, Orders, Expenses, Reports, Settings
- Owner: Dashboard, Reports, Expenses, Settings

Lock is a top-bar button for everyone (not a tab).

## Approval flow

A server needing a discount or void picks a manager of that outlet (or the owner) and that person types their own
PIN. The same lockout rules as login apply. The approver's id is stored on the order and in the audit log.

## Rules that are not purely role based

- A user's outlet is fixed by their profile. Only the owner can see or switch outlets.
- A paid order, expense or payment belonging to a **closed** shift cannot be voided or changed.
- A shift cannot close while any mobile-money payment is still unverified (flagged is allowed, with a reason).
- Nobody can deactivate their own account.
