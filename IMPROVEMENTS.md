# Improvements Over the Original Version

This document explains what was added on top of the original 4-module
scope (`model` / `engine` / `db` / `cli`), and the reasoning behind each
change, in case it's useful for a viva or submission writeup.

## Why these particular improvements

The original project was a solid, working implementation of its stated
scope: an 11-category deadline tracker with a priority-queue urgency
engine and dependency-free file persistence. It did exactly what it set
out to do. The additions below aren't scope creep for its own sake —
each one targets a specific gap between "a working CLI tool" and "a
project that's genuinely hard to dismiss as a template exercise":

1. **The data was never actually protected.** A tool that stores
   insurance numbers, tax filings, and health checkup dates in a plain
   serialized file is asking to be judged on that. Real encryption (not
   just "we could add this later") closes that gap and doubles as a
   natural showcase of the Cyber Security specialization the project
   sits under.

2. **Sorting by date isn't the same as sorting by importance.** A
   subscription due tomorrow and a lapsed insurance policy due next week
   are not equally urgent. The original queue ordered by days-until-due
   only; a risk-weighted score fixes that and is a more interesting
   algorithmic story for a report than a bare `PriorityQueue`.

3. **A history log you can silently edit isn't much of an audit trail.**
   Hash-chaining the log entries (identical in principle to how Git
   commits or blockchains detect tampering) turns "we keep logs" into
   "we can *prove* the logs haven't been altered" — a direct, legible
   application of the Digital Forensics half of the specialization.

4. **The tool was an island.** It tracked reminders but had no way to
   get that information into anything else. CSV export/import and `.ics`
   calendar export fix that without breaking the "no external service
   dependency" rule from the original problem statement — both are
   plain local files, not API integrations.

5. **A CLI tool with no undo, search, or filtering doesn't scale past a
   demo.** Once there are 30+ items across 11 categories, "list
   everything" stops being useful. Search/filter/sort and undo are what
   make the tool usable for real ongoing use rather than a one-time demo.

## What was added, module by module

### `security/` (new package)
- `CryptoUtil.java` — AES-256-GCM encryption/decryption and PBKDF2 key
  derivation, using only `javax.crypto` (ships with every JDK, so the
  "just `javac` + `java`" build promise is untouched).
- `VaultAuth.java` — master password setup and unlock flow. Stores only
  a random salt and an HMAC-SHA256 verifier in `data/vault.meta` —
  never the password itself, and never anything from which the password
  could be recovered.

### `db/FileStore.java`
- Now encrypts before writing and decrypts after reading, transparently,
  using the key derived from the master password. A wrong password or a
  corrupted/tampered file fails loudly (`GeneralSecurityException`,
  reported to the user) instead of silently returning garbage.

### `model/TrackedItem.java`
- Added `riskScore()` — combines urgency, a per-category risk weight
  (tax/insurance/vehicle/health outrank groceries/subscriptions), and
  cost into one comparable number.
- Added tags, a per-item custom alert window, and snooze support.

### `model/ReminderLog.java`
- Every entry now stores a SHA-256 hash of its own content chained to
  the previous entry's hash (`previousHash` / `entryHash`), the same
  pattern used for tamper-evident chain-of-custody logs.

### `engine/ReminderEngine.java`
- Urgency queue now sorts by `riskScore()` instead of raw days.
- Added `snoozeItem()`, `logAdded()`, and `verifyAuditIntegrity()` (walks
  the hash chain and reports the exact entry where tampering occurred,
  if any).
- Added `costByCategory()` to feed the new analytics module.

### `engine/VaultService.java`
- Added `search()`, `filterByCategory()`, `filterOverdue()`,
  `filterDueWithin()`, and `sorted()` (5 sort modes).
- Added `delete()` (permanent removal, separate from the existing
  `deactivate()`), `importItems()` (for CSV restore), and a single-step
  `undoLast()` covering add/renew/deactivate/delete.

### `engine/AnalyticsEngine.java` (new — Module 5)
- ASCII bar charts of item count and estimated spend per category.
- `projectedSpend(days)` — walks each recurring item's schedule forward
  and estimates total cost over the next 30/90/365 days.

### `engine/ExportEngine.java` (new — Module 6)
- CSV export/import (hand-rolled, quote-aware CSV parsing — no external
  library) for backup, bulk editing, and migration.
- `.ics` (iCalendar) export with `RRULE` recurrence for weekly/monthly/
  yearly items, importable into any standard calendar app.

### `cli/Main.java` and `cli/ConsoleColors.java`
- Added the login/vault-setup flow.
- Expanded the menu from 8 to 16 options to expose every new capability
  (snooze, delete, undo, search/filter/sort, analytics, export/import,
  integrity check).
- Added ANSI color-coding to the dashboard and item listings so urgency
  is visible at a glance.

## What was deliberately left out of scope (same as the original)

Per the original problem statement, this is still a single-user,
fully offline, dependency-free terminal tool. No GUI, no cloud sync, no
push/SMS/email notifications, and no third-party calendar/banking API
calls were added — the `.ics` export is a local file the user chooses to
import wherever they like, which keeps the "no external service
dependency" boundary intact while still solving the "how do I actually
get reminded" problem.

## Round 2: closing the remaining gaps

After the first pass, a few things were still missing for the tool to
feel finished rather than "a demo with encryption bolted on":

- **No way to change your mind about a password.** A vault you can
  never re-key isn't really usable long-term. `VaultAuth` already had
  everything needed to generate a new salt/verifier; the missing piece
  was re-encrypting the *existing* data files in place rather than
  starting over. `FileStore.reencryptFile()` does this at the byte
  level (decrypt with the old key, encrypt with the new one) without
  ever touching the deserialized objects, so it works identically for
  `items.dat`, `logs.dat`, or anything added later.
- **No way to edit a mistake.** You could deactivate or delete an item,
  but not fix a wrong due date or typo without losing its ID and
  history. `VaultService.editItem()` closes that gap and plugs into
  the same undo mechanism as everything else.
- **CSV wasn't the whole story for backups.** CSV is intentionally
  plaintext (so it's spreadsheet-editable), which means it's the wrong
  tool for "back up my actual encrypted vault." `BackupEngine` adds a
  single-file `.rvbackup` (built with `java.util.zip`, still part of
  the JDK) that bundles the real encrypted files byte-for-byte and
  restores them onto a fresh machine, still gated by the original
  password.
- **A history log with no feedback loop.** The hash chain proved the
  log couldn't be tampered with, but it wasn't doing anything for the
  user day to day. On-time renewal streaks (`ReminderEngine.
  currentOnTimeStreak()`) reuse the same log data to answer a simple,
  motivating question: "am I actually staying on top of this?" — with
  zero additional stored state, since it's derived entirely from
  existing RENEWED log entries.
- **No way to catch an accidental duplicate.** A quick case-insensitive
  name check (`VaultService.findPossibleDuplicate()`) warns before
  adding "Netflix" for the third time.
- **"Manually tested" doesn't hold up to scrutiny.** `TestRunner`
  (`renewalvault.test`) is a from-scratch, zero-dependency assertion
  framework — no JUnit — that runs 12 automated regression tests
  covering crypto, tamper detection, recurrence math, CSV round-trips,
  undo, backup/restore, and password rotation. It's a more convincing
  claim of correctness than a testing paragraph in a README, and two
  real bugs (an inverted early/late sign in the streak calculation, and
  a key-derivation mismatch in an early draft of the backup test) were
  caught and fixed specifically because these tests existed.
