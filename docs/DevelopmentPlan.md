# TzAlarmClock — Staged Development Plan

This plan sequences the work in `docs/Requirements.md` into buildable,
independently testable stages. Each stage lists its goal, the requirements it
covers, the technical approach, and an exit criteria checklist. Assumptions
made to resolve ambiguity in the spec are called out inline (⚠) and
summarized in [Assumptions](#assumptions-log) at the end.

Phase 2 items from the spec are intentionally excluded from staging below —
see [Phase 2 backlog](#phase-2-backlog).

## Guiding technical decisions

These aren't stated in the spec, so they're fixed here as a baseline the
stages build on. ⚠ = assumption.

- ⚠ **Language/UI**: Kotlin, Jetpack Compose, single-Activity architecture.
  Nothing in the spec mandates this, but it's the current standard for a
  new API 31+ app and best supports the responsive-layout TODOs already
  noted in `Checklist.md`.
- ⚠ **Persistence**: Room (SQLite) for alarms; Jetpack DataStore (Preferences)
  for app settings. Both are local-only, matching the "no internet
  dependency" non-functional requirement.
- ⚠ **Scheduling primitive**: `AlarmManager.setAlarmClock()` (not
  `setExactAndAllowWhileIdle`) — it's the only API that reliably fires
  during Doze/battery optimization *and* surfaces to the user as a visible
  upcoming alarm, which supports the "battery-efficient but still fires"
  and "works when phone is on silent/DND" requirements.
- ⚠ **Ringing UI delivery**: Full-screen intent notification on a
  high-importance channel + a foreground `Service` that owns playback while
  the activity is shown. This is the standard mechanism for over-lock-screen
  alarm UI on API 31+ and for bypassing DND when the channel is configured
  with `AudioAttributes.USAGE_ALARM`.
- ⚠ **Dev process**: Work on each phase will be committed to a branch from main
  called "phaseN" where N is the number of the phase.
- Root package: `imb.tzalarmclock` (per spec).

---

## Stage 0 — Project scaffolding

**Goal:** empty app that builds, installs, and has the architectural
skeleton in place, so every later stage is additive.

- Android project targeting API 31, package `imb.tzalarmclock`.
- Module structure: `data` (Room entities/DAO, DataStore), `domain`
  (scheduling/recurrence logic, pure Kotlin so it's unit-testable without
  Android), `ui` (Compose screens), `alarm` (AlarmManager integration,
  receivers, ringing service).
- Navigation shell with placeholder screens for the four pages (Summary,
  Details, Ringing, Settings).
- CI-friendly test setup (JVM unit tests for `domain`, instrumented tests
  reserved for `alarm`/`data`).

**Exit criteria:** app installs on an API 31 emulator; navigating between
the four placeholder screens works; `domain` module has no Android
dependency.

---

## Stage 1 — Alarm data model & persistence

**Goal:** the `Alarm` entity and settings schema exist and are durable
across process death and restarts, independent of scheduling or UI.

Covers: *Alarm Definition*, *App Settings* (storage half only).

- `Alarm` entity: id, name, time (wall-clock, no date), optional IANA time
  zone id, schedule type (`NEXT_OCCURRENCE | ONE_TIME_DATE | WEEKLY |
  MONTHLY`), schedule payload (none, or date, or set of weekdays, or set of
  days-of-month), enabled flag, ringtone URI (nullable → falls back to
  setting), vibration flag (nullable → falls back to setting).
  - ⚠ *Recurrence vs fixed date are mutually exclusive* per the spec's "or"
    — modeled as a single `scheduleType` discriminator, not independent
    booleans.
  - ⚠ *`NEXT_OCCURRENCE` is a fourth schedule type*, not in the original
    three: the spec's baseline alarm has neither a date nor a recurrence
    ("the only temporal field required to define an alarm is time"). Giving
    it its own type avoids storing a synthesized date that would go stale
    the moment the device changes zone.
- Settings schema: home time zone, snooze period, max snooze count,
  default ringtone, alarm volume, escalation on/off, default vibration,
  12/24-hour format. Stored in DataStore, loaded at app start, written on
  settings-page close per spec.
  - ⚠ *Fresh-install defaults* aren't specified: 10-minute snooze, 3
    snoozes, full alarm volume, no escalation, vibration on, 24-hour
    times, and home zone / default ringtone left unset so they follow the
    device zone and the system alarm sound respectively.
  - ⚠ *Unknown zone ids degrade rather than fail*: an alarm or home-zone
    setting naming a zone the platform's tzdb no longer has reads back as
    "no zone" (floating / follow-device) instead of failing the read,
    since one stale id must not make the alarm list unreadable.
- Repository layer wrapping Room + DataStore with Flow-based reads so UI
  stages can observe changes reactively.

**Exit criteria:** CRUD operations on alarms and settings round-trip
correctly in instrumented tests; data survives app restart.

---

## Stage 2 — Recurrence & next-occurrence engine

**Goal:** pure, unit-testable logic that answers "when does this alarm next
ring?" — the hardest correctness surface in the app, so it's isolated and
tested before any UI or OS integration depends on it.

Covers: *Alarm Definition* (time-zone and recurrence semantics), the fuzzy
countdown formatting used by the Summary page.

- **Floating (no time zone) alarms**: next occurrence computed in the
  device's *current* local time zone at evaluation time. Re-evaluated (not
  cached) whenever the device zone changes or the app queries it, so a
  device TZ change automatically updates the answer — this is the core
  value proposition of the app.
- **Fixed-zone alarms**: next occurrence computed as a fixed instant in the
  alarm's own zone, converted to device-local for display. Unaffected by
  device TZ changes.
- **DST edge cases**:
  - ⚠ *Spring-forward gap* (wall-clock time doesn't exist that day):
    skip that day's occurrence entirely and take the next valid one, rather
    than firing at a shifted time. This matches the spec's "occurrence is
    skipped" language.
  - ⚠ *Fall-back overlap* (wall-clock time occurs twice): fire on the
    first occurrence (standard offset resolution used by
    `java.time.ZoneId`/`LocalDateTime.atZone` default).
- **Recurrence resolution**: weekly (set of `DayOfWeek`), monthly (set of
  day-of-month, e.g. {1, 15}).
  - ⚠ *Monthly day-of-month overflow* (e.g., 31st in a 30-day month): skip
    that month for that day rather than clamping to month-end, since the
    spec doesn't define clamping behavior and silent date-shifting would be
    surprising for an alarm app.
- **Fuzzy countdown formatter**: implements the exact spec algorithm —
  thresholds at ≥1d / ≥1h / <1h, half-to-even (bankers) rounding to integer
  N (and M for the day case), M omitted when zero, locale-independent unit
  labels "d"/"h"/"min".
- **Summary grouping**: Today / Tomorrow / This Week / Later, ascending
  time-to-ring within each group.
  - ⚠ *Group boundaries*: "Today" and "Tomorrow" are calendar-day matches
    in the device's local zone; "This Week" is the remainder of the
    current calendar week (device locale's first day of week) excluding
    today/tomorrow; "Later" is everything beyond that. Groups are
    mutually exclusive as written.
  - ⚠ *Disabled alarms*: still appear in their group (name + local time
    only, per the spec's conditional display), but since disabled alarms
    have no defined "next occurrence," they're placed at the end of
    whichever group they'd otherwise fall into by time-of-day only, or in
    a trailing "Disabled" bucket — **flagged for product decision**, not
    fully specified. Placeholder: append disabled alarms to "Later",
    ordered among themselves by time of day.
  - ⚠ *Enabled alarms with no future occurrence* (a one-time dated alarm
    whose date has passed, before Stage 6 gets a chance to disable it)
    have no time-to-ring either, so they are treated exactly like disabled
    ones and appended to "Later". Same placeholder, same open product
    question.

**Exit criteria:** unit test suite covering the formatter's rounding table,
DST gap/overlap dates, weekly/monthly recurrence math, and TZ-change
re-evaluation, all green with no Android dependency.

---

## Stage 3 — OS scheduling integration

**Goal:** the app can actually make the device ring at the right instant,
survive restarts, and react to time-zone changes — the non-functional
requirements that make or break an alarm app.

Covers: *AlarmManager* usage, *TZ change reactivity*, *survives restarts /
battery optimization / OS updates*, *no internet dependency*.

- Wrapper service that: reads the domain layer's "next occurrence" for
  every enabled alarm, schedules the single nearest instant per alarm via
  `AlarmManager.setAlarmClock()`, and re-schedules the *following*
  occurrence once an alarm fires or is dismissed/snoozed.
- `BroadcastReceiver` for `ACTION_BOOT_COMPLETED` (re-arm all enabled
  alarms after reboot).
- `BroadcastReceiver` for `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED`
  (re-evaluate and re-arm all floating alarms — this is the app's whole
  reason to exist).
- Runtime handling for the API 31+ `SCHEDULE_EXACT_ALARM` permission: check
  on app start, and per the existing README TODO, warn the user if it's
  not granted since alarms silently won't fire otherwise.
- Request exemption from battery optimization (`REQUEST_IGNORE_BATTERY_
  OPTIMIZATIONS`) with user-facing rationale, since Doze can otherwise
  delay non-`setAlarmClock` alarms.

**Exit criteria:** an alarm set for +2 minutes rings on a real device after
(a) a reboot, (b) force-stopping and relaunching the app, (c) changing the
device time zone mid-countdown, (d) enabling battery optimization for the
app.

---

## Stage 4 — Alarms Summary page (real data)

**Goal:** replace the Stage 0 placeholder with the full summary experience
driven by Stages 1–3.

Covers: *Alarms Summary Page* in full.

- List grouped into Today/Tomorrow/This Week/Later using Stage 2 logic,
  live-updating (Flow-backed) as time passes and as alarms change.
- Per-alarm row: local time, name, and (if enabled) next-ring date +
  fuzzy countdown.
- Enable/disable control per alarm (toggle), wired to reschedule/cancel via
  Stage 3.
- ⚠ *Refresh on disabled→enabled transition*: spec explicitly calls this
  out, implying the naive implementation might miss it — implemented via
  the same reactive Flow so no special-case code is needed, but called out
  as an explicit test case given the README TODO ("fix update of alarm
  list on change alarm enabled switch") already flags this as historically
  buggy.
- Navigation to Details page (new/edit) and tap targets sized for the
  "responsive display" TODO already noted in README.

**Exit criteria:** toggling an alarm's enabled state updates its group
membership and countdown live, without navigating away; countdown values
spot-checked against the Stage 2 rounding table.

---

## Stage 5 — Alarm Details page

**Goal:** full create/edit/delete flow for a single alarm.

Covers: *Alarm Details Page* in full.

- Form fields: name, time picker, optional time-zone picker,
  recurrence-vs-fixed-date selector, ringtone picker, vibration toggle.
- The spec's read-only "alarm time in local time zone" field (shown when
  alarm TZ ≠ device TZ) is **cut from this page** per product decision —
  the Details page shows only the alarm-zone time as entered. The
  computed local-time conversion remains available where it's actually
  useful for at-a-glance scanning: the Summary page (Stage 4), which
  already displays "alarm time in local time zone" for every alarm
  regardless of whether it has an assigned zone.
- Delete button with confirmation (destructive, so a confirm step is
  warranted even though the spec doesn't say so explicitly — deleting an
  alarm is hard to undo).
- ⚠ *Back-button save semantics*: spec says back-button saves changes
  (no explicit Cancel action is specified). Implemented as autosave-on-
  navigate-away for both system back and any in-app back affordance;
  no separate "discard changes" path exists since the spec doesn't
  request one.
- Validation: at minimum a time must be set (spec's only required field);
  recurrence pattern must have ≥1 day selected if recurrence is chosen.

**Exit criteria:** creating, editing, and deleting an alarm from this page
correctly reflects in the Summary page and in Stage 3's scheduled alarms.

---

## Stage 6 — Alarm Ringing page

**Goal:** the highest-stakes screen in the app — must be legible,
low-light-friendly, and resistant to fat-fingering "stop" when the user
meant "snooze."

Covers: *Alarm Ringing Page*, *Alarm ring workflow*.

- Full-screen activity launched via full-screen-intent notification,
  showing over the lock screen or on top of other apps per spec.
- Displays current time, time zone, date, alarm name.
- Foreground service plays the alarm's ringtone (or default) at the
  configured alarm volume; vibrates if the alarm's vibration setting (or
  default) is on and the device supports it.
- ⚠ *Volume escalation*: interpreted as the service gradually increasing
  playback volume from a lower starting point up to the configured alarm
  volume over a fixed ramp window (spec doesn't give a duration —
  assumption: ~60–90s ramp, tunable constant, not user-configurable since
  no such setting exists in the spec's settings list).
- ⚠ *Accidental-stop mitigation*: Dismiss requires a deliberate
  multi-step gesture (e.g., slide-to-confirm or press-and-hold), while
  Snooze is a single low-friction tap — inverted from the usual pattern,
  intentionally, per the spec's explicit priority ("difficult to
  accidentally stop... when the intent is to pause").
- Snooze: pauses ringing, reschedules via Stage 3 for now + settings'
  snooze period, decrements remaining-snooze count.
- ⚠ *Max snooze reached*: Snooze control is hidden/disabled and only
  Dismiss is offered, per spec.
- Dismiss: stops ringing/service; if the alarm has no future occurrence
  (one-time, non-recurring, already fired), flips it to disabled per spec.
- Must render correctly with DND active and screen off (wake + turn on
  screen via the full-screen intent + `FLAG_KEEP_SCREEN_ON`/window flags).

**Exit criteria:** manual test on a real device with screen off and DND
enabled: alarm still displays, sounds, and vibrates as configured; snooze
cycles correctly down to zero remaining; dismiss on a one-time alarm
disables it in the Summary page.

---

## Stage 7 — App Settings page

**Goal:** global settings UI wired to Stage 1's DataStore-backed settings,
consumed everywhere Stages 4–6 need a default.

Covers: *App Settings* in full.

- Form for all eight settings listed in the spec.
- Save-on-close semantics exactly as specified (no autosave-per-field,
  no explicit Save button implied by spec).
- Settings load on app open and are the fallback source for any
  alarm-level nullable field (ringtone, vibration) and drive Stage 6's
  volume/escalation/12h-24h/snooze behavior.

**Exit criteria:** changing a setting (e.g., snooze period, 12/24-hour
format) and reopening the app shows the persisted value and it's reflected
in the Summary page's time formatting and in a newly-triggered alarm's
behavior.

---

## Stage 8 — Reliability & non-functional hardening pass

**Goal:** dedicated pass on the four non-functional requirements, since
each cuts across every stage above and is easy to regress incrementally.

- DND/silent bypass: verify the notification channel + `AudioAttributes`
  configuration actually overrides DND on the target OS versions being
  tested; add regression test/checklist since this is called out as the
  #1 complaint the app exists to fix.
- Restart/kill/update survival: scripted manual test matrix — reboot,
  force-stop, OS update simulation (or documented as untestable without a
  real OTA), and OEM battery-optimization variance (this is a known
  Android fragmentation risk on non-Pixel devices, including the user's
  Motorola).
- Battery efficiency: confirm no polling/foreground-service is running
  except while an alarm is actively ringing or snoozed — all scheduling is
  event-driven (`AlarmManager` + broadcast receivers), no background
  timers.
- No network dependency: audit that no code path (ringtone lookup, TZ
  data) requires connectivity — time zone data ships with the OS/JDK, not
  fetched remotely.
- Startup permission check (already in README TODO): if
  `SCHEDULE_EXACT_ALARM` isn't granted, show a blocking warning on launch
  before the user can trust any alarm is actually armed.

**Exit criteria:** all four non-functional requirements have an explicit,
repeatable test or audit step checked off, not just "seems to work."

---

## Stage 9 — Polish & release readiness

**Goal:** ship-quality pass across everything built.

- Responsive layout fixes for alarm-properties view and time-chooser
  dialog (existing README TODOs — naturally resolved once Stages 4–7 are
  built with Compose's adaptive layout primitives from the start, but
  verified here across phone sizes/orientations).
- Empty states (no alarms yet), error states (permission denied,
  ringtone missing/uninstalled).
- Accessibility pass on the Ringing page specifically (large touch
  targets, high contrast, works with reduced vision/cognition as the
  spec explicitly calls out).
- App icon, naming/branding consistency with "TzAlarmClock" short name.
- Retire `Checklist.md`'s ad hoc TODO list once its items are folded into
  this plan (several already are: TZ broadcasts → Stage 3, alarm
  scheduling/recurrence → Stages 2–3, delete alarm → Stage 5, summary
  refresh bug → Stage 4, permission warning → Stages 3/8, responsive
  layout → Stage 9).

**Exit criteria:** fresh-install walkthrough of all four pages with no
placeholder data, on at least two screen sizes.

---

## Phase 2 backlog

Explicitly out of scope for staging above, per the spec's own phase split.
Noted here only so later re-planning starts from the same place:

- Per-day-of-week independent alarm times within one recurring alarm.
- Public-holiday skip logic, with region selectable independent of alarm
  time zone (needs an external holiday-data source — research spike, not
  yet a stage).
- Recurrence end-date (spec notes this could simplify one-time-alarm
  modeling by making it a special case — worth revisiting the Stage 1
  `scheduleType` design if/when this is picked up, to avoid rework).
- "Morning brightness" screen transition in the minute before an alarm.

---

## Assumptions log

Consolidated list of the ⚠ items above, for quick review/sign-off:

1. Kotlin + Jetpack Compose, single-Activity architecture.
2. Room for alarms, DataStore for settings.
3. `AlarmManager.setAlarmClock()` as the scheduling primitive.
4. Full-screen-intent notification + foreground service for the ringing UI.
5. Recurrence and fixed-date are mutually exclusive per alarm, with a
   fourth `NEXT_OCCURRENCE` schedule type for the spec's time-only alarm.
6. DST spring-forward gap → skip that occurrence; fall-back overlap →
   resolve to first instant.
7. Monthly recurrence on a day that doesn't exist in a given month is
   skipped for that month, not clamped.
8. Week grouping uses the device locale's calendar week, and "This Week"
   excludes Today/Tomorrow.
9. Disabled alarms' placement within the Summary groups is underspecified;
   placeholder behavior (append to "Later") is a stand-in for a product
   decision, not a final answer.
10. Back-button on Details page autosaves; there's no separate discard/
    cancel path since the spec doesn't request one.
11. Volume escalation ramp duration is a fixed, non-user-configurable
    constant (~60–90s), since the spec lists no setting for it.
12. Dismiss uses a deliberate multi-step gesture; Snooze is single-tap —
    intentionally inverted from convention per the spec's accidental-stop
    concern.
13. Delete-alarm action requires a confirmation step, though not spelled
    out in the spec, given its destructive/irreversible nature.
14. Product decision (not an ambiguity resolution): the Details page's
    read-only "alarm time in local time zone" field from the spec is
    dropped; that conversion is only shown on the Summary page.
15. Fresh-install settings defaults (10 min snooze, 3 snoozes, full
    volume, no escalation, vibration on, 24-hour times, home zone and
    default ringtone unset).
16. A stored time-zone id the platform no longer recognises degrades to
    "no zone" rather than failing the read.
