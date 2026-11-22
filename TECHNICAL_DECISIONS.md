# Technical Decisions

## TD-001: Single-Module (`:app`) with Package Boundaries
- **Decision**: Use a single `:app` Gradle module with `core/`, `data/`, `domain/`, and `feature/` packages.
- **Rationale**: Keeps Gradle configuration simple, builds fast, and avoids premature multi-module boilerplate while preserving clean layer separation.

## TD-002: Integer (`Long`) Currency Representation
- **Decision**: Represent all IDR amounts as `Long` (never `Double` or `Float`).
- **Rationale**: Eliminates floating-point rounding errors and guarantees deterministic financial calculations.

## TD-003: Explicit Removal of `INTERNET` Permission
- **Decision**: Declare `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` in `AndroidManifest.xml`.
- **Rationale**: Prevents any transitive library from introducing network permission into the merged manifest, enforcing the offline-first guarantee.

## TD-004: GitHub Actions CI Build Verification
- **Decision**: Run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` on GitHub Actions (JDK 17) using the committed Gradle `8.9` Wrapper.
- **Rationale**: Local environment intentionally omits the Android SDK; CI ensures reproducible builds and automated test verification.
