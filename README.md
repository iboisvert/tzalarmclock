# TzAlarmClock

An Android alarm clock that updates alarms automatically when the device
changes time zones, so travellers don't have to manually recalculate wake-up
times or leave the device time zone set to manual.

Stock alarm apps (e.g., the Motorola default) don't account for time zone
changes: an alarm set for 7:00 AM stays at 7:00 AM device-local time even
after flying somewhere the intended wake-up instant has shifted. TzAlarmClock
solves this by letting an alarm be either **floating** (always rings at a
given wall-clock time in whatever zone the device is currently in) or
**zone-locked** (rings at a fixed instant regardless of device zone).

## Status

In progress. Stages 0–3 of `docs/DevelopmentPlan.md` are done: the module
skeleton, alarm/settings persistence, the recurrence and next-occurrence
engine, and OS scheduling — alarms are armed with `AlarmManager`, re-armed
after a reboot or an app update, and re-evaluated whenever the device's time
zone changes. The four screens are still Stage 0 placeholders, so there is no
way to create an alarm from the UI yet; that arrives with Stages 4–5.

Stage 3's exit criteria are only partly verifiable on an emulator — reboot,
force-stop, and OEM battery-optimisation behaviour need a real device. See
`docs/Stage3ManualTests.md` for the checklist.

## Features

- **Alarms Summary** — all alarms grouped into Today / Tomorrow / This Week /
  Later, sorted by time-to-ring, with a fuzzy countdown ("2 d, 5 h" / "3 h" /
  "45 min") and quick enable/disable.
- **Alarm Details** — create/edit an alarm: name, time, optional time zone,
  fixed date or recurrence (weekly by day, or monthly by day-of-month),
  ringtone, vibration, delete.
- **Alarm Ringing** — full-screen, over-lock-screen ringing UI designed for
  low light and reduced cognition, with dismiss and snooze. Dismiss is
  deliberately harder to trigger than snooze, to avoid accidentally killing
  an alarm you meant to pause.
- **Settings** — home time zone, snooze period and max snooze count, default
  ringtone/vibration, alarm volume with optional escalation, 12/24-hour time
  format.

Full behavioral spec, including the fuzzy-countdown rounding rules,
recurrence semantics, and DST handling, is in `docs/Requirements.md`.

## Non-functional requirements

These are the reason the app exists and are treated as first-class,
independently tested requirements (see Stage 8 of the development plan):

- Fires even when the phone is on silent or in Do Not Disturb.
- Survives reboots, battery-optimization killing background processes, and
  OS updates.
- Never depends on internet connectivity to fire.
- Battery-efficient — no polling; scheduling is event-driven.

## Tech stack

- Kotlin, Jetpack Compose, single-Activity architecture.
- Room for alarm storage; Jetpack DataStore for app settings.
- `AlarmManager.setAlarmClock()` for scheduling, chosen because it's the API
  that reliably fires through Doze and surfaces as a visible system alarm.
- Full-screen-intent notification + foreground service for the ringing page,
  so it can appear over the lock screen and bypass DND.
- Target API level 31. Root package: `imb.tzalarmclock`.

Rationale for these choices, and the alternatives considered, are in
`docs/DevelopmentPlan.md`.

## Documentation

- [`docs/Requirements.md`](docs/Requirements.md) — the product/behavioral
  spec: features, screens, alarm semantics, and Phase 2 backlog.
- [`docs/DevelopmentPlan.md`](docs/DevelopmentPlan.md) — the staged build
  plan derived from the spec, including every assumption made to resolve
  ambiguity in the requirements (recurrence/date exclusivity, DST edge
  cases, week-grouping boundaries, dismiss/snooze interaction model, etc.),
  each logged explicitly so they can be reviewed rather than discovered
  later.

Phase 2 work (per-day recurrence times, public-holiday skipping, recurrence
end dates, pre-alarm screen brightness ramp) is intentionally out of scope
until Phase 1 above is complete — see the Phase 2 backlog section of the
development plan.
