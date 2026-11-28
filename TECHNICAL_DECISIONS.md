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

## TD-005: Ledger Source of Truth & Protected Funds Semantics
- **Decision**: `TransactionEntity` is authoritative for wallet balances (`INCOME`, `EXPENSE`, `TRANSFER` only; `GOAL_ALLOCATION` and `GOAL_WITHDRAWAL` do not alter wallet balance). `GoalAllocationEntity` is authoritative for goal and wallet-specific allocations. `DIRECT_EXPENSE` withdrawals link `GOAL_WITHDRAWAL` and `EXPENSE` via `linkedExpenseTransactionId` inside a single atomic transaction.
- **Decision**: `Protected Funds` is a subset of `Allocated Money` determined by each Goal's current `protectionMode` (`PROTECTED` or `STRICT`).

## TD-006: Overflow-Safe Integer Arithmetic (`SafeMath`)
- **Decision**: All monetary additions and subtractions use `Math.addExact` / `Math.subtractExact` via `SafeMath`, throwing `DomainException.ArithmeticOverflowException` before DB commit.

## TD-007: Figma Visual Reference Adoption (Phase 2+ UI)
- **Decision**: Adopt layout hierarchy, `#F5F5FA`/`#FFFFFF` surface contrast, `#060D2D` ink, and semantic financial colors (`#039855`, `#D92D20`, `#FA9600`) from Figma `kFd3ya5EowknzzvzFr4fEn`, while retaining Deep Forest Green (`#166534`) as primary and excluding online e-wallet/payment features.

## TD-008: Integer-Only Monetary Input (`CurrencyInputParser`)
- **Decision**: Parse wallet opening balance inputs as digits-only `Long` via `CurrencyInputParser.parseNonNegativeIdr`, rejecting blanks, decimals, signs, and `Long` overflow rather than silently coercing invalid input.

## TD-009: Calendar-Day Tracking Start Date & Archived Wallet Immutability
- **Decision**: Compare `transactionDate` and `wallet.trackingStartDate` at local calendar-day granularity (`LedgerValidator.isBeforeTrackingDay`), converting Material 3 `DatePicker` UTC midnight to local start-of-day.
- **Decision**: Reject edits or deletes on transactions involving any currently archived wallet (original or resulting), and block Phase 3 mutation usecases from modifying `GOAL_ALLOCATION` or `GOAL_WITHDRAWAL` records directly.

## TD-010: Append-Only Linked Goal Ledger & Overflow-Safe Progress
- **Decision**: Expenses created via `WithdrawFromGoalUseCase(DIRECT_EXPENSE)` cannot be edited or deleted independently through Phase 3 transaction mutation usecases; corrections use new valid operations.
- **Decision**: `FinancialEngine.calculateGoalProgressPermille` uses `BigInteger` integer division so `Long.MAX_VALUE` targets never overflow.

## TD-011: Dashboard Reactive Observation & Privacy Masking
- **Decision**: `ObserveDashboardUseCase` combines the 5 repository Room Flows and converges to the latest committed database state after atomic writes (without claiming cross-table SQLite snapshot atomicity on `combine`).
- **Decision**: `DashboardViewModel` holds `amountsHidden` in presentation state so toggling privacy masks all dashboard monetary values without altering `DashboardSnapshot`.

## TD-012: SQL-Aggregated Ledger Validation & Keyset Transaction Paging
- **Decision**: Replace full-history `getAllTransactions()` loads in `LedgerValidator`, `ObserveDashboardUseCase`, and `TransactionsScreen` with indexed SQL component-sum queries (`WalletLedgerTotals`, `MonthlyActivityTuple`), `LIMIT 5` recent queries, and 3-column keyset paging (`transactionDate`, `createdAt`, `id`, `BATCH_SIZE = 20`).
- **Rationale**: Keeps memory and write validation bounded as ledger history grows while preserving every `SafeMath`, resulting-state, transfer, tracking-date, and Protected Funds invariant inside `runInTransaction`.

## TD-013: Budget Overlap Prevention & Alert Deduplication
- **Decision**: Enforce strict non-overlapping active budget envelopes per `categoryId` across all 6 `MONTHLY` / `WEEKLY` / `CUSTOM` combinations (`BudgetPeriodResolver.overlaps`), and persist `lastAlertedPeriodKey` + `lastAlertedLevel` inside `runInTransaction` to deduplicate 80%/100% in-app threshold alerts per period.

## TD-014: Reports SQL Aggregation, Transfer Separation & Historical Semantics
- **Decision**: Implement Phase 7 Reports with zero DB migration on `SakuGuardDatabase v2`, using native Compose `Canvas` charts and SQL aggregation (`TransactionDao`, `GoalAllocationDao` joined on `transactions.transactionDate`).
- **Decision**: Separate `Transfer In` and `Transfer Out` per wallet in Wallet Activity while excluding transfers from Total Income, Total Expense, and Net Cash Flow; use `BigInteger` for overflow-safe trend/category percentages (`null` on zero baseline); and document current-name/current-limit semantics for historical categories and budgets without adding snapshot tables.

## TD-015: Portable `.saku` Backup Format, Optional AES-256-GCM & Transactional Restore
- **Decision**: Export all 6 Room entities into a `.saku` ZIP (`manifest.json` + `payload.json` or `payload.enc`) using `kotlinx.serialization.json`, strict `ZipFile` validation (`64 MB` archive / `128 MB` payload / `2` entries), `SHA-256` checksum, and optional `PBKDF2WithHmacSHA256` (`210k` iterations) + `AES/GCM/NoPadding` with canonical manifest AAD.
- **Decision**: Enforce full `BackupValidator` financial invariants before live DB mutation and perform replace-all restore inside a single `database.withTransaction` (preserving Hilt's `@Singleton` `SakuGuardDatabase` and guaranteeing rollback on any failure). Supports `formatVersion = 1` / `schemaVersion = 2`.

## TD-016: Release Privacy (`FLAG_SECURE`), R8 Verification & Secret-Free Signing Hook
- **Decision**: Apply `WindowManager.LayoutParams.FLAG_SECURE` only when `!BuildConfig.DEBUG` so debug builds remain testable while release builds protect recent-apps thumbnails. Configure release signing optionally via gitignored `keystore.properties` or `SAKUGUARD_KEYSTORE_*` environment variables, and verify both `assembleRelease` and `bundleRelease` with R8 enabled in GitHub Actions CI.

## TD-017: Background Flow Offloading & Bounded Portfolio/Detail Observation
- **Decision**: All reactive `combine` UseCases (`Dashboard`, `Wallets`, `Goals`, `Budgets`, `Reports`, `TransactionDetail`) apply `.distinctUntilChanged().flowOn(Dispatchers.Default)` so `FinancialEngine` and mapping work never run on `Dispatchers.Main.immediate`.
- **Decision**: Replace `observeTransactions()` in `Wallets`, `Goals`, and `TransactionDetail` with `observeAllWalletLedgerTotals()` (`*FromTotals` engine methods) and `observeTransactionById(id)`. Cache `DecimalFormat` per thread (`ThreadLocal`) and `DateTimeFormatter` per `Locale`.

## TD-018: Ledger Change Signal & Restricted Hard Deletion (No Schema Migration)
- **Decision**: Drive `TransactionsViewModel` reactive reloads using a single bounded SQL aggregate `observeLedgerChangeSignal()` (`COUNT(*)`, `COALESCE(MAX(updatedAt), 0)`, `COALESCE(SUM(amount), 0)`) rather than `observeRecentTransactions(limit = 1)`, so edits to older transactions and deletes trigger reload immediately.
- **Decision**: Keep `Wallet` and `Goal` archive-first for any record with financial history (`transactions` or `goal_allocations`), permitting hard deletion (`DeleteWalletUseCase`, `DeleteGoalUseCase` inside `runInTransaction`) strictly when both counts equal `0`.
- **Decision**: Use lightweight `tween(180)` cross-fade transitions in `SakuNavHost` and remove per-frame `expandHorizontally` / `spring` animations in `SakuBottomBar` to eliminate bottom-bar relayout jank.
