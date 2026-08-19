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

- **Navigation** — a title bar tab switcher moves between the Alarms and
  Timers Summary pages, with Settings alongside.
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
  the alarm's time. If more than one alarm is ringing at once, they share a
  single notification and ringing screen, and Snooze/Dismiss act on all of
  them together — each alarm's own unacknowledged-ring timeout still runs
  independently, so one alarm auto-snoozing or auto-dismissing never
  disturbs another still-ringing alarm.
- **Timers Summary** — run multiple timers concurrently, each counting down
  independently, with per-timer start/pause-resume/reset/delete and a live
  remaining-time display.
- **Adding a Timer** — enter a duration via h/min/s fields; tapping a field
  selects its existing value so the next keystroke replaces rather than
  appends.
- **Timer Ringing** — behaves like Alarm Ringing (over-lock-screen/on-top
  full-screen UI, notification, tap-to-dismiss) using a separate, globally
  configured timer ring tone. Timers that expire while another is still
  ringing join the same notification and ringing screen rather than cutting
  it off; Dismiss clears every ringing timer at once, while each timer's own
  ring timeout still dismisses just that timer on its own.
- **Settings** — grouped into General (home time zone, 12/24-hour format),
  Alarm (snooze period and max snooze count, default ringtone, alarm volume
  with optional escalation, default vibration, unacknowledged-ring timeout),
  and Timer (default timer ring tone).

## Install

See [`docs/Install.md`](docs/Install.md) for how to build and install the
app on a device.

## Documentation

- [`docs/Requirements.md`](docs/Requirements.md) — the product/behavioral
  spec: features, screens, alarm semantics, and the Phase 2 (timers) and
  Phase 3 (backlog) sections.
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

