# Pocket Ledger MD

A simple single-user Android bookkeeping app that stores all records in local Markdown files.

## Scope (MVP)
- Add one income/expense record quickly
- Save records into monthly Markdown files (no database)
- View current month records
- View current month summary (income/expense/balance)

## Tech
- Kotlin
- Jetpack Compose
- Local file storage (`filesDir/ledger/*.md`)

## Data location
The app stores data under:
`/data/data/com.example.pocketledgermd/files/ledger/`

Each month is one file: `YYYY-MM.md`

## Build
Open this folder with Android Studio and run the `app` module.

Required local SDK:
- Android SDK Platform 36
- Android SDK Build-Tools 36+
- Android SDK Platform-Tools

App runtime compatibility:
- Android 5.0+ (minSdk 21)
- targetSdk 34 (for broader device installer compatibility)

## Notes
- Offline only
- No login, no cloud sync
- Single user only
