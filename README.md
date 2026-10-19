# Nia's Food (native Android, v0.1.0)

Point of sale, shift control and money tracking for a two-outlet restaurant. Kotlin, Jetpack Compose, Room with
SQLCipher. Offline only: no INTERNET permission, no backend, no Firebase, no payment APIs.

> **Status:** `codeQualityCheck`, Android lint and `testDebugUnitTest` pass locally. GitHub Actions runs the same checks
> on pushes and pull requests targeting `main` or `develop`; the workflow can also be started manually.

## Open it

1. Android Studio (current stable). Open the `NiasFood` folder.
2. Settings, Build Tools, Gradle: set **Gradle JDK = Temurin 21**.
3. The Gradle wrapper is included (`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`). Set the Gradle JDK
   to Temurin 21. In PowerShell, run `& .\scripts\Invoke-AndroidChecks.ps1` for quality checks, lint and unit tests.
4. Sync, then run the `app` configuration on your **Kenya Baseline** AVD (API 33).
5. First launch seeds demo data in **debug** builds, so it takes a few seconds (6 bcrypt hashes).

## Demo accounts (debug builds only, never shown in the UI)

| Who | Role | Outlet | PIN |
|---|---|---|---|
| Nia | Owner | all | 482915 |
| Amina Hassan | Manager | Bogani Road | 603817 |
| Brian Otieno | Manager | Jogoo Road | 715204 |
| Akinyi Odhiambo | Server | Bogani Road | 2580 |
| Kevin Mutua | Server | Bogani Road | 4096 |
| Faith Wanjiru | Server | Jogoo Road | 7391 |

The demo has 7 days of consistent sales, a closed Z-report with a KES 200 cash variance, one flagged payment, one
voided order, one discount, unverified mobile-money payments and a held order today.

## Things worth trying on the emulator

- Sign in as Akinyi: only POS and Orders appear. Add dishes, use Options for half plate and add-ons, pay with M-Pesa
  (any 10 letters/digits, for example `QWERTY1234`; reusing a code is rejected). Ask for a discount: a manager PIN is required.
- Sign in as Amina: Reports, Shift tab. Verify the unverified payments, count the cash, close the shift, share the PDF.
- Sign in as Nia: Dashboard with both outlets. Compare its totals with Reports, Summary: they must match.
- Wrong PIN five times: the profile locks for 5 minutes. Leave the app idle for 2 minutes: it locks.
- Rotate the device mid-order: the cart survives. Kill the app from recents and reopen: cart is restored, session is not.

## See the first-run Setup wizard

Debug builds seed demo data. To test the real first-run flow set `buildConfigField("boolean", "DEMO_SEED", "false")` in
the `debug` block of `app/build.gradle.kts`, then clear the app's data (or uninstall) and run again.

## Release builds

No demo code or PINs exist in release (`src/release` has a no-op seeder). Signing reads `KEYSTORE_PATH`,
`STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` from environment variables. Screenshots are blocked (`FLAG_SECURE`).

## If sync or build fails

Most likely suspects, in order:
1. **Versions.** The catalog is a known-good set from late 2024 (AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28,
   Compose BOM 2024.12.01). Android Studio may offer to upgrade AGP. Accept after the first clean build.
2. **SQLCipher** (`net.zetetic:sqlcipher-android:4.5.6`, class `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`).
3. A Compose or Room API signature that changed. Paste the error and the file name.

## Tests

`./gradlew testDebugUnitTest` (JVM only): Money, Permissions, rules and lockout, real bcrypt hashing, aggregation
consistency (gross equals cash plus mobile money, per-day totals equal range totals, voids excluded), business-day logic.
Not covered yet: DAO and token-sequence tests (instrumented), Compose UI tests (added by the hardening pass).

GitHub Actions runs `codeQualityCheck`, `lint` and `testDebugUnitTest` on pushes and pull requests to `main` and
`develop`. Failed and successful Gradle reports are retained as workflow artifacts for 7 days. See
`.github/workflows/ci.yml`.

## Docs

`docs/RBAC_MATRIX.md`, `docs/DATA_CLASSIFICATION.md`, `docs/ARCHITECTURE.md` (includes the deliberate v1 simplifications).
