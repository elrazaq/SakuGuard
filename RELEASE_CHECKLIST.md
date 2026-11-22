# SakuGuard v1.0.0 Release Checklist

## 1. Automated CI Verification
- [x] `./gradlew assembleDebug --no-daemon`
- [x] `./gradlew testDebugUnitTest --no-daemon`
- [x] `./gradlew assembleRelease --no-daemon` (R8 + `isShrinkResources = true`)
- [x] `./gradlew bundleRelease --no-daemon` (R8 + `isShrinkResources = true`)

## 2. Instrumented Tests (Requires Local Android Emulator or Physical Device)
- [ ] `./gradlew connectedDebugAndroidTest`
  - `Migration1To2AndQueryPlanTest`
  - `ReportQueryPlanTest`
  - `BackupRestoreDatabaseTest`

## 3. Manual QA Verification
- [ ] Cold start in Light Mode & Dark Mode (verify no white flash on dark start).
- [ ] Wallet CRUD, archive/restore, tracking start date boundaries.
- [ ] Transaction CRUD, search, keyset scroll (`20` per batch), filters.
- [ ] Savings Goals: `STANDARD`, `PROTECTED`, `STRICT` unlock + `DIRECT_EXPENSE` immutability.
- [ ] Dashboard: Total Assets / Free Money / Allocated / Protected Funds + privacy toggle.
- [ ] Budgeting: `MONTHLY` / `WEEKLY` / `CUSTOM` creation (`DatePicker`), 80%/100% alerts.
- [ ] Reports: Period presets, charts, category drill-down, `Transfer In`/`Transfer Out` separation.
- [ ] Backup & Restore: Encrypted (`.saku`) & unencrypted export, wrong-password rejection, atomic replace-all restore.
- [ ] Release APK: Verify `FLAG_SECURE` blocks recent-apps thumbnail & screenshots in release build.

## 4. Release Signing (Local / Env Only — Never Commit Secrets)
Provide either `keystore.properties` in project root (gitignored) or environment variables:
- `SAKUGUARD_KEYSTORE_PATH`
- `SAKUGUARD_KEYSTORE_PASSWORD`
- `SAKUGUARD_KEY_ALIAS`
- `SAKUGUARD_KEY_PASSWORD`

Then run:
```bash
./gradlew assembleRelease bundleRelease
```
