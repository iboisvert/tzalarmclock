# TzAlarmClock

An Android alarm clock that adjusts your alarms automatically when the device
changes time zones. Travellers don't have to recalculate wake-up times or
leave the device time zone set to manual.

Stock alarm apps may not account for time zone changes: an alarm set for
7:00 AM stays at 7:00 AM device-local time, even after you cross time zones.
TzAlarmClock lets each alarm be one of two kinds:

- **Floating** alarms ring at the same wall-clock time in whatever zone the
  device is currently in.
- **Zone-locked** alarms ring at a fixed instant regardless of the device
  zone. Use them for meetings, so the alarm rings at the same moment wherever
  you are.

## Features

- **Alarms** — floating or zone-locked, and either kind can be made recurring.
- **Timers** — run several countdown timers at once, each with its own
  start, pause, reset, and ring.
- **Google Assistant** — set alarms and timers by voice through the system
  alarm and timer intents.

## Install

See [`docs/Install.md`](docs/Install.md) for how to build and install the
app on a device.
