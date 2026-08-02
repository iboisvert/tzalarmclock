# Stage 9 — reliability & non-functional hardening

Stage 9's exit criteria are the four non-functional requirements the app
exists to satisfy, each with "an explicit, repeatable test or audit step
checked off, not just 'seems to work.'" Two of the four (battery efficiency,
no network dependency) are settled by a code audit rather than a device test,
since they're claims about what code *doesn't* run/call rather than
observable device behaviour. The other two need a real device, same as
Stage 3.

## 1. DND / silent bypass

**Automated regression coverage:** `RingingNotificationsTest`
(`alarm/src/androidTest`) pins the mechanism this relies on: the ringing
channel (`alarm_ringing_v2`) is `IMPORTANCE_HIGH`, and the built notification
is `CATEGORY_ALARM` at `PRIORITY_MAX` and stays ongoing. That covers the
notification half. The audio half — `RingingService`'s `MediaPlayer` and
`Vibrator` both built with `AudioAttributes`/`VibrationAttributes` using
`USAGE_ALARM` (`RingingService.kt`, `newMediaPlayer`/`startVibration`) — is
what actually makes Android route sound through DND/silent regardless of
notification settings, and can't be asserted from an instrumented test
without genuinely playing audio, so it's confirmed live below.

- [ ] Set the device to **Total silence** (or the Motorola equivalent) via
      the volume rocker or Settings → Sound → Do Not Disturb.
- [ ] Arm an alarm for +2 minutes from the Details page.
- [ ] Confirm the alarm rings audibly (and vibrates, if enabled) at the
      configured volume despite Total silence being active — this is the #1
      complaint (per the spec) the app exists to fix.
- [ ] Repeat with DND's "Priority only" mode and confirm the same.
- [ ] Confirm the full-screen Ringing UI still appears over the lock screen
      under DND (some OEMs suppress full-screen intents separately from
      audio — this checks that path too).

## 2. Restart / kill / update / battery-optimization survival

Stage 3 already signed off cases (a)-(d) of this matrix on the user's real
Motorola (`docs/Stage3ManualTests.md`, commit `27c5b7a`). Stage 9 doesn't
repeat that work; it re-confirms the same four cases still hold now that
Stages 4-8 have landed substantial changes around them (snooze registry,
ringing service, settings-driven volume/vibration), plus documents the one
case that's structurally untestable here.

- [ ] **(a) Reboot** — re-run Stage 3's recipe; confirm still ringing after
      reboot + first unlock.
- [ ] **(b) Force-stop + relaunch** — re-run; confirm still ringing.
- [ ] **(c) Time-zone change mid-countdown** — re-run for both a floating and
      a zone-locked alarm; confirm floating re-arms to the new zone's
      wall-clock time and zone-locked doesn't move.
- [ ] **(d) Battery optimization set to "Optimised"** — re-run; confirm still
      rings through Doze/OEM power management.
- [x] **(e) OS update.** Documented as untestable without a real OTA on the
      test device — noted here rather than skipped silently. The closest
      available proxy is (b): a force-stop is how the OS treats an app across
      most update paths that don't outright reinstall it, and that case is
      covered above.

## 3. Battery efficiency (code audit)

**Audited 2026-08-02, no changes needed.** Confirmed by reading the code
rather than a device test, since the requirement is the *absence* of a
mechanism:

- No `Handler.postDelayed`, `Timer`, `WorkManager`, or `JobScheduler` anywhere
  in the codebase. The only look-alike hits are Compose's `BackHandler` API
  (`DetailsScreen.kt`, `SettingsScreen.kt`) and `ContextCompat
  .startForegroundService` in `AlarmReceiver.kt` — unrelated.
- All scheduling is event-driven: `AlarmManager.setAlarmClock()` plus
  `BroadcastReceiver`s for `BOOT_COMPLETED`, `TIMEZONE_CHANGED`/`TIME_SET`,
  and the alarm's own fire intent. `TzAlarmClockApplication` re-syncs the
  scheduler by collecting `AlarmRepository.observeAlarms()`, a Room `Flow`
  that only emits when a row actually changes (Room's invalidation tracker),
  not on a timer.
- `RingingService` (`mediaPlayback` foreground service type) is the only
  foreground service in the app. It starts only from `AlarmReceiver`'s fire
  intent or a snooze/dismiss action, and calls `stopSelf()` at the end of
  `ring`'s error path, `snooze()`, and `dismiss()` — it does not stay
  foreground once the alarm is neither ringing nor snoozed. The snoozed
  state is represented by a plain notification (`STOP_FOREGROUND_DETACH`),
  not a lingering foreground service.
- The Summary page's live countdown ticker (`SummaryViewModel`, `delay(...)`
  in a `viewModelScope`-bound loop) only runs while that screen's ViewModel is
  alive — foreground UI, not a background mechanism — and is unrelated to
  this requirement.
- Optional device spot-check: with no alarm ringing/snoozed,
  `adb shell dumpsys activity services imb.tzalarmclock` should show no
  running service; snooze one and re-run to see `RingingService` appear;
  dismiss it and confirm it's gone again.

## 4. No network dependency (code audit)

**Audited 2026-08-02, no changes needed.**

- No `INTERNET` or `ACCESS_NETWORK_STATE` permission declared in any
  module's `AndroidManifest.xml`.
- No HTTP/networking library or API (`OkHttp`, `Retrofit`, `HttpURLConnection`,
  `Socket`, `ktor`, etc.) anywhere in the codebase — the only `http://` hits
  are doc-comment links in the stock `ExampleInstrumentedTest`/
  `ExampleUnitTest` boilerplate.
- Time-zone data (`ZoneId`, DST rules) comes from the OS/ICU tzdb bundled
  with the platform, not fetched remotely.
- Ringtone lookup goes through `RingtoneManager`/the system picker, both
  purely local (`content://` URIs into the device's media store).

## 5. Startup permission check

Already implemented (`SchedulingHealth`, `SchedulingWarnings`,
`ExactAlarmPermission`, `BatteryOptimization` — landed in Stage 3, ahead of
this stage's own numbering). `SchedulingHealth.allClear`'s three-way check is
now covered by `SchedulingHealthTest` (`alarm/src/test`). Confirm live:

- [ ] Fresh install, deny the notification permission when prompted: the
      warning banner appears above the nav host on every screen and offers
      "Allow"; granting it (Settings or the in-app prompt) makes it disappear
      on the next `onResume`.
- [ ] Revoke `SCHEDULE_EXACT_ALARM` from system Settings (API 31-32 devices;
      not revocable on 33+ since `USE_EXACT_ALARM` is install-granted): the
      banner appears in its error-coloured (critical) styling.
- [ ] Leave battery optimization enabled for the app: the advisory
      (non-critical) banner appears alongside/instead of the above.
