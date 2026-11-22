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
