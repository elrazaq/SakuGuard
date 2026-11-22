# SakuGuard

> **Track your money. Protect your goals.**

SakuGuard is a fully offline, privacy-first personal finance and savings goal manager for Android.

## Principles
- **Offline-first & Privacy-first**: Zero internet permission (`INTERNET` explicitly removed), no accounts, no cloud, no analytics or tracking SDKs.
- **Financial Accuracy**: Deterministic integer-based (`Long`) Indonesian Rupiah (`IDR`) accounting.
- **Goal Protection**: Allocate funds across multiple wallets to savings goals and protect them with Standard, Protected, or Strict Protection modes.
- **Local Data Ownership**: Encrypted `.saku` backups (JSON + ZIP + AES-GCM + PBKDF2) via Android Storage Access Framework.

## Tech Stack
- Kotlin `2.0.21` + Jetpack Compose + Material 3
- Single-module `:app` with Clean Architecture packages (`core/`, `data/`, `domain/`, `feature/`)
- Hilt DI (`2.52`), Room (`2.6.1`), Navigation Compose (`2.8.4`), DataStore (`1.1.1`)
- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`

## Building
Builds and unit tests run via the standard Gradle Wrapper on JDK 17:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

GitHub Actions CI (`.github/workflows/android.yml`) runs both tasks automatically on `push` and `pull_request`.
