# Development Log

## Phase 0 — Project Foundation (2026-11-22)
- Initialized SakuGuard Android project (`com.sakuguard.app`, `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`).
- Configured Gradle `8.9`, AGP `8.7.3`, Kotlin `2.0.21`, KSP `2.0.21-1.0.28`, Compose BOM `2024.11.00`, Hilt `2.52`, Navigation Compose `2.8.4`, Room `2.6.1` (catalog).
- Implemented Material 3 Deep Forest Green Design System (`LightColorScheme` and `DarkColorScheme`) and compact typography/shapes.
- Set up 5-tab Bottom Navigation (`Home`, `Wallets`, `Goals`, `Transactions`, `Reports`) with empty-state placeholder screens and zero fake financial data.
- Blocked `android.permission.INTERNET` via `tools:node="remove"` in `AndroidManifest.xml`.
- Added English (`values/strings.xml`) and Indonesian (`values-in/strings.xml`) string resources, plus `CurrencyFormatter` (`Long` -> `Rp1.500.000`) with unit tests.
- Configured GitHub Actions CI (`.github/workflows/android.yml`) on JDK 17 using the Gradle Wrapper.

## Phase 1 — Core Database & Financial Engine (2026-11-22)
- Adopted Figma Community UI Kit (`kFd3ya5EowknzzvzFr4fEn`) as the Phase 2+ visual reference while preserving Deep Forest Green (`#166534`) identity.
- Implemented Room `SakuGuardDatabase` (`version = 1`, `exportSchema = true`) with 5 normalized entities (`WalletEntity`, `CategoryEntity`, `GoalEntity`, `TransactionEntity`, `GoalAllocationEntity`), DAOs, `ForeignKey.RESTRICT`, and `SakuDatabaseSeeder`. Deferred `BudgetEntity`/`BudgetDao` to Phase 6.
- Implemented `SafeMath` (`Math.addExact` / `Math.subtractExact`) and pure Kotlin `FinancialEngine` (`Long`-only, zero double-counting, `Protected Funds <= Allocated Money <= Total Assets`, `Total Assets == Free Money + Allocated Money`).
- Implemented repository layer (`data/repository/`) with `withTransaction` atomicity and invariant-checking UseCases (`CreateWalletUseCase`, `UpdateWalletUseCase`, `ArchiveWalletUseCase`, `CreateGoalUseCase`, `UpdateGoalProtectionUseCase`, `ArchiveGoalUseCase`, `AllocateToGoalUseCase`, `WithdrawFromGoalUseCase`, `RecordTransactionUseCase`, `UpdateTransactionUseCase`, `DeleteTransactionUseCase`).
- Added JVM unit test suites: `SafeMathTest`, `FinancialEngineTest`, and `TransactionInvariantsTest`.

## Phase 2 — Wallet Management (2026-11-22)
- Evolved `core/designsystem/` (`Color.kt`, `Theme.kt`, `Spacing.kt`, `SakuComponents.kt`) with Figma-adapted surface hierarchy (`#F5F5FA` canvas, `#060D2D` ink), semantic financial tokens, Deep Forest Green (`#166534`) `SakuHeroBalanceCard`, and `SakuKpiPairCard`.
- Added `CurrencyInputParser` (`Long`-only integer input validation) and `DomainException.InvalidInputException`.
- Added `ObserveWalletPortfolioUseCase` and `ObserveWalletDetailUseCase` powered by `FinancialEngine`, plus non-blank name validation in `CreateWalletUseCase` / `UpdateWalletUseCase`.
- Implemented `WalletsScreen`, `WalletDetailScreen`, `WalletFormScreen` (Create/Edit), `WalletsViewModel`, `WalletDetailViewModel`, `WalletFormViewModel`, `WalletVisuals`, nested routes in `SakuNavHost`, and full EN + ID strings.
- Added unit tests: `CurrencyInputParserTest`, `WalletUseCasesTest`, `WalletsViewModelTest`, `WalletFormViewModelTest`, `WalletDetailViewModelTest`.

## Phase 3 — Transaction Management (2026-11-22)
- Added `DateFormatter` (`java.time` calendar-day & Material 3 `DatePicker` UTC-midnight normalization) and `LedgerValidator.isBeforeTrackingDay` so same-day transactions are accepted across timezones.
- Hardened `UpdateTransactionUseCase` & `DeleteTransactionUseCase`: reject mutations involving archived wallets (original or resulting), reject direct edits/deletes on `GOAL_ALLOCATION` and `GOAL_WITHDRAWAL`, and enforce `CategoryType` matching on category updates.
- Added `ObserveTransactionsFeedUseCase`, `CategoryVisuals` (EN/ID localized default categories), `TransactionsViewModel`, `TransactionFormViewModel`, `TransactionDetailViewModel`.
- Implemented `TransactionsScreen` (search, type/wallet/category/date filters, monthly grouping, empty states), `TransactionFormScreen` (Income/Expense/Transfer, locked type on edit, double-submit guard), `TransactionDetailScreen`, and `SakuNavHost` routes.
- Added tests: `TransactionUseCasesPhase3Test`, `TransactionsViewModelTest`, `TransactionFormViewModelTest`, `TransactionDetailViewModelTest`.

## Phase 4 — Savings Goals & Protection (2026-11-22)
- Added `FinancialEngine.calculateGoalProgressPermille` using `BigInteger` for overflow-safe `[0, 1000]` permille progress.
- Extended `GoalUseCases` with `UpdateGoalUseCase`, `RestoreGoalUseCase`, `UpdateGoalStatusUseCase`, `ObserveGoalsPortfolioUseCase`, `ObserveGoalDetailUseCase`, blank-name validation, `COMPLETED` allocation lock, and mandatory withdrawal `reason` for `PROTECTED`/`STRICT`.
- Locked `DIRECT_EXPENSE` linked `EXPENSE` transactions from independent Phase 3 edit/delete in `UpdateTransactionUseCase`, `DeleteTransactionUseCase`, and `TransactionDetailViewModel`.
- Implemented `GoalsScreen`, `GoalFormScreen`, `GoalDetailScreen`, `GoalAllocateScreen`, `GoalWithdrawScreen`, `GoalVisuals`, ViewModels, `SakuNavHost` routes, and EN/ID localization.
- Added tests: `GoalUseCasesPhase4Test`, `GoalsViewModelTest`, `GoalFormViewModelTest`, `GoalDetailViewModelTest`, `GoalActionViewModelTest`.

## Phase 5 — Dashboard & Visual Polish (2026-11-22)
- Added SakuGuard adaptive & legacy launcher icon (`ic_launcher`, `ic_launcher_round`) and full-bleed scroll with bottom opacity fade above `SakuBottomBar`.
- Added `FinancialEngine.calculateMonthlyActivity` (`MonthlyActivity`) using `SafeMath` and explicit `YearMonth`/`ZoneId` local calendar-month boundaries (excluding `TRANSFER`, `GOAL_ALLOCATION`, `GOAL_WITHDRAWAL`).
- Added `ObserveDashboardUseCase`, `DashboardViewModel` (with presentation-layer `amountsHidden` privacy toggle masking all dashboard monetary figures), and `DashboardScreen` with independent section empty states and navigation to detail/list/create screens.
- Added `ObserveDashboardUseCaseTest` and `DashboardViewModelTest`.

## Phase 6 — Budgeting & Scalability Architecture (2026-11-22)
- Upgraded `SakuGuardDatabase` to `version = 2` with `BudgetEntity` (`budgets`), `BudgetDao`, `MIGRATION_1_2` (non-destructive), and composite `transactions` indices (`(type, categoryId, transactionDate)` and `(transactionDate, createdAt, id)`).
- Remediated scalability bottlenecks without weakening financial invariants:
  - `LedgerValidator.validateAffectedWallets`: SQL component-sum aggregation (`WalletLedgerTotals`) inside `runInTransaction` preserving `SafeMath`, transfers, tracking-date checks, goal/protected-funds bounds, and archive guards.
  - `ObserveDashboardUseCase`: bounded queries (`observeRecentTransactions(5)`, `observeMonthlyActivity`, `observeAllWalletLedgerTotals`).
  - `TransactionsScreen` & `TransactionsViewModel`: 3-column keyset incremental scroll (`BATCH_SIZE = 20`) with SQL-level filters and inline retry.
- Implemented `BudgetPeriodResolver` (`MONTHLY`, `WEEKLY`, `CUSTOM`), 6-case cross-period overlap prevention per category, `BudgetUseCases` (CRUD/Archive/Restore + `ObserveBudgetsUseCase` + `EvaluateBudgetAlertsUseCase` with per-period deduplication), and Budget UI (`BudgetsScreen`, `BudgetFormScreen`, `BudgetDetailScreen`).
- Added unit tests (`FinancialEngineBudgetTest`, `BudgetUseCasesTest`, `BudgetsViewModelTest`, updated `TransactionsViewModelTest` for keyset paging) and `Migration1To2AndQueryPlanTest` (`androidTest`).

## Phase 7 — Reports (2026-11-22)
- Added `ReportModels` (`ReportPeriodPreset`, `ReportPeriodRange`, `ReportsSnapshot`, `WalletActivityItem`, `SpendingTrendSummary`, `GoalPeriodSummary`, `BudgetPerformanceSummary`) and `ReportPeriodResolver` (exact calendar/custom boundaries, previous equivalent periods, ≤24 capped time buckets, `BigInteger` overflow-safe percentages with `null` zero-baseline).
- Added zero-migration SQL aggregation queries in `TransactionDao` (`observeCategoryTotalsForPeriod`, `observeWalletActivityForPeriod` separating `Transfer In`/`Transfer Out` from `Income`/`Expense`, `getPeriodTotals` inside `@Transaction`) and `GoalAllocationDao` (`observePeriodGoalContributions` joining `transactions.transactionDate`).
- Implemented `ObserveReportsUseCase`, `ReportsViewModel`, `ReportCategoryDetailViewModel` (keyset `LIMIT 20` drill-down), `ReportCharts` (native Compose `Canvas` grouped bar, trend line, and proportion bar), `ReportsScreen`, `ReportCategoryDetailScreen`, `SakuNavHost` routes, and EN/ID localization.
- Added unit tests (`ReportPeriodResolverTest`, `ReportUseCasesTest`, `ReportsViewModelTest`) and `ReportQueryPlanTest` (`androidTest`).

## Phase 8 — Backup & Restore (2026-11-22)
- Implemented portable `.saku` backup format (`manifest.json` + `payload.json` or `payload.enc` ZIP container) with strict defensive limits (`MAX_ARCHIVE_BYTES = 64 MB`, `MAX_MANIFEST_BYTES = 64 KB`, `MAX_PAYLOAD_BYTES = 128 MB`, `MAX_ENTRY_COUNT = 2`, `ZipFile` central-directory validation, path-traversal/duplicate rejection, 50x compression-ratio guard, and manifest-vs-payload `recordCounts` verification).
- Implemented `BackupCrypto` (`PBKDF2WithHmacSHA256` at `210,000` iterations, 16B `SecureRandom` salt, 12B IV, `AES/GCM/NoPadding` 128-bit tag with canonical manifest AAD, `SHA-256` checksum, and `CharArray` zeroing).
- Implemented `BackupValidator` verifying all FK, transaction/category, transfer, `DIRECT_EXPENSE`, derived balance/Free Money/Protected Funds, tracking-date, `SafeMath`, and budget invariants prior to DB mutation.
- Implemented `BackupRepositoryImpl` (single `withTransaction` export snapshot + self-verification + SAF write/cleanup; transactional reverse-FK `deleteAll` + forward-FK chunked `insertAll` restore), `BackupRestoreViewModel`, `BackupRestoreScreen`, `SakuNavHost` route, `DashboardScreen` entry button, and EN/ID strings.
- Added unit tests (`BackupCryptoTest`, `BackupArchiveCodecTest`, `BackupValidatorTest`, `BackupRestoreViewModelTest`) and `BackupRestoreDatabaseTest` (`androidTest`).

## Phase 9 — Polish & Production (2026-11-22)
- Fixed `BudgetFormScreen` `DatePicker` UTC-to-local conversion (`DateFormatter.localMillisToUtcDatePickerMillis` / `utcDatePickerMillisToLocalStartOfDay`) and added `imePadding()` + `navigationBarsPadding()` across nested forms/screens.
- Added TalkBack `contentDescription` strings (`common_back`, `common_dismiss`) on all remaining `IconButton`s and `Modifier.semantics` summaries on `ReportCharts` (`Canvas` charts) in EN and ID.
- Added `res/values-night/themes.xml` and light/dark `android:windowBackground` in `themes.xml` to eliminate dark-mode cold-start white flash.
- Enabled `WindowManager.LayoutParams.FLAG_SECURE` in `!BuildConfig.DEBUG` (`MainActivity`), bumped `versionName = "1.0.0"` (`versionCode = 1`), added optional `keystore.properties` / env-var release signing hook, added `assembleRelease` & `bundleRelease` (R8) to `.github/workflows/android.yml`, and created `RELEASE_CHECKLIST.md`.

## Post-Release Performance Optimization (2026-11-28)
- Eliminated remaining full-table `observeTransactions()` / `getAllTransactions()` reads in `ObserveWalletPortfolioUseCase`, `ObserveWalletDetailUseCase`, `UpdateWalletUseCase` (`getEarliestTransactionDateForWallet`), `ObserveGoalsPortfolioUseCase`, `ObserveGoalDetailUseCase`, and `TransactionDetailViewModel` (`ObserveTransactionDetailUseCase` / `observeTransactionById`).
- Offloaded `combine` calculations in all reactive UseCases (`Dashboard`, `Wallets`, `Goals`, `Budgets`, `Reports`, `TransactionDetail`) to `Dispatchers.Default` via `.flowOn(Dispatchers.Default)`.
- Cached `DecimalFormat` (`ThreadLocal`) in `CurrencyFormatter`, `DateTimeFormatter` (`ConcurrentHashMap`) in `DateFormatter`, `Path` via `drawWithCache` in `SpendingTrendLineChart`, `formatMoney` & static `LazyColumn` keys in `DashboardScreen`, and `fadeBrush` in `MainActivity`.

## Post-Release Stability, Deletion & Navigation Polish (2026-11-28)
- **Transaction History Auto-Refresh**: Replaced `observeRecentTransactions(limit = 1)` + `latestList.isNotEmpty()` in `TransactionsViewModel` with bounded scalar `observeLedgerChangeSignal()` (`COUNT(*)`, `MAX(updatedAt)`, `SUM(amount)`), ensuring inserts, edits to any existing transaction, and deletes immediately reload the active keyset feed without touching filters.
- **Restricted Wallet & Goal Deletion**: Added `DeleteWalletUseCase` and `DeleteGoalUseCase` (executed inside `runInTransaction`) allowing hard deletion only when both transaction count (`walletId`/`destinationWalletId` or `goalId`) and goal allocation count are `0`; otherwise rejects with `wallet_error_delete_has_history` / `goal_error_delete_has_history`. Wired `Delete` action + confirmation `AlertDialog` in `WalletDetailScreen` and `GoalDetailScreen` with EN/ID strings.
- **Navigation & Bottom Bar Transitions**: Added crisp `tween(180)` `fadeIn`/`fadeOut` transitions on `SakuNavHost`, simplified `SakuBottomBar` color transitions to `tween(150)`, and removed `iconScale` spring and `expandHorizontally`/`shrinkHorizontally` layout animations.
- **Tests**: Added restricted deletion tests in `WalletDetailViewModelTest` and `GoalDetailViewModelTest`, and auto-refresh insert/edit/delete coverage in `TransactionsViewModelTest`.
