# Contributing to SakuGuard

Thank you for your interest in contributing to **SakuGuard**!

## Project Rules & Invariants
Before opening a Pull Request, please ensure your changes respect SakuGuard's core architecture:
1. **Strictly Offline**: Never add `INTERNET` permissions, analytics SDKs, cloud sync, or network dependencies.
2. **Integer-Only Currency**: All IDR monetary values must use `Long` and `SafeMath` — never `Float` or `Double`.
3. **Ledger Integrity**: Wallet balances and goal allocations are derived from the ledger; preserve `LedgerValidator` and atomic `withTransaction` writes.
4. **Localization**: Keep both English (`app/src/main/res/values/strings.xml`) and Indonesian (`app/src/main/res/values-in/strings.xml`) up to date.
5. **Testing**: Add or update JVM unit tests (`app/src/test/`) for domain/ViewModel changes.

## Local Build & Verification
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

## Pull Request Process
1. Fork the repository and branch from `main`.
2. Keep commits focused and descriptive.
3. Verify all unit tests pass (`./gradlew testDebugUnitTest`).
4. Open a PR describing what changed and why.
