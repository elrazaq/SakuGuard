# SakuGuard

> **Track your money. Protect your goals.**

SakuGuard is a fully offline, privacy-first personal finance, budgeting, reporting, and savings goal manager for Android.

## Key Features
- **Dashboard**: Real-time Total Assets, Free Money, Allocated Money, Protected Funds, monthly cash flow, and privacy balance toggle.
- **Wallets**: Multi-wallet tracking (Bank, Cash, E-Wallet, Other), opening balances, tracking start dates, and archive/restore.
- **Transactions**: Income, Expense, and Transfer ledger with search, filters, and keyset incremental scrolling (`20` items per batch).
- **Savings Goals & Protection**: Multi-wallet allocations with `Standard`, `Protected`, and `Strict` unlock modes, plus `Direct Expense` linked withdrawals.
- **Budgeting**: Recurring `Monthly` and `Weekly` budgets, `Custom` planned spending envelopes, overlap prevention, and 80%/100% threshold alerts.
- **Reports**: Period overview, Income vs Expense bar chart, Spending Trend comparison, Expense/Income category breakdowns with drill-down, Wallet Activity (separating transfers), Budget Performance, and Savings Goals.
- **Backup & Restore**: Portable offline `.saku` archive (ZIP + JSON + SHA-256 + optional `PBKDF2WithHmacSHA256` / `AES-256-GCM`), pre-commit financial validation, and atomic transactional restore via Android Storage Access Framework.
- **Bilingual UI**: English (`en`) and Indonesian (`in`), Material 3 Light & Dark themes.

## Principles
- **Offline-first & Privacy-first**: Zero internet permission (`INTERNET` explicitly removed), `android:allowBackup="false"`, no accounts, no cloud, no analytics or tracking SDKs, and `FLAG_SECURE` enabled in release builds.
- **Financial Accuracy**: Deterministic integer-based (`Long`) Indonesian Rupiah (`IDR`) accounting with overflow-checked `SafeMath` and atomic Room transactions.

## Tech Stack
- Kotlin `2.0.21` + Jetpack Compose + Material 3
- Single-module `:app` (`core/`, `data/`, `domain/`, `feature/`)
- Hilt DI (`2.52`), Room (`2.6.1`, schema `v2`), Navigation Compose (`2.8.4`), `kotlinx.serialization.json`
- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`

## Building & Testing
Builds and unit tests run via the Gradle Wrapper on JDK 17:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleRelease
./gradlew bundleRelease
```

See [ROADMAP.md](ROADMAP.md) and [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for project status and release verification.

## License
Licensed under the [Apache License, Version 2.0](LICENSE).
