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
  an alarm you meant to pause. An unacknowledged ring auto-snoozes after a
  configurable timeout, the same as tapping Snooze; once no snoozes are left
  it auto-dismisses instead, posting a notification of the cancellation and
  the alarm's time.
- **Settings** — home time zone, snooze period and max snooze count, default
  ringtone/vibration, alarm volume with optional escalation, 12/24-hour time
  format, unacknowledged-ring timeout.

## Install

See [`docs/Install.md`](docs/Install.md) for how to build and install the
app on a device.

## Documentation

- [`docs/Requirements.md`](docs/Requirements.md) — the product/behavioral
  spec: features, screens, alarm semantics, and Phase 2 backlog.
- [`docs/DevelopmentPlan.md`](docs/DevelopmentPlan.md) — the staged build
  plan derived from the spec, including every assumption made to resolve
  ambiguity in the requirements (recurrence/date exclusivity, DST edge
  cases, week-grouping boundaries, dismiss/snooze interaction model, etc.),
  each logged explicitly so they can be reviewed rather than discovered
  later.
- [`docs/Stage3ManualTests.md`](docs/Stage3ManualTests.md) and
  [`docs/Stage9ManualTests.md`](docs/Stage9ManualTests.md) — real-device
  checklists for the behaviour an emulator can't verify: reboot/force-stop
  survival, time-zone-change reactivity, and DND/silent/battery-optimisation
  bypass.

