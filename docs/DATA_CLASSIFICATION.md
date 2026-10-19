# Data classification

| Table | Class | Notes |
|---|---|---|
| staff | restricted | `pinHash` is bcrypt (cost 12, per-hash salt). PINs are never stored, logged or shown. |
| payment | restricted | `reference` is the mobile-money transaction code. Masked everywhere except the manager's verification list. No customer phone numbers are stored. |
| shift | restricted | Cash counts, expected cash and variance. |
| customer_order, order_line | confidential | Sales records. Never hard-deleted once paid. |
| expense | confidential | Voided with a reason, never deleted. |
| audit_event | internal | Append-only. Contains no PINs, hashes or full payment codes. |
| outlet, category, menu_item, add_on | internal | |
| sync_outbox | internal | Queue for a future backend. Nothing consumes it in v1. |
| app_setting | internal | |

The whole database is encrypted at rest with SQLCipher. The passphrase is random, wrapped with an Android Keystore
AES-GCM key, and only the wrapped form is stored on disk. `allowBackup` is off and backup/transfer rules exclude all
data, so the database never leaves the device through Android backup.
