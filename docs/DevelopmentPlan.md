# TzAlarmClock — Staged Development Plan

This plan sequences the work in `docs/Requirements.md` into buildable,
independently testable stages. Each stage lists its goal, the requirements it
covers, the technical approach, and an exit criteria checklist. Assumptions
made to resolve ambiguity in the spec are called out inline (⚠) and
summarized in [Assumptions](#assumptions-log) at the end.

Phase 2 of the spec (multi-timer countdown functionality) is staged below as
Stages 13-19. Phase 3 items remain intentionally excluded from staging — see
[Phase 3 backlog](#phase-3-backlog). (This plan originally labelled that
backlog "Phase 2"; `Requirements.md` has since grown its own Phase 2 section
—timers — pushing the old Phase 2 content to Phase 3. The backlog's contents
are unchanged, only its name and the phase number below have caught up.)

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
- ⚠ **Timer module structure** (Stages 13-19): a new `timer` Gradle module
  mirrors `alarm`'s internal shape (`schedule`/`ringing`/`receiver`
  packages) rather than extending `alarm` itself. Timers share the app's
  scheduling *primitive* (`AlarmManager.setAlarmClock()`, same rationale as
  the alarms decision above) but have a materially different state machine
  — pause/resume, no recurrence, no time zone — and `alarm` is a
  domain-specific module name, not a generic "schedulable thing" module.
  Some duplication of the scheduler/receiver/PendingIntent-per-id shape is
  accepted as the cost of keeping both modules' names honest and their
  state machines uncoupled.

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
  - ⚠ *`USE_EXACT_ALARM` is the primary permission*, with
    `SCHEDULE_EXACT_ALARM` declared only up to API 32 where the former
    doesn't exist. `USE_EXACT_ALARM` is granted at install to apps whose
    core function is an alarm clock and cannot be revoked out from under a
    scheduled alarm, which is strictly more reliable — and reliability is
    what this whole stage is for. The startup check is kept regardless,
    since `canScheduleExactAlarms()` is the authority either way and the
    permission *is* revocable on API 31–32.
- Request exemption from battery optimization (`REQUEST_IGNORE_BATTERY_
  OPTIMIZATIONS`) with user-facing rationale, since Doze can otherwise
  delay non-`setAlarmClock` alarms.
  - ⚠ *Offered, not forced*: surfaced as an action on the startup warning
    rather than an unprompted system dialog on first launch, and worded as
    advisory — `setAlarmClock` fires through stock Doze regardless, so the
    real target is OEM power managers (the user's device is a Motorola).
- ⚠ *Interim ringing*: Stage 6 owns the ringing UI, but Stage 3's own exit
  criteria say an alarm must *ring*, so a fired alarm posts a
  high-importance notification on a channel carrying the default alarm
  sound and `USAGE_ALARM` attributes. Stage 6 replaces it with a
  full-screen intent plus a playback service; because channel settings are
  immutable once created, it will need a new channel id rather than an
  edit to this one.
- ⚠ *Non-recurring alarms are retired when they fire*, not when they are
  dismissed. Without this an alarm defined by time alone re-arms forever,
  since "the next occurrence of 07:00" is always tomorrow. Stage 6 moves
  the trigger to dismiss, once there is a ringing screen to dismiss from.

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

## Stage 8 — Refactoring & bug-fixing pass

**Goal:** pay down known issues and refactoring debt accumulated across
Stages 1–7 before Stage 9's reliability/hardening pass audits the app —
so that pass is checking a clean baseline rather than auditing around
already-known-broken behavior.

- **Known issue from Stage 6**: the Summary page's countdown/date display is
  snooze-*unaware* — `buildSummaryUiState` computes next-occurrence straight
  from the alarm's own schedule, with no knowledge of `SnoozeRegistry`
  (`alarm` module), unlike `AndroidAlarmScheduler`'s arming plan, which
  Stage 6 did make snooze-aware. Concretely: snooze a one-time alarm and the
  Summary page shows tomorrow's naive next-occurrence (e.g. "Tomorrow, 24h")
  while the OS is actually armed to ring in ~10 minutes (the snooze period).
  Found during Stage 6's manual emulator verification on 2026-08-01; fix
  deferred here rather than expanding Stage 6's scope. Fix shape: mirror the
  `armingPlan` fix — give `buildSummaryUiState` (or its caller) the same
  snoozed-until lookup so a snoozed alarm's displayed next-ring time matches
  what's actually armed.
- **Known issue from Stage 5**: the date selector on the Details page
  (`AlarmDatePickerDialog`, wrapping Material3's `DatePickerDialog`) doesn't
  display correctly in landscape — the calendar grid is clipped and its
  later rows overlap the Cancel/OK buttons, cutting part of the current
  month off entirely. Same root category as the time-picker landscape bug
  Stage 5 already fixed (a Material3 picker component fighting a dialog
  container that doesn't give it the space it needs in a short/landscape
  viewport), just not caught at the time since the time picker was the one
  reported. Confirmed via direct verification on 2026-08-01. Fix shape:
  likely analogous to the time-picker fix — stop constraining the dialog's
  width/height against the component's natural layout (e.g. drop
  `usePlatformDefaultWidth`/add a proper scrollable, height-capped
  container) so the calendar actually fits the landscape viewport instead
  of overflowing it.
- **New task: notify when an alarm is snoozed, with an in-notification
  Dismiss action.** Today `RingingService.snooze()` (Stage 6) just cancels
  the ringing notification and re-arms silently — there's no ongoing
  indication that an alarm is snoozed and pending, and no way to cancel a
  pending snooze without waiting for it to ring again. Add a notification
  (e.g. "Alarm snoozed until 07:10") posted when the snooze starts, replacing
  the ringing notification rather than stacking alongside it, carrying a
  `Dismiss` action button wired to the same `RingingService.dismissIntent`
  the Ringing screen's own hold-to-dismiss gesture uses — tapping it should
  disable a non-recurring alarm (or leave a recurring one armed) and clear
  the snooze exactly like dismissing from the Ringing screen, without
  needing to relaunch that screen. The notification is cancelled once the
  snooze resolves (the alarm fires again) or is dismissed directly from it.
- **New task: a Cancel button for a new alarm on the Details page.** Stage 5's
  back-button-always-saves semantics (per spec, for *editing*) also apply to
  *creating* a new alarm, so there is currently no way to back out of the "+"
  flow without it being saved anyway — the Delete button (which only shows
  for `!isNew`) doesn't help here either, since there's nothing to delete yet.
  Add a Cancel button in the same location the Delete button occupies when
  editing (bottom of the form), shown only when `isNew`, that discards the
  in-progress form and navigates back *without* saving — the one exception to
  the autosave-on-back rule, scoped specifically to a not-yet-saved alarm.
- **New task: `FuzzyCountdown`'s "0 min" reads `"< 1 min"` instead.** ⚠ A
  deliberate deviation from Stage 2's literal spec algorithm (which rounds to
  a bare `"N min"` with no zero special-case): rounding a sub-30-second
  remainder down to `"0 min"` reads as "no time left" / "should be ringing
  now" on the Summary page, when the alarm is in fact still up to ~30 seconds
  from firing. `"< 1 min"` isn't misleading either way it can arise — a
  genuine near-future countdown that rounds to zero, or the already-elapsed
  case the formatter also clamps to zero.

**Exit criteria:** both known issues above are resolved and verified (in
both portrait and landscape for the date/time pickers); snoozing an alarm
posts a notification with a working Dismiss action that matches the Ringing
screen's own dismiss behavior; creating a new alarm and tapping Cancel
returns to the Summary page without adding a row; an alarm due in under a
minute reads `"< 1 min"` rather than `"0 min"`; no regressions in the
existing test suite.

---

## Stage 9 — Reliability & non-functional hardening pass

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

## Stage 10 — R8 optimization & release build CI

**Goal:** turn on release-build code shrinking/obfuscation and make CI build
and test that variant, so R8 issues (missing keep rules, stripped reflection
targets, etc.) surface before Stage 12's polish pass rather than after.

- Enable R8 optimization for the `release` build type (flip `app/build.gradle
  .kts`'s `optimization.enable` from `false` to `true`), including
  minification/shrinking and adding whatever `proguard-rules.pro` keep rules
  Room, KSP-generated code, and Compose need to survive shrinking intact.
- Update `.github/workflows/build.yml` to build and unit-test the `release`
  variant instead of `debug` (`assembleRelease` in place of `assembleDebug`;
  `testReleaseUnitTest` in place of `testDebugUnitTest`, still paired with
  the explicit `:domain:test` since `domain` is a plain Kotlin/JVM module
  with no build-type variants).
  - ⚠ *Still no signing config*: CI continues to produce an unsigned release
    APK/AAR, same as today — this stage is about exercising R8, not standing
    up a signing/release pipeline, which is out of scope until the app is
    ready to ship.

**Exit criteria:** `./gradlew assembleRelease testReleaseUnitTest :domain:test`
succeeds locally and in CI with R8 enabled, with no runtime regressions from
shrinking (spot-check Room queries, Compose navigation, and alarm scheduling
still work on a release-variant install).

---

## Stage 11 — Details page buttons & Summary recurrence labels

**Goal:** three independent changes bundled into one stage because they were
requested together: (1) replace the Details page's autosave-on-back behavior
with an explicit button set, (2) show each recurring alarm's actual
recurrence pattern on the Summary page instead of leaving it implicit, and
(3) make the Summary page's proximity group headers (Today/Tomorrow/This
Week/Later) easier to spot against the alarm rows.

### Task 1 — Details page: explicit Add/Cancel/Delete buttons

⚠ **Deviates from the spec, not an ambiguity resolution.**
`docs/Requirements.md:70-71` states: "If the user chooses the Android back
button, then changes to the alarm settings will be saved." This task
deliberately replaces that behavior:

- **Creating a new alarm** (`isNew`): the bottom-of-form button row shows
  **Add** and **Cancel**. Add explicitly saves the new alarm — superseding
  both Stage 5's implicit autosave-on-back and Stage 8's Cancel-only cutout
  for new alarms. Cancel discards the in-progress form without creating
  anything. Edits stay in the ViewModel's local `DetailsUiState` only —
  nothing touches the repository until Add is tapped, same as today.
- **Editing an existing alarm**: the button row shows **Cancel** and
  **Delete**, replacing Stage 5's "Delete" + implicit autosave-on-back.
  There is no page-level Save button; edits also stay purely local (the
  repository is untouched while editing), same draft-in-memory model as
  creating a new alarm. Delete removes the alarm outright (keeping Stage 5's
  existing confirmation step).
- The Android system back button is remapped to behave exactly like Cancel
  in both cases — it no longer saves unconditionally.
- **Exit-confirmation dialogs**, shown only when the form is actually dirty
  relative to the snapshot taken when the page was opened (an unmodified
  form's Cancel/back navigates straight back, no dialog):
  - **New alarm**: a 2-way *"Discard changes?"* dialog — **Discard** leaves
    without creating anything; **Keep Editing** dismisses and stays. Add
    remains the only way to persist a new alarm, so the dialog doesn't need
    a save option of its own.
  - **Existing alarm**: since editing has no page-level Save button, the
    save action lives in the exit dialog itself — a 3-way *"Save changes
    before leaving?"* dialog: **Save** persists the current draft (`Alarm
    Repository.save`) and leaves; **Discard** leaves with the repository
    untouched (still holding the pre-edit alarm); **Keep Editing** dismisses
    and stays, changes still only in memory.
- Supersedes assumption #10 (see the amended entry in the Assumptions log)
  and folds Stage 8's `isNew`-only Cancel button into a single Cancel
  affordance shared by create and edit.

### Task 2 — Summary page: recurrence label

The spec's Summary page field list (`docs/Requirements.md:40-54`) has no
recurrence label — this is an addition, not a deviation. A recurring alarm's
row gains a label describing its pattern:

- **Weekly**: `"Weekly "` followed by the alarm's selected days, in calendar
  order starting Monday (not the device locale's first-day-of-week, so the
  label reads the same everywhere), comma-separated, using the Android SDK's
  short `DayOfWeek` display name (`TextStyle.SHORT`, e.g. "Mon", "Tue") rather
  than a single letter, e.g. an alarm on Monday/Wednesday/Friday reads
  `"Weekly Mon, Wed, Fri"`.
- **Monthly**: `"Monthly "` followed by the alarm's selected days-of-month,
  in ascending numeric order, comma-separated, e.g. an alarm on the 1st and
  15th reads `"Monthly 1, 15"`.
- One-time and next-occurrence (non-recurring) alarms get no recurrence
  label, same as today.
- ⚠ *Locale scope*: unlike the countdown labels' explicit "locale-independent"
  requirement (spec line 53), nothing says whether the recurrence label
  should localize. Treated as English-only for now, consistent with the
  literal examples given.

### Task 3 — Summary page: proximity-header background color

The spec's Summary page field list doesn't address header styling — this is
a legibility fix, not a deviation. The Today/Tomorrow/This Week/Later group
headers (`SectionHeader`) previously relied on tinted label text alone
(`colorScheme.primary`) with no fill, which read faintly against the alarm
rows and was easy to miss while scrolling a populated list.

Two background treatments were built and compared side by side, both
screenshotted from the running app on the same three-section dataset:

- **Header background only**: each header gets a full-width
  `primaryContainer` fill (text in `onPrimaryContainer`); alarm rows are
  untouched.
- **Per-group background, header + rows**: each of the four groups gets its
  own tint — `primary`/`secondary`/`tertiary` containers plus
  `surfaceVariant` for Later — carried through the header and lightened
  (blended 35% toward `colorScheme.surface`) for its alarm rows.

**Header background only** was chosen: it fixes the legibility problem the
per-group treatment also fixes, without the added visual noise of four
differently-tinted row blocks competing for attention. Both options draw
from existing Material theme roles rather than hardcoded colors, so either
would have adapted to dynamic color and dark theme automatically.

**Exit criteria:** creating a new alarm and tapping Add saves it and returns
to Summary; tapping Cancel (button or back) on a dirty new-alarm form shows
*"Discard changes?"*, and Discard leaves with no row added while Keep Editing
stays; editing an alarm, changing a field, and tapping Cancel (button or
back) shows *"Save changes before leaving?"* — Save persists the edit and
returns to Summary showing the new values, Discard returns to Summary with
the alarm unchanged, Keep Editing stays on the page with the edit still
present; editing without changing anything and pressing back/Cancel returns
immediately with no dialog; Delete continues to require its existing
confirmation step; a weekly alarm on Mon/Wed/Fri shows `"Weekly Mon, Wed,
Fri"` on the Summary page and a monthly alarm on the 1st/15th shows
`"Monthly 1, 15"`; non-recurring alarms show no recurrence label; each
proximity header (Today, Tomorrow, This Week, Later) renders with a
`primaryContainer` background behind its label, visually distinct from the
alarm rows below it, which keep their default background.

---

## Stage 12 — Polish & release readiness

**Goal:** ship-quality pass across everything built.

A pre-implementation audit found most of this stage's bullet list already
satisfied by earlier stages, so it's built as targeted gaps (Tasks 1–3)
rather than a from-scratch pass:

- **Responsive layout** — the time-chooser dialog was already fixed in
  Stage 5, the date-chooser dialog in Stage 8; the time-zone picker and
  ringtone picker were confirmed already correct in landscape during
  Stage 5. Details and Settings both wrap their form in a
  `verticalScroll` `Column`, and the weekday/month-day selectors use
  `FlowRow`, so narrow widths wrap rather than clip. Live-verified this
  stage on the emulator at portrait/landscape and at `wm size`-overridden
  360×640dp (the compact-phone reference width still common today) with
  no issues. ⚠ *One genuine finding, deliberately left unfixed*: at
  320×640dp — Android's absolute minimum supported width — the Schedule
  `SingleChoiceSegmentedButtonRow`'s "Monthly" segment wraps and the row
  overflows past the screen edge, because Material3's row sizes itself to
  `IntrinsicSize.Min` and a `SegmentedButton` label's minimum intrinsic
  width is its longest *unbreakable* word (confirmed via the Material3
  1.4.0 source — passing an explicit per-segment `Modifier.weight(1f)`
  changes nothing, since `SegmentedButton` already applies that weight
  internally regardless). Not fixed because it's structurally
  unreachable: this app's `minSdk` is 31 (Android 12), and no device that
  ships with Android 12+ has a 320dp-wide screen — that width class
  predates the OS versions this app can run on. No further changes
  needed on any width this app can actually run at.
- **Empty states** — Summary already shows "No alarms yet." for a
  zero-alarm list (`SummaryScreen`, since Stage 4). Already done.
- **Error states, permission denied** — `SchedulingWarnings`
  (Stages 3/9) already surfaces exact-alarm/notification/battery
  permission gaps with an inline fix action. Already done.
- **Error states, ringtone missing/uninstalled** — split into two cases.
  *Playback* (`RingingService`) already falls back to the system default
  alarm sound if the stored URI fails to resolve (Stage 6) — no change
  needed. *Display* (`RingtonePickerRow`, used by both Details and
  Settings) didn't distinguish "unset, using the system default" from "a
  ringtone was explicitly chosen but can no longer be resolved" — both
  showed the same ambiguous "Unknown". This gap is closed in **Task 1**.
- **App icon, naming/branding** — the launcher icon is already a custom
  design (not the Android Studio template), `app_name` is `TzAlarmClock`
  everywhere it's user-facing. Already done.
- **Accessibility pass on the Ringing page** — large touch targets
  (64dp buttons) and high contrast (fixed black/white regardless of
  system theme) were already in place from Stage 6; every icon-only
  control app-wide already carries a descriptive `contentDescription`
  (`EditableRow`'s Edit/Clear buttons, Settings/Add/Back). The gap:
  Dismiss's hold gesture reads raw press-duration off
  `MutableInteractionSource`, bypassing Compose's semantics tree, so an
  accessibility service's synthesized click (e.g. TalkBack's plain
  double-tap) lands on a no-op `onClick = {}` instead of dismissing —
  the only way to trigger it was a real-time hold. Closed in **Task 2**.
- **Retire `Checklist.md`** — of its items, TZ broadcasts (Stage 3), alarm
  scheduling/recurrence (Stages 2–3), permission warning (Stages 3/8), and
  responsive layout (this stage, above) were already struck through or
  covered. The remaining two open items — remove-timezone button (done via
  `EditableRow`'s `onClear` in Stage 5/7, just never marked) and the
  Summary-refresh-on-toggle bug (done via `observeAlarms()` in Stage 4) —
  are both already implemented, just not reflected in the file. Folded in
  and the file retired in **Task 3**.

### Task 1 — `RingtonePickerRow`: distinguish unresolvable from unset

`title` now falls back to `"Ringtone unavailable"` (not `"Unknown"`) when
this field's *own* `ringtoneUri` is non-null but fails to resolve — telling
the user their previously-picked ringtone is gone and prompting a re-pick,
versus `"System default"` for the rare case where nothing is set and even
the platform default URI won't resolve.

### Task 2 — Accessible alternative to Ringing's hold-to-dismiss gesture

`HoldToDismissButton` gains a `semantics { onLongClick(...) }` action
alongside the existing press-and-hold gesture, so an accessibility
service's long-click action (TalkBack's double-tap-and-hold or a Switch
Access long-press action) dismisses immediately rather than requiring a
timed real-world hold — the visual/touch interaction for sighted users is
unchanged.

### Task 3 — Retire `Checklist.md`

Its two still-open items are confirmed already implemented (see above),
the file is deleted, and this plan is the sole TODO source going forward.

**Exit criteria:** fresh-install walkthrough of all four pages with no
placeholder data, on at least two screen sizes.

---

## Phase 2 — Multi-timer countdown functionality

Stages 13-19 sequence `docs/Requirements.md`'s Phase 2 section (multi-timer
countdown functionality, independent of alarms) the same way Stages 0-12
sequenced Phase 1. They follow the same module boundaries and patterns
established there (see the timer-module guiding decision above) rather than
re-deriving an architecture from scratch.

## Stage 13 — Timer data model & persistence

**Goal:** the `Timer` entity exists and is durable across process death and
restarts, independent of scheduling or UI — mirrors Stage 1's role for
alarms.

Covers: *Timers Summary Page* (data half), *Adding a Timer* (data half),
*Persistence*.

- `Timer` entity: id, configured duration (a single seconds/millis value —
  the spec's three h/min/s entry fields are an entry-form UX, not a schema
  requirement), state (`STOPPED | RUNNING | PAUSED | EXPIRED`, see Stage 14
  for the state machine), remaining duration as of the last pause (used only
  in `PAUSED`), end instant (`Instant`, used only in `RUNNING` — the fixed
  wall-clock time this timer next fires, analogous to what
  `AndroidAlarmScheduler`/`ArmedAlarmRegistry` already track per alarm),
  creation timestamp (for list ordering, see Stage 14).
  - ⚠ *No name field*: unlike `Alarm`, the spec's Add Timer section only
    takes h/min/s — there's nowhere in the spec a user enters a timer name,
    and neither the Summary nor Ringing page descriptions show one.
  - ⚠ *End instant, not just remaining-at-start, is persisted*: required for
    the reboot/hard-stop survival requirement below — a relative countdown
    stored at start time wouldn't know how long the device was off, but a
    fixed instant needs no adjustment. Same reasoning `SnoozeRegistry`
    documents for why it's on disk rather than in memory.
- New Room entity + DAO added to the existing `data` module's
  `TzAlarmClockDatabase` (currently version 1, `AlarmEntity` only) — this is
  the project's first schema change past its initial version, so this stage
  also writes the project's first real Room `Migration` rather than just
  bumping the version number, since a destructive-fallback migration would
  wipe existing alarms too.
- `TimerRepository` (domain interface + Room-backed impl), same Flow-based
  observe-and-write shape as `AlarmRepository`, so later UI stages can
  observe reactively.

**Exit criteria:** CRUD and state-field round-trips on a `Timer` verified in
instrumented tests; a migration test confirms existing `Alarm` rows survive
the schema bump untouched; timer data survives an app restart.

---

## Stage 14 — Timer countdown/state engine

**Goal:** pure, unit-testable logic for how a timer's remaining time and
state evolve over time and user action — mirrors Stage 2's role for alarms.

Covers: *Timers Summary Page* (remaining-time display), *Adding a Timer*
(default duration), the start/pause/resume/reset control semantics.

- Pure Kotlin, no Android dependency, alongside Stage 2's `domain/schedule`:
  - `remaining(timer, now)`: `endInstant - now` (clamped to zero) when
    `RUNNING`; the stored paused-remaining value when `PAUSED`; the full
    configured duration when `STOPPED`; zero when `EXPIRED`.
  - State-transition functions, each a pure `(Timer, Instant) -> Timer`:
    - ⚠ **Start** (`STOPPED → RUNNING`): sets `endInstant = now +
      configuredDuration`.
    - ⚠ **Pause** (`RUNNING → PAUSED`): stores `remaining(timer, now)`,
      clears `endInstant`.
    - ⚠ **Resume** (`PAUSED → RUNNING`): recomputes `endInstant = now +
      storedRemaining` — mirrors how Stage 6's snooze recomputes an instant
      from "now" rather than replaying an original schedule.
    - ⚠ **Reset** (any state `→ STOPPED`): restores the full configured
      duration, discarding any in-progress or expired countdown — usable
      from `RUNNING`, `PAUSED`, or `EXPIRED` alike.
- Countdown display formatter: plain `H:MM:SS` (or `MM:SS` under an hour).
  - ⚠ Deliberately a *different* formatter from `FuzzyCountdown` (Stage 2):
    the spec's fuzzy d/h/min rounding rule (`docs/Requirements.md:40-54`) is
    explicitly scoped to the *Alarms* Summary page, and a live-decrementing
    timer needs second-level precision the fuzzy formatter discards by
    design.
- ⚠ *New-timer default state*: a timer created via Add Timer is `STOPPED` at
  its full configured duration, not auto-started — the spec lists Start as
  one of the Summary page's own per-timer controls, implying it's a
  separate, explicit action from creation.
- ⚠ *Post-expiry state*: a running timer reaching zero moves to a distinct
  `EXPIRED` state (pinned at zero) rather than silently reverting to
  `STOPPED` — Reset is the only way back to a fresh countdown, mirroring how
  a non-recurring alarm needs an explicit dismiss rather than quietly
  re-arming. **Flagged for product decision**, same open-question status as
  assumption #9 (disabled-alarm placement).
- ⚠ *List ordering*: the spec defines Summary sort order for alarms
  explicitly but not for timers. Placeholder: running/paused timers first
  (ascending remaining time), then stopped/expired timers by creation order.
  **Flagged for product decision.**

**Exit criteria:** unit test suite covering every state transition (including
resume-after-pause preserving the exact remaining duration across a
simulated time gap), the `H:MM:SS`/`MM:SS` formatter, and the
`STOPPED → RUNNING → EXPIRED` lifecycle, all green with no Android
dependency.

---

## Stage 15 — OS scheduling integration for timers

**Goal:** a running timer actually fires at the right instant and survives
restarts independent of whether the app process is alive — mirrors Stage 3's
role for alarms.

Covers: *Persistence* (ring-survival half), the non-functional requirements
as they apply to timers.

- New `timer` Gradle module (see the guiding technical decision above) with
  `TimerScheduler` (interface) + an `AlarmManager`-backed impl: arms the
  single `endInstant` for every `RUNNING` timer via
  `AlarmManager.setAlarmClock()` — same primitive and rationale as Stage 3
  — and cancels the OS alarm for any timer that isn't `RUNNING`. Re-synced
  on app start and on every `TimerRepository` write, the same idempotent
  "make the OS match storage" shape as `AlarmScheduler.sync`.
- `TimerReceiver` (mirrors `AlarmReceiver`): on fire, flips the timer to
  `EXPIRED` in storage and hands off to a foreground `TimerRingingService`
  (Stage 17 builds the full ringing UI/playback; this stage's own exit
  criteria only need an audible ring, the same staging trick Stage 3 used
  for alarms via an interim notification).
- `BootReceiver` counterpart: re-arms every `RUNNING` timer after reboot
  from its persisted `endInstant` — the concrete mechanism behind the
  spec's "a running timer continues counting down correctly across such an
  event, and will still ring at the correct time," since `endInstant`
  (Stage 13) needs no adjustment for how long the device was off.
- ⚠ *Concurrent expiries*: the spec doesn't say what happens when two
  timers expire close together. Placeholder, **flagged for product
  decision**: each expiry gets its own full-screen-intent notification: the
  exact ringing-*UI* queuing behavior is Stage 17's problem to resolve.

**Exit criteria:** a timer started for +2 minutes rings on a real device
after (a) a reboot, (b) force-stopping and relaunching the app, (c) with
battery optimization enabled for the app — the same three of Stage 3's four
cases that apply (timers have no time-zone concept, so there's no TZ-change
case here).

---

## Stage 16 — Navigation shell: Alarms/Timers switch + Timers Summary page

**Goal:** the app's title bar can switch between Alarms and Timers, and the
Timers Summary page shows real (Stage 13-15-backed) data with full list
controls — mirrors Stage 4's role for alarms plus the new top-level nav.

Covers: *Navigation*, *Timers Summary Page*.

- `TopAppBar` gains two icon buttons (⏰ Alarms, ⏱ Timers) beside the
  existing Settings gear, added to **both** the Alarms Summary and the new
  Timers Summary screens.
  - ⚠ *Active page's own icon*: spec doesn't say whether it's hidden,
    disabled, or left as a tappable no-op. Treated as always-visible and
    always-tappable (tapping the current page's own icon just re-navigates
    to itself), the simplest option and consistent with how the existing
    Settings icon behaves.
- New `Routes.Timers` destination in `TzAlarmClockNavHost` (mirrors
  `Routes.Summary`); a new `ui/timers` package (`TimersScreen`,
  `TimersViewModel`) following the same `AndroidViewModel` +
  repository-`Flow` pattern as `SummaryViewModel`, but on a faster (~1s, not
  30s) ticker, since seconds-level precision matters for a countdown in a
  way it doesn't for an alarm's fuzzy countdown.
- Per-timer row: remaining time (Stage 14's formatter, live-ticking) plus
  Start/Pause-Resume/Reset/Delete controls per the spec's exact list.
  - ⚠ *Which controls show when*: Start only when `STOPPED`; Pause when
    `RUNNING`; Resume when `PAUSED`; Reset always available (Stage 14's
    "usable from any state" design); Delete always available — not
    explicit in the spec beyond naming the four controls, but the natural
    state-gated mapping given Stage 14's state machine.
  - ⚠ *No delete confirmation*: deliberately inconsistent with alarm
    deletion (assumption #13) — a timer is cheap and fast to recreate
    (three number fields, no name/recurrence/zone to re-enter), so losing
    one by mistake is low-stakes by comparison.
- **Add Timer**: a control (the spec's own naming) opens the entry form —
  three numeric fields labelled h/min/s, tap-to-select-all-on-focus (so
  typing replaces rather than appends), numeric keyboard, defaulting to 0h
  5min 0s per spec; an empty field reads as 0. Confirming creates a new
  `STOPPED` timer at that duration and returns to the list.
  - ⚠ No explicit Cancel is specified for this form (unlike the Details
    page's Stage 11 buttons) — treated as a lightweight dismissible
    dialog/inline affordance with no side effects if abandoned, not a full
    navigation destination needing Stage 11-style dirty-state handling,
    since the spec's Add Timer section reads as a small entry form rather
    than a page.

**Exit criteria:** starting a timer counts down live and rings at expiry
(Stage 15); pause/resume preserves the exact remaining time across a real
elapsed gap; reset returns a running or expired timer to its original
configured duration; deleting a timer removes its row and cancels any armed
OS alarm; the Alarms/Timers icon buttons navigate correctly from both
Summary pages; a newly-added timer defaults to 0h 5min 0s with each field
pre-selected on tap.

---

## Stage 17 — Timer Ringing page

**Goal:** the page shown when a timer expires — mirrors Stage 6's role for
alarms.

Covers: *Timer Ringing Behavior*.

- Full-screen `TimerRingingActivity` (`app` module) launched via
  full-screen-intent notification from `TimerRingingService` (`timer`
  module), same `showWhenLocked`/`turnScreenOn` shape as `RingingActivity`,
  showing over the lock screen or on top of other apps per spec.
- Foreground service plays the **default timer ring tone** (new setting,
  Stage 18 — not any alarm-level ringtone), at the alarm volume/escalation
  settings.
  - ⚠ *Shared volume/escalation/vibration settings*: the spec adds only a
    timer *ringtone* setting, nothing else for timers — volume, escalation,
    and vibrate-if-capable all reuse the existing alarm-level `AppSettings`
    fields as-is rather than being duplicated per timer-vs-alarm.
- ⚠ *Dismiss only, no Snooze*: the spec's Timer Ringing Behavior section
  says the page "behaves like the Alarm Ringing Page" only for
  lock-screen/foreground/notification/playback purposes, then separately
  gives timers their own control set (Start/Pause/Resume/Reset/Delete on
  the Summary page) with no mention of snoozing a *ringing* timer. Treated
  as Dismiss-only: dismissing an expired timer moves it to `STOPPED` at its
  full original duration (ready to be started again), not left pinned at
  `EXPIRED`. Same accidental-stop mitigation as alarms (hold-to-dismiss)
  applies — the usability rationale (reduced visibility/cognition while
  something is ringing) isn't alarm-specific.
- ⚠ *Concurrent-expiry UI*: per Stage 15's placeholder, a second timer
  expiring while this page is already showing queues behind the current one
  (its own full-screen-intent notification stays pending) rather than
  interrupting or stacking activities — simplest safe behavior, **flagged
  for product decision** like the plan's other open placeholders.

**Exit criteria:** manual test on a real device with screen off and DND
enabled: an expired timer displays, sounds, and vibrates (if enabled)
exactly like an alarm does today; dismiss returns the timer to a fresh,
restartable state on the Timers Summary page.

---

## Stage 18 — App Settings: default timer ring tone

**Goal:** the one new setting Phase 2 adds gets a UI and is wired through —
mirrors Stage 7's role for alarms.

Covers: *Timer Ring Tone*, *App Settings* (extension).

- `AppSettings` gains a tenth field, `defaultTimerRingtoneUri: String?`
  (nullable → falls back to the system default alarm sound, same shape as
  the existing `defaultRingtoneUri`), persisted via the same DataStore key
  pattern (`SettingsKeys`). This is the first `AppSettings` schema change
  since `ringTimeoutMinutes` (assumption #24) and follows the same
  precedent: an additive nullable-or-defaulted field, no migration needed
  since DataStore Preferences has no schema to migrate.
- Settings page gains a "Default timer ring tone" row next to the existing
  "Default ring tone" (alarm) row, reusing the existing `RingtonePickerRow`
  component (`ui/common`) unmodified — it's already parameterized by a
  fallback URI (per Stage 7) for exactly this kind of second use.

**Exit criteria:** picking a timer ringtone in Settings and reopening the
app shows the persisted value; a newly-expiring timer plays that ringtone
(Stage 17), not the alarm default.

---

## Stage 19 — Timers reliability & polish pass

**Goal:** close the same category of gaps Stages 8/9/12 closed for alarms,
scoped to what Phase 2 actually adds — lighter than those three combined,
since timers reuse most of the app's existing infrastructure (theming,
accessibility patterns, R8 config, CI) rather than introducing new
categories of risk.

- DND/silent-bypass and battery-efficiency audits, scoped to
  `TimerScheduler`/`TimerRingingService`: confirm the same
  `AudioAttributes.USAGE_ALARM` + high-importance-channel configuration
  Stage 9 verified for alarms also holds for the new timer notification
  channel, and that no polling runs except while a timer is actively
  `RUNNING` or ringing.
- Accessibility: `TimerRingingActivity`'s hold-to-dismiss gets the same
  `semantics { onLongClick(...) }` treatment Stage 12 Task 2 added for
  alarms, rather than rediscovering the same gap independently.
- Responsive layout: Add Timer's three-field h/min/s row checked at the
  same 360×640dp / landscape matrix Stage 12 used for Details — three
  side-by-side numeric fields plus labels is a narrow-width risk this app
  hasn't had before.
- Empty state: Timers Summary shows an equivalent of Summary's "No alarms
  yet." for a zero-timer list.
- R8/release build: confirm the new `timer` module's entities/receivers/
  services survive shrinking the same way Stage 10 confirmed for `alarm`
  (an explicit spot-check, not an assumption that Room/KSP's existing
  consumer rules automatically cover a same-shaped new module).

**Exit criteria:** fresh-install walkthrough of the Timers Summary, Add
Timer, and Timer Ringing pages with no placeholder data, on at least two
screen sizes; a release-variant (`assembleRelease`) install exercises
create/start/pause/resume/reset/delete/expire with zero crashes; the same
`uiautomator` long-clickable check Stage 12 used confirms the Timer Ringing
dismiss gesture has an accessible equivalent.

---

## Phase 3 backlog

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
10. ~~Back-button on Details page autosaves; there's no separate discard/
    cancel path since the spec doesn't request one.~~ **Superseded by
    Stage 11** — see item 22.
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
17. `USE_EXACT_ALARM` (install-granted, non-revocable, intended for alarm
    clock apps) is preferred over `SCHEDULE_EXACT_ALARM`, which is declared
    only for API 31–32; the startup permission check is kept either way.
18. Battery-optimisation exemption is offered from the startup warning
    rather than requested unprompted, and is presented as advisory.
19. Stage 3 ships an interim sound-carrying notification so its own "rings"
    exit criteria are verifiable; Stage 6 supersedes it on a new channel id.
20. An alarm with no future occurrence is disabled at the moment it fires
    (Stage 3) rather than at dismiss (Stage 6), so a time-only alarm can't
    re-arm itself indefinitely.
21. `FuzzyCountdown` reads `"< 1 min"` rather than a literal `"0 min"` when
    the remainder rounds to zero (Stage 8), deviating from Stage 2's literal
    spec algorithm since "0 min" reads as no time left rather than imminent.
22. ⚠ **Deliberate deviation from the spec** (Stage 11), not an ambiguity
    resolution: `Requirements.md:70-71` says the Android back button always
    saves changes on the Details page. Stage 11 instead gives new alarms
    explicit **Add**/**Cancel** buttons and existing alarms **Cancel**/
    **Delete** buttons (no page-level Save), remaps system back to behave
    like Cancel, and shows an exit-confirmation dialog only when the form is
    dirty: 2-way *"Discard changes?"* for a new alarm (Add is the only save
    path), 3-way *"Save changes before leaving?"* (Save/Discard/Keep
    Editing) for an existing one, since editing has no other way to persist
    a change — resolved with the user via `AskUserQuestion` on 2026-08-04
    after the plan's earlier "Cancel reverts to last-saved state" draft
    turned out to have no path to actually keep an edit.
23. Summary page recurrence label (Stage 11, an addition, not a spec
    deviation — the spec's field list has no such label): weekly reads
    `"Weekly "` + the Android SDK's short `DayOfWeek` display name
    (`TextStyle.SHORT`) in Mon-first calendar order (e.g. `"Weekly Mon, Wed,
    Fri"`) — not the single-letter narrow form, since narrow abbreviations
    collide (Tue/Thu both "T", Sat/Sun both "S"); monthly reads `"Monthly "` +
    ascending days-of-month (e.g. `"Monthly 1, 15"`). The label is treated as
    English-only, unlike the spec's explicitly locale-independent countdown
    labels.
24. **Unacknowledged-ring timeout, added 2026-08-08, revised same day — an
    addition, not a spec deviation** (the spec's Alarm Ringing / App
    Settings sections don't mention one; requested directly by the user,
    after the plan's staged build had already completed, so it isn't a
    numbered stage). A ninth `AppSettings` field, `ringTimeoutMinutes`
    (default **3**, range 1–60 minutes, same shape as the existing
    snooze-period stepper on the Settings page): `RingingService` now
    schedules a delayed self-action alongside playback on every `ring()`,
    cancelled the moment the user snoozes or dismisses first. That action is
    **snooze**, not dismiss, whenever `maxSnoozeCount` allows one more —
    the exact same `snooze()` path an explicit tap takes (arms the next
    instant, posts "Snoozed until…"); only once snoozes are exhausted does
    it fall back to `dismiss()`, reusing the exact same path a manual
    hold-to-dismiss takes (non-recurring alarms disable, snooze state
    clears, scheduler resyncs) — the one difference there is it also posts a
    new "Alarm canceled" notification (own low-importance channel,
    `alarm_canceled_v1`) naming the alarm and the time it was set for, since
    nobody was necessarily present to see it happen. (First implemented as
    always-dismiss with a 5-minute default; changed to snooze-first, 3-minute
    default, same day, before merge — no released version ever shipped the
    always-dismiss behavior.) The ringing screen itself needs to close on
    its own too when this fires in the background, regardless of which of
    the two outcomes it was: `RingingService` now tracks the
    currently-ringing alarm id in an in-memory companion var, and
    `RingingViewModel`'s existing 1-second tick polls it (alongside the
    snooze count it already polled) to finish the screen once the service
    ends the cycle without a tap here having caused it.
25. A `Timer` has no name field — only a configured h/min/s duration, per the
    spec's Add Timer section (Stage 13).
26. A running timer's fixed end instant is persisted (not just its
    remaining-at-start duration) so it survives reboot/hard-stop with the
    correct fire time, no adjustment needed for elapsed downtime (Stage 13).
27. Timer state machine: `STOPPED` (fresh, full duration) → `RUNNING` ⇄
    `PAUSED`, `RUNNING` → `EXPIRED` on its own, and Reset returns to
    `STOPPED` from any state; a newly-created timer starts `STOPPED`, not
    auto-started (Stage 14).
28. Timer countdown display uses a plain `H:MM:SS`/`MM:SS` formatter,
    deliberately distinct from `FuzzyCountdown`, since the spec's fuzzy
    d/h/min rounding rule is scoped to the Alarms Summary page only
    (Stage 14).
29. Timers Summary list ordering (unspecified by the spec, unlike alarms'
    explicit ascending time-to-ring): running/paused timers first by
    ascending remaining time, then stopped/expired timers by creation order
    — placeholder for a product decision (Stage 14), same open status as
    item 9.
30. A new `timer` Gradle module mirrors `alarm`'s internal shape rather than
    extending `alarm` itself, accepting some duplication of the
    scheduler/receiver/PendingIntent-per-id pattern to keep both modules'
    names honest and state machines uncoupled (Stage 15, and the guiding
    technical decisions section above).
31. Concurrent timer expiries each get their own full-screen-intent
    notification and queue rather than interrupt each other's ringing UI —
    placeholder for a product decision (Stages 15/17).
32. The title bar's Alarms/Timers icon buttons are always visible and
    tappable, including on the page that's already active (a no-op tap) —
    the spec doesn't say whether the active page's own icon should hide or
    disable instead (Stage 16).
33. Timer deletion has no confirmation step, deliberately inconsistent with
    alarm deletion (item 13), since a timer is cheap and fast to recreate
    (Stage 16).
34. Add Timer's entry form has no explicit Cancel affordance — treated as a
    lightweight dismissible dialog with no side effects if abandoned, not a
    full page needing Stage-11-style dirty-state handling (Stage 16).
35. Timer Ringing is Dismiss-only, with no Snooze — the spec names
    Start/Pause/Resume/Reset/Delete as the Timers Summary controls and never
    mentions snoozing a ringing timer; dismiss returns the timer to
    `STOPPED` at its full original duration (Stage 17).
36. Timers reuse the existing alarm-level volume, escalation, and
    default-vibrate settings rather than getting their own — the spec adds
    only a timer *ringtone* setting (Stage 17).
