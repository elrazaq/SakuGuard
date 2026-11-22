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
