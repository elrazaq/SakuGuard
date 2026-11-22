# Development Log

## Phase 0 — Project Foundation (2026-11-22)
- Initialized SakuGuard Android project (`com.sakuguard.app`, `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`).
- Configured Gradle `8.9`, AGP `8.7.3`, Kotlin `2.0.21`, KSP `2.0.21-1.0.28`, Compose BOM `2024.11.00`, Hilt `2.52`, Navigation Compose `2.8.4`, Room `2.6.1` (catalog).
- Implemented Material 3 Deep Forest Green Design System (`LightColorScheme` and `DarkColorScheme`) and compact typography/shapes.
- Set up 5-tab Bottom Navigation (`Home`, `Wallets`, `Goals`, `Transactions`, `Reports`) with empty-state placeholder screens and zero fake financial data.
- Blocked `android.permission.INTERNET` via `tools:node="remove"` in `AndroidManifest.xml`.
- Added English (`values/strings.xml`) and Indonesian (`values-in/strings.xml`) string resources, plus `CurrencyFormatter` (`Long` -> `Rp1.500.000`) with unit tests.
- Configured GitHub Actions CI (`.github/workflows/android.yml`) on JDK 17 using the Gradle Wrapper.
