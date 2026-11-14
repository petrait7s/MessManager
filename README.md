# MessManager

Private, manager-only, fully offline mess accounting app for small bachelor messes (5–10 members).
Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Navigation Compose · PdfDocument. No network permission, no cloud.

## Get the APK (GitHub Actions)
Every push to `main` runs `.github/workflows/build.yml`: unit tests → debug APK → signed release APK, uploaded as the
`apks-and-reports` artifact and as a GitHub Release (`build-N`) with the release APK attached.
Install the `app-release.apk` on your phone (allow "install unknown apps").

### Signing
* Without secrets, CI signs the release APK with a throwaway key (installable, but each build has a different key → uninstall before updating).
* For stable updates add repo secrets: `KEYSTORE_BASE64` (`base64 -w0 my.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
* Local: `export KEYSTORE_FILE=/path/my.keystore KEYSTORE_PASSWORD=… KEY_ALIAS=… KEY_PASSWORD=…` then build. Without them the release APK is unsigned (`app-release-unsigned.apk`; sign with `apksigner`).

## Build locally
Requirements: JDK 17, Android SDK (platform 34, build-tools 34), Gradle 8.9+ (or generate a wrapper with `gradle wrapper`).
```bash
gradle testDebugUnitTest assembleDebug assembleRelease
# outputs: app/build/outputs/apk/debug/ and app/build/outputs/apk/release/
```

## Features
Dashboard · Bazar (items, qty/unit/price, receipts, duty calendar with rotation & manual reassignment, completed/pending/skipped) · Expenses (9 categories, equal / custom / fixed-share split) · Members (deposits, payments, refunds, statements) · Notes (pinned, searchable, linked to member/expense/month) · Floating calculator · PDF reports (monthly, member, daily bazar, category, due/advance) with Share / native Print · Month closing/reopen with audit log · ZIP backup/restore + optional daily local auto-backup · PIN lock with recovery code.

## Accounting rules
* Money = `Long` poisha. Never floating point. Splits use the largest-remainder method so shares always sum exactly to the amount.
* Deposits/payments/refunds are **not** expenses. Itemised bazar entries are counted once (category "Bazar"); an expense entered with category "Bazar" is a separate lump sum, also counted once.
* Member balance = carry + deposits + payments − refunds + paid-from-own-pocket (expense payer / bazar buyer) − expense share − bazar share. Positive = advance, negative = due.
* Expense shares are stored per expense (`expense_split`). Bazar total is split equally among that month's participants (joined by month end and not deactivated before it).
* Month closing needs all earlier months with data closed; stores closing balances; closed months are read-only; only the latest closed month can be reopened (PIN + reason, audited).

## Database (Room, version 1, schema exported to `app/schemas`)
| Table | Key columns / relations |
|---|---|
| mess | id=1, name, address, managerName |
| member | id, name, phone, joinDay, inactiveFrom, shareWeight |
| expense | id, month, day, category, amount, payerId→member (SET NULL), splitMode |
| expense_split | expenseId→expense (CASCADE), memberId→member, amount, shares |
| deposit | month, day, memberId→member, amount |
| payment | month, day, memberId→member, amount, kind (PAYMENT/REFUND) |
| bazar_entry | month, day, purchasedById→member (SET NULL), receipt |
| bazar_item | entryId→bazar_entry (CASCADE), name, qtyMilli, unit, unitPrice, total |
| bazar_duty | month, day (unique), memberId→member, status |
| note | title, body, pinned, day, memberId/expenseId (SET NULL), month |
| monthly_cycle | month (PK), status OPEN/CLOSED, snapshotJson, reopenCount |
| audit_log | ts, action, entity, entityId, detail, month |
| app_settings | key/value (PIN hash+salt, recovery hash) |

Migrations: add `Migration` objects to `AppDatabase.MIGRATIONS`; destructive fallback is never used. Backups carry `formatVersion` (currently 1), a SHA-256 checksum and are fully validated (versions, references, split sums) before the DB is touched.

## Notes / limitations
* PDFs use the device's system fonts (Bangla renders where the device has a Bengali font; no font file is bundled).
* Minify is disabled to keep the build robust.
* Calendar-month cycles; no meal-count based billing.

## Tests
`gradle testDebugUnitTest` — accounting, month closing, duty rotation, backup codec round-trip/validation, calculator. See `TEST_REPORT.md`.
