# Stage 3 — manual test checklist

Stage 3's exit criteria are about what the OS does to a scheduled alarm over a
reboot, a force-stop, a time-zone change, and under battery optimisation. Three
of those four can't be reproduced on an emulator, so they are checked by hand
on a real device.

The four screens are still Stage 0 placeholders, so **there is no way to create
an alarm from the UI yet** — Stages 4–5 add that. Until then alarms are
inserted directly into the app database over `adb`, which is what the setup
step below does.

## Setup

Install the debug build and create an alarm row:

```bash
./gradlew :app:installDebug
adb shell am start -n imb.tzalarmclock/.MainActivity
```

Grant the notification permission when the startup banner offers it, otherwise
a fired alarm will make no sound and show nothing.

Arm an alarm two minutes out. `minute_of_day` is minutes since local midnight;
this computes it from the device's own clock:

```bash
now=$(adb shell date +%H:%M | tr -d '\r')
mod=$(( (10#${now%%:*} * 60 + 10#${now##*:} + 2) % 1440 ))

adb shell "run-as imb.tzalarmclock sqlite3 \
  /data/data/imb.tzalarmclock/databases/tzalarmclock.db \
  \"insert into alarms (name, minute_of_day, zone_id, schedule_type, enabled)
    values ('Manual test', $mod, NULL, 'NEXT_OCCURRENCE', 1);\""
```

The app only notices the new row while it is running, so relaunch it, then
confirm the OS took the alarm:

```bash
adb shell am force-stop imb.tzalarmclock
adb shell am start -n imb.tzalarmclock/.MainActivity
adb logcat -d -s AlarmScheduler:*          # "Armed alarm N for <instant>"
adb shell "dumpsys alarm | grep -A4 walarm.*tzalarmclock"
```

In `dumpsys` the alarm should read `type=RTC_WAKEUP` with a non-empty
`exactAllowReason`. A `NEXT_OCCURRENCE` alarm disables itself once it fires, so
re-run the insert (or flip `enabled` back to 1) before each case below.

## The four cases

Each one passes if the alarm audibly rings at the expected minute.

- [ ] **(a) Reboot.** Arm for +2 min, `adb reboot`, leave the device alone and
      unlock it once it comes up. The alarm is re-armed by `BootReceiver`, but
      only after the first unlock — the database is credential-protected, so
      `BOOT_COMPLETED` on a still-locked device can't read it. If the device
      isn't unlocked before the alarm is due, it will not ring; that's a known
      limitation of Stage 3, not a bug in this test.

- [ ] **(b) Force-stop, then relaunch.** Arm for +2 min, force-stop the app
      from Settings → Apps, wait ~30 s, then launch it again from the
      launcher. A force-stop clears the app's pending alarms *and* blocks
      broadcasts until the user launches it manually, so re-arming on launch
      is the only recovery path. Alarms will not survive a force-stop that is
      never followed by a launch — expected, and true of every alarm app.

- [ ] **(c) Time-zone change mid-countdown.** Arm for +2 min, then change the
      device time zone (Settings → System → Date & time, turn off automatic,
      pick a zone several hours away). A floating alarm should re-arm to the
      same wall-clock time in the *new* zone, which almost certainly moves it
      out of the next two minutes. Confirm with `dumpsys` rather than by
      waiting. Then set an alarm two minutes out in the new zone and let it
      ring. Re-run with `zone_id` set to an IANA id (e.g. `'Europe/Berlin'`)
      to confirm a zone-locked alarm's instant does *not* move.

- [ ] **(d) Battery optimisation enabled.** In Settings → Apps → TzAlarmClock
      → Battery, set the app to "Optimised" or the Motorola equivalent (the
      startup banner offers the opposite, so decline it here). Arm for
      +2 min, turn the screen off and leave the device untouched.
      `setAlarmClock` is supposed to fire through Doze; this case exists
      because OEM power managers layer their own rules on top and are a known
      cause of missed alarms.

## Already verified on the emulator

For reference, so real-device time is spent on what actually needs it:

- An alarm reaches the OS at the right instant
  (`AndroidAlarmSchedulerTest.theArmedInstantReachesTheSystemAlarmClock`), and
  it lands as `RTC_WAKEUP` / "Next wake from idle".
- Alarms dropping out of the plan are cancelled, including across a simulated
  process restart.
- `TimeChangeReceiver` re-arms a floating alarm after
  `cmd alarm set-timezone`, starting the app process from cold to do it.
  Checked across Toronto / Berlin / Tokyo, including whether today's
  occurrence had already passed in each zone.
- A fired alarm retires itself if non-recurring and re-arms if recurring
  (`AlarmReceiverTest`).
- Relaunching after a force-stop re-arms.
