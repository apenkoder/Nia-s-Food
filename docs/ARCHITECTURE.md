# Architecture

Single Gradle module `:app`, package `com.apenkoder.niasfood`, Kotlin + Jetpack Compose + Material 3.

```
core/      Money (Long cents), time (Africa/Nairobi, business day), Outcome/AppError
domain/    Roles, permissions (RBAC), rules (mobile-money code, PIN rules, lockout policy)
data/
  local/     Room entities, DAOs, encrypted database (SQLCipher)
  security/  bcrypt PinHasher, Keystore-wrapped DatabaseKeyProvider, SessionManager (idle/background lock)
  repo/      AuthService, SalesService, MenuService, ReportService, Aggregator, Audit
  export/    Z-report PDF writer and share intent
  demo/      DemoSeeder (src/debug = real seeder, src/release = no-op)
di/        AppContainer (manual DI, no Hilt/Dagger)
ui/        theme, components, navigation shell, one package per screen (Screen + ViewModel + UiState)
```

## Decisions

- **Manual DI** and **KSP only for Room** to keep builds light on an 8 GB machine. No Hilt, no kapt.
- **Money is `Long` cents** everywhere. `Money` is a value class used at the edges; entities store `Long`.
- **One calculation for every number.** `Aggregator.summarize` feeds the Dashboard, Reports summary and the Z-report,
  so totals cannot drift apart (covered by `AggregatorTest`).
- **Prices never come from the UI.** `SalesService.submit` re-reads item and add-on prices from the database and
  re-checks sold-out state inside one Room transaction.
- **Business day** starts at the outlet's opening time, not midnight. Token numbers are sequential per outlet per
  business day and allocated inside the same transaction (unique index as a backstop).
- **Offline-first, no network.** The manifest has no INTERNET permission. `sync_outbox` is written on order and expense
  changes so a backend can be added later without touching screens.
- **Z-report vs daily summary.** A Z-report is per shift (orders, payments and expenses tagged with the shift id). The
  Summary tab and Dashboard are per business date. With one shift per outlet per day they match.
- **Bytecode target 17.** Gradle runs on JDK 21 (set the Gradle JDK in Android Studio). Java 21 bytecode with AGP 8.7
  is avoided on purpose. `gradle-daemon-jvm.properties` is not used because Gradle 8.9 does not need it.

## Deliberate v1 simplifications (refactor path)

- Room entities are used directly by services and ViewModels (no separate domain model classes). Services in `data/repo`
  stand in for repository interfaces + use cases. Split them when a second data source (backend) arrives.
- Not in v1: receipt photos, expense backdating, outlet-hours editing after setup, Swahili translations, printing,
  stock/batch tracking, backend sync, Compose UI tests, instrumented DAO tests.
