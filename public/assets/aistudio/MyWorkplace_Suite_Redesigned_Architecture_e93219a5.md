# MyWorkplace Suite: Redesigned Architecture

Nov 21, 2026 · @Yogendra Limbu

The v1 split stays: HR owns employee data, MyWorkplace is the one daily app. Version 2 changes how the app behaves on bad networks, who may do what, and what can block a technician at a customer's door.

## Design principles

| Area | v1 | v2 | Why |
| --- | --- | --- | --- |
| Connectivity | Cached jobs | Offline-first: local write queue for every action | Fiber crews work in poor coverage |
| Job closure | One ACID transaction across Ticketing, Inventory, Accounting | Operational close is atomic; accounting posts later via outbox | A closed period must never block field work |
| Access control | Three fixed role layers | Capabilities per user, with self-approval blocked | People straddle roles (lead who also climbs poles) |
| Cross-module links | Direct reads, e.g. dispatch reads HR leave | Events plus read-only projections | Modules stay independent and testable |
| Sensitive data | Same app session as everything else | Re-authentication, no PDF caching, view audit | Field phones get shared and lost |
| Attendance | Punch accepted as sent | Punch accepted, scored, flagged for review | GPS and Wi-Fi proofs are easy to spoof |
| Platform | Not covered | Shared services: sync, events, audit, notifications | Every module needs them; build once |

## Suite architecture

&#91;embedded content: suite architecture · app, API, 4 modules, platform services\]

The app talks only to the API. Modules do not call each other directly; they publish events and write audit entries through the platform layer, which is what lets Accounting fall behind without slowing a technician.

## Permissions: capabilities, not three tiers

Each user holds a set of capabilities, and the app builds its home screen from that set. A role such as Field Technician or Branch Lead is only a named bundle of capabilities that HR assigns, so one person can hold two bundles.

| Capability | Unlocks | Scope |
| --- | --- | --- |
| `attendance.punch` | Punch in and out | Self |
| `ess.view` | Profile, leave, payslip, documents | Self |
| `claims.submit` | Expense and overtime claims | Self |
| `jobs.execute` | Work orders, job completion, route log | Assigned tickets |
| `stock.van` | Van stock, restock, peer transfer | Own van |
| `dispatch.assign` | Dispatch board, ticket assignment | Branch or region |
| `roster.manage` | Shift and standby roster | Branch or region |
| `approve.attendance_ot` | Late punches and overtime approval | Direct reports |
| `approve.expense` | Receipt verification before Accounting | Direct reports |
| `audit.stock` | Issued vs consumed serial reconciliation | Branch or region |

Three rules sit on top of the capability list:

- **No self-approval.** The approver on any request can never be the requester, even when they hold the capability. A manager's own overtime goes to their manager.
- **Delegation with an end date.** A manager going on leave names a delegate for a set period. The delegate's approvals are logged as delegated.
- **Scope is part of the grant.** `dispatch.assign` for Branch A does not reach Branch B. Scope comes from the HR reporting line and branch assignment, not from the app.

## MyWorkplace navigation

The home screen is assembled from the user's capabilities, with the same five base items for everyone and extra modules appearing only when granted. Nothing is hidden by job title; it is hidden by missing capability.

| Module | Needs capability | Contents |
| --- | --- | --- |
| Today | `attendance.punch` | One-tap punch, shift timer, duty status (On Duty, On Field Job, Break, Off Duty), today's schedule, sync status badge |
| My HR | `ess.view` | Profile, Leave, Payslip, Documents. Opens HR views; payslip, bank, PAN and SSF screens ask for biometric or PIN first |
| Claims and Overtime | `claims.submit` | Fuel, travel, meal, local purchase receipts tagged to a ticket or branch; overtime and call-out claims |
| Notices | none | Announcements, safety circulars, staff directory. Outage alerts also arrive by SMS fallback |
| My Work Orders | `jobs.execute` | Prioritized queue with map pins and SLA countdowns; completion wizard for optical power, OTDR and splice photos, customer signature, consumed materials |
| My Van | `stock.van` | Serial and consumable balance, restock request, peer-to-peer transfer |
| Route and Pole Log | `jobs.execute` | Daily route and ODB/FDB or pole maintenance entries |
| Dispatch Board | `dispatch.assign` | Crew map and timeline, skill-matched assignment, SLA breach alerts, crew blocked by approved leave |
| Roster | `roster.manage` | Weekly shifts and 24/7 standby planner; leave shown read-only from HR |
| Approvals | `approve.*` | Late punches, overtime, expense verification, with a delegation switch |
| Stock Audit | `audit.stock` | Warehouse issued vs ticket consumed, per technician |

Two additions over v1. A **sync status badge** on Today shows queued, uploading and failed items so a technician knows what has not yet reached the server. A **Needs attention** inbox collects rejected syncs, flagged punches and expired delegations in one place.

## Offline-first sync

Punches, job completions, serial scans, claims and photos all go into a local write queue first, and the server decides afterwards. The device is the source of truth for what happened at the site; the server is the source of truth for assignments, rosters and stock.

&#91;embedded content: job completion states · device queue to server result\]

A rejected item keeps every photo, reading and signature, so nothing captured at the customer's premises is lost. It lands in the technician's Needs attention inbox and reaches a coordinator when it cannot be fixed on the phone.

| Conflict while offline | Server rule | Result |
| --- | --- | --- |
| Ticket was reassigned, not yet closed by anyone | Accept the close and flag it for coordinator review | Accepted, flagged |
| Ticket was already closed by another technician | Reject with `TICKET_CLOSED`, keep the captured data | Needs attention |
| Scanned serial is not in this van's stock | Reject with `SERIAL_NOT_IN_VAN`; coordinator can correct the serial and replay | Needs attention |
| Cable or consumable use exceeds van balance | Accept; van balance goes negative and is flagged for stock audit | Accepted, flagged |
| Same upload arrives twice | Return the stored result for that idempotency key | Accepted, no duplicate |
| Punch arrives later than the allowed window | Accept with the device time and hold for supervisor review | Held for review |

## Cross-module flows

The rule is simple: field truth commits immediately, money follows. Anything a technician does at a site must succeed or fail on operational grounds only, and Accounting catches up through a retrying outbox.

| Trigger | Immediate (atomic) | Asynchronous (event or outbox) |
| --- | --- | --- |
| Punch in | Write to `hr.attendance_logs` with proof, device ID, server time and risk score | Dispatch board marks the person Available |
| Leave approved | HR deducts balance | `leave.approved` event updates a read-only availability projection used by roster and dispatch |
| FTTH install closed | Ticket closes, drop cable deducted, ONT serial moves TECH\_VAN to CUSTOMER, all in one operational transaction | Outbox posts Dr Installation COGS, Cr Inventory; failures go to an exceptions queue for Accounting |
| Expense approved | Claim status set to Approved | Outbox creates Dr Field Operations Expense, Cr Employee Payable; reimbursement queued for payout or payslip |
| Payroll run | HR locks attendance and approved overtime | Salary, SSF and TDS journal posted; payslip PDFs published to My Payslip |

Three behaviors matter most:

- **Accounting never blocks the field.** A closed period, a missing account mapping or a posting error leaves the job closed and raises an exception for finance.
- **Leave is advisory for dispatch, not absolute.** Half-day and emergency leave show as a warning, and a coordinator with `dispatch.assign` may override with a reason that is logged.
- **Every outbox message is idempotent.** Replaying a journal posting never produces a second voucher.

## Security and attendance integrity

Treat every field phone as something that will eventually be shared, lost or tampered with, and design so that a lost phone exposes little.

**Sensitive data (payslip, bank, PAN, SSF, documents)**

- Biometric or PIN re-authentication before opening, and again after a short idle period.
- Payslip PDFs are never written to shared storage or cached after viewing; they stream and are discarded.
- Every view of a sensitive screen is written to an audit log with user, device and time.
- Remote session revocation and device wipe of app data, triggered by HR or IT.
- Each account is bound to a registered device; a new device needs approval.

**Attendance punches**

- The server stamps its own time and compares it with device time; a large gap raises a flag.
- Mock-location and rooted-device signals, Wi-Fi BSSID match and GPS accuracy feed a risk score.
- A suspicious punch is accepted and marked for review, never silently rejected, because a wrongly rejected punch costs a technician a day of pay.
- A missed punch-out closes at shift end as Incomplete and goes to the Approvals inbox, where the supervisor confirms the real time.
- Offline punches carry the device timestamp and are held for review if they arrive more than a set window late.

**Customer signatures and photos**

Compress on the device, upload in the background, and keep originals under a defined retention period set with legal and operations. The retention length is an open decision (see roadmap).

## Offline login and GPS limits

Unlocking the app and proving location are separate checks. The app opens offline with a device-bound token that expires, and a missing GPS fix flags an action instead of blocking it.

&#91;embedded content: offline access modes · 3 states, transitions\]

GPS works without internet, so a phone with no data can still get a fix. The two failures are no network, which is common in the field, and no fix, which happens indoors or in tunnels. Payslip, bank, PAN, SSF and documents stay blocked offline, so a lost phone exposes nothing sensitive.

| Control | Rule | Proposed start |
| --- | --- | --- |
| Offline token window | App locks this long after the last online sign-in | 24 hours |
| Unlock | Biometric or PIN on every open; token bound to the registered device | Every open |
| Queue cap | App asks for a sync once this many actions are waiting | 20 items |
| GPS accuracy | A fix counts as verified only below this error | 50 m |
| No GPS fix | Retry briefly, then save last known location with its age, Wi-Fi BSSID and mock-location signal; mark No location and hold for supervisor review | Held for review |
| Clock check | Compare the phone's monotonic clock with the last trusted server time; a mismatch flags the punch | Server rechecks on sync |
| Revocation | Takes effect at the next sync; queued items from that device are held for review | At next sync |

The window, accuracy and queue values are starting points. Begin strict and loosen them after a field trial, and list them with the other open decisions before build.

## Mobile API blueprint

The v1 endpoint set holds up; v2 adds a sync contract, a capability-aware bootstrap, and a machine-readable error format. All paths sit under `/api/v1/`.

| Group | Endpoint | Purpose |
| --- | --- | --- |
| Init | `GET workplace/bootstrap` | Small payload: capabilities, punch state, shift, unread count, pending approvals, van location, server time |
| Sync | `POST sync/push` | Batch of queued actions, each with `client_id`, `idempotency_key`, `device_time`, `base_version`; returns per-item result |
| Sync | `GET sync/pull?updated_since=` | Changed jobs, stock, roster, notices since a cursor; supports ETag |
| Devices | `POST devices/register`, `POST devices/push-token` | Device binding and push registration |
| Attendance | `POST workplace/attendance/punch` | Punch with proof; returns accepted, flagged or held |
| Claims | `POST workplace/claims` | Expense and overtime with receipt upload |
| HR | `GET hr/me/profile` | Personal, bank, PAN, SSF, reporting line |
| HR | `GET/POST hr/me/leaves` | Balances, history, requests |
| HR | `GET hr/me/payslips`, `GET hr/me/payslips/:period/pdf` | List and streamed PDF, re-auth required |
| HR | `GET hr/me/documents` | Letters, contracts, credentials |
| Field | `GET workplace/jobs` | Assigned tickets |
| Field | `POST workplace/jobs/:id/complete` | Operational close with serials, readings, photos; accounting is queued |
| Field | `GET/POST workplace/stock`, `POST workplace/stock/request` | Van balance, restock, peer transfer |
| Dispatch | `GET workplace/dispatch/board`, `POST workplace/dispatch/assign` | Crew availability and assignment, with override reason field |
| Approvals | `GET/POST workplace/approvals`, `POST workplace/delegations` | Inbox, decisions, delegation |

**Conventions**

- Cursor pagination on every list. Version in the path; additive changes only inside a version.
- Errors return `{ "code", "message", "retryable", "details" }` with codes such as `SERIAL_NOT_IN_VAN`, `TICKET_REASSIGNED`, `PERIOD_CLOSED`, so the app can act without parsing text.
- Photos upload separately by pre-signed URL; the job completion call carries only their references.
- Bootstrap stays under a few kilobytes and never includes lists.

## Delivery roadmap and open decisions

Build in four phases, but prototype the offline sync and job-close flow before phase 3 starts, since that is where a late surprise costs the most.

&#91;embedded content: delivery roadmap · 4 phases, 3 gates, 1 early spike\]

Six decisions are still open, and each one changes something specific in the build:

| Decision | What it changes |
| --- | --- |
| Do Ticketing, Inventory and Accounting share one database? | Whether the outbox runs in-process or over a message queue |
| Retention period for customer signatures and photos | Storage cost, upload size limits, legal exposure |
| Allowed lateness for offline punches before supervisor review | Attendance flag volume and payroll lock timing |
| Who may override leave when dispatching, and who reviews the log | Dispatch board rules and audit reporting |
| Nepali (BS) calendar and Nepali/English interface | Date handling across leave, payroll and rosters from day one |
| Overnight on-call and overtime calculation rules | Roster model and the payroll run |
