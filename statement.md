# Problem Statement

## The Problem

Everyday life is full of deadlines that are easy to forget: a
subscription silently auto-renews and charges you, an insurance
policy lapses without you noticing, a warranty window closes right
before an appliance breaks, a vehicle's insurance/PUC certificate
expires, a routine health checkup gets pushed back for years, a tax
or compliance filing is missed, groceries spoil unnoticed in the
back of the fridge, and a plant dies because nobody remembered to
water it on schedule.

Each of these is currently handled — if at all — by a *different*
app, a sticky note, or simply human memory. There is no single,
free, offline tool that treats "don't forget this by this date" as
one unified problem across every domain of a person's life.

## Scope of the Project

RenewalVault is a command-line Java application that consolidates
deadline and renewal tracking for **every category of recurring or
one-time obligation** a person deals with — not just one narrow use
case. It is scoped as a fully offline, dependency-free, single-user
desktop/terminal tool, deliberately avoiding any GUI framework or
external service dependency so that it is portable, auditable, and
runnable purely from the command line.

The scope explicitly excludes: multi-user accounts, cloud sync,
push/SMS/email notifications, and integration with third-party
calendars or banking APIs — the goal is a self-contained core engine
that solves the underlying tracking and prioritization problem well,
which could later be extended with those integrations. Local AES-256
encryption of the vault and `.ics` calendar file export are included,
since both stay entirely local/offline and don't require talking to
any external service.

## Target Users

- Individuals managing personal subscriptions, insurance, and
  warranties
- Vehicle owners tracking servicing and compliance deadlines
- Health-conscious individuals who want a reminder system for
  routine checkups
- Households wanting to reduce food waste by tracking grocery
  expiry dates
- Freelancers and small-business owners tracking tax/compliance
  filing deadlines
- Pet owners and plant enthusiasts tracking recurring care tasks
- Essentially: anyone who has ever forgotten a deadline that cost
  them money, health, or a broken appliance

## High-Level Features

1. **Unified item tracking** across 11 life-domain categories in a
   single data model, rather than fragmented single-purpose apps
2. **Weighted risk-ranked dashboard** powered by a priority queue, so
   the most consequential items always surface first — combining
   urgency, category stakes (a lapsed insurance policy outranks a
   subscription due a day sooner), and estimated cost, not just raw
   date order
3. **Flexible recurrence engine** supporting one-time, weekly,
   monthly, yearly, and custom-interval deadlines, plus per-item tags,
   custom alert windows, and snooze
4. **Renewal workflow** that automatically rolls a recurring item's
   due date forward and records the event
5. **AES-256 encrypted, tamper-evident audit trail**: every save is
   encrypted behind a master password, and every alert/renewal/edit is
   recorded in a SHA-256 hash-chained log so the app can prove whether
   its own history has been altered outside normal use — the same
   chain-of-custody principle used in digital forensics
6. **Search, filter, and multi-mode sort** across name, tags, category,
   overdue status, and due-date window, plus single-step undo
7. **Analytics and interoperability**: ASCII spend/count charts with
   30/90/365-day cost projections, CSV backup/restore, and `.ics`
   calendar export so any calendar app can pick up the same deadlines
8. **Category overview** giving a single-screen summary of everything
   being tracked across every domain of the user's life
9. **Local, dependency-free, encrypted persistence** so the tool works
   fully offline with no external database, internet connection, or
   external cryptography library required (only `javax.crypto`, part
   of every standard JDK)
