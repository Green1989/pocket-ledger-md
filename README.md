# Pocket Ledger MD

Pocket Ledger MD is a single-user Android bookkeeping app.
All ledger data is stored in local Markdown files only.

## Current Scope

This project has gone beyond the original MVP and currently includes:

- Quick add income/expense entries
- Edit and delete existing entries
- Date/time picker (limited to current time and earlier)
- Date filters: today, week, month, year, all
- Member dimension (multi-member records + member-based view filter)
- Monthly summary (income, expense, balance)
- Expense category aggregation and category detail drill-down
- Custom categories (persisted locally)
- Clipboard share text generation (1 day and custom N days)
- Clipboard sync import with structured sync block parsing
- Sync diagnostics logging (copy latest log to clipboard)
- Backup and restore from external directory (SAF DocumentTree)

## Core Tech

- Kotlin
- Jetpack Compose (Material 3)
- Android ViewModel + coroutines
- Local file storage only (`filesDir/ledger/*.md`)

## Build And Run

Open this folder in Android Studio and run module `app`.

You can also build from terminal with Gradle wrapper.
Wrapper is pinned to stable `Gradle 8.7`.

Build config:

- `compileSdk = 36`
- `minSdk = 21` (Android 5.0+)
- `targetSdk = 34`
- Kotlin/JVM target `17`

Required local environment:

- JDK `17` (recommended: bundled JDK from Android Studio)
- Android SDK Platform 36
- Android SDK Build-Tools 36+
- Android SDK Platform-Tools

Common commands:

- Windows build debug APK: `.\gradlew.bat :app:assembleDebug`
- Windows Kotlin compile check: `.\gradlew.bat :app:compileDebugKotlin`
- Windows unit tests: `.\gradlew.bat :app:testDebugUnitTest`
- macOS/Linux build debug APK: `./gradlew :app:assembleDebug`
- macOS/Linux unit tests: `./gradlew :app:testDebugUnitTest`

## Data Storage Layout

App private directory:

- Ledger files: `/data/data/com.example.pocketledgermd/files/ledger/`
- Sync diagnostics logs: `/data/data/com.example.pocketledgermd/files/sync_diagnostics/`
- Restore snapshots: `/data/data/com.example.pocketledgermd/files/ledger_backup_yyyyMMdd_HHmmss/`

Ledger file rule:

- One file per month: `YYYY-MM.md`

## Markdown Format

### File shape

```md
# 2026-03 Ledger

## 2026-03-02
- 09:30 | expense | 25.50 | 餐饮 | 早餐
- 19:10 | expense | 68.00 | 交通 | 打车 | @member=xiaoxin | @id=5f6d...
```

### Entry line format

```text
- HH:mm | type | amount | category | note | [optional metadata...]
```

Rules:

- `type`: `income` or `expense`
- `amount`: decimal with 2 digits when written
- `note`: can be empty
- optional metadata currently supports:
  - `@member=<code>`
  - `@id=<uuid>`

## Sync Block (Clipboard)

Share text can include a machine-readable sync block:

```text
---POCKET_LEDGER_SYNC_START---
V|2
D|2026-03-02
E|<id>|<iso_datetime>|expense|<member>|<amount>|<urlencoded_category>|<urlencoded_note>
---POCKET_LEDGER_SYNC_END---
```

Notes:

- Supports parsing legacy `V|1` data
- Import is id-based deduplication
- Parse/import failures are logged to `sync_diagnostics`

## UI Behavior Notes

- Default entry type: expense
- Member options (entry object): 少鑫, 洁丽, 童童, 老人, 所有人
- Member view filters: 全部(聚合), 少鑫, 洁丽, 童童, 老人, 所有人(仅 `member=all`)
- If type is expense + category is `餐饮`, note may auto-fill based on hour:
  - 06-10 早餐
  - 11-12 午餐
  - 13-15 下午茶
  - 16-20 晚餐
  - 21-23 宵夜

## Backup / Restore

- Backup: exports local monthly markdown files to selected directory under `ledger/`
- Restore: imports valid `YYYY-MM.md` files from selected directory (or its `ledger/` child)
- Before restore, app creates a local snapshot in app private storage

## Tests

Unit tests currently cover:

- Markdown codec round-trip and parsing edge cases
- Repository update/delete/import/snapshot key paths
- Sync codec round-trip and legacy compatibility

Test sources:

- `app/src/test/java/com/example/pocketledgermd/data/MarkdownCodecTest.kt`
- `app/src/test/java/com/example/pocketledgermd/data/MarkdownRepositoryTest.kt`
- `app/src/test/java/com/example/pocketledgermd/data/ShareSyncCodecTest.kt`

## Non-Goals

- No online account/login
- No cloud backend
- No server-side database
- No OCR/AI automation in current phase

## License

No license file is currently provided in this repository.
