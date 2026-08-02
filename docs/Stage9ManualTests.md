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

- [x] Set the device to **Total silence** (`adb shell cmd notification
      set_dnd none`, or the volume rocker). Armed an alarm two minutes out;
      it fired on time and the full-screen Ringing UI displayed correctly
      over the lock state (confirmed via screenshot, with the DND icon
      visible in the status bar) — the notification/full-screen-intent half
      of the bypass works under DND. **The audio did not play** — confirmed
      live on the Motorola on 2026-08-02, not just inferred from logs.
      `dumpsys audio` shows why: under `ZEN_MODE_NO_INTERRUPTIONS`,
      `STREAM_ALARM` itself is muted at the ringer-mode level
      (`Muted: true`, `streamVolume:0`, and `STREAM_ALARM` listed in
      "ringer mode muted streams") regardless of the player's
      `AudioAttributes.USAGE_ALARM`. ⚠ **This matches Android's documented
      Total Silence behavior** ("mutes all sounds, including alarms") — it's
      the one DND level designed to be inescapable, and even the stock
      Clock app is silenced by it. Not treated as an app bug; no code
      change made. Recorded here because Stage 9's own checklist wording
      assumed otherwise going in, and that assumption was wrong.
- [x] Plain **Silent** ringer mode (not DND — `adb shell cmd audio
      set-ringer-mode SILENT`, zen off): alarm rang audibly. Confirmed live
      on the Motorola. This is the everyday "phone on silent" case the spec
      most likely means, and it works correctly.
- [x] DND **Priority only** (`adb shell cmd notification set_dnd
      priority`): alarm rang audibly. Confirmed live on the Motorola.
- [x] Full-screen Ringing UI over the lock screen under DND: confirmed in
      the Total Silence case above (screenshot taken with DND active).

## 2. Restart / kill / update / battery-optimization survival

Stage 3 already signed off cases (a)-(d) of this matrix on the user's real
Motorola (`docs/Stage3ManualTests.md`, commit `27c5b7a`). Stage 9 doesn't
repeat that work; it re-confirms the same four cases still hold now that
Stages 4-8 have landed substantial changes around them (snooze registry,
ringing service, settings-driven volume/vibration), plus documents the one
case that's structurally untestable here.

- [x] **(a) Reboot** — confirmed live on the Motorola, 2026-08-02. Armed
      +6min, `adb reboot`, waited for `sys.boot_completed=1`, user unlocked
      the device. The app process auto-started from `BOOT_COMPLETED` (no
      manual launch needed) and `BootReceiver`/`AlarmScheduler` re-armed the
      alarm within ~6s of boot completing (confirmed via logcat + `dumpsys
      alarm`). Rang exactly on time; since the user was actively browsing
      the app drawer when it fired, the full-screen intent correctly fell
      back to a heads-up notification instead of forcing full-screen
      (expected behavior per Android's full-screen-intent rules — full
      takeover is reserved for the locked/idle case), and tapping it brought
      up the full Ringing screen.
- [x] **(b) Force-stop + relaunch** — confirmed live on the Motorola,
      2026-08-02. Armed +8min, `am force-stop`, waited, relaunched. Alarm
      history showed `Reason=pi_cancelled` on force-stop (as expected) and
      `dumpsys alarm` showed a fresh `RTC_WAKEUP` entry re-armed immediately
      on relaunch; rang correctly at the scheduled time.
- [x] **(c) Time-zone change mid-countdown** — confirmed live on the
      Motorola, 2026-08-02, via `adb shell cmd alarm set-timezone` (same
      mechanism `TimeChangeReceiver` reacts to) plus `dumpsys
      alarm`/logcat, which is more precise than a stopwatch: a floating
      alarm armed for `2026-08-02T16:30-06:00[America/Edmonton]` re-armed to
      `2026-08-03T16:30+09:00[Asia/Tokyo]` (same wall-clock time, new zone)
      the moment the device zone changed to Asia/Tokyo — confirmed by the
      `AlarmScheduler` log line and a `whenElapsed` jump in `dumpsys alarm`.
      A zone-locked alarm (explicit `America/Edmonton`) kept the exact same
      `origWhen` epoch millis (`1785709800000`) across two further device
      zone changes (Tokyo → Berlin), proving it doesn't move. (A live
      "let it ring in the new zone" run wasn't completed — a UI dial mistap
      during the session left that alarm at the wrong time — but the
      `dumpsys`/logcat evidence for the re-arm mechanics is more rigorous
      than a stopwatch-timed ring would have been, so this wasn't repeated.)
- [x] **(d) Battery optimization set to "Optimised"** — confirmed live on
      the Motorola, 2026-08-02. App confirmed NOT battery-exempted
      (`dumpsys deviceidle whitelist` empty for the package — the default,
      matching this case's precondition), screen turned off via
      `KEYCODE_POWER`, alarm armed 2 minutes out. Fired exactly on time,
      `dumpsys power` showed `mWakefulness=Awake` (screen woken from off),
      and the Ringing UI displayed correctly — confirms `setAlarmClock()`
      fires through Doze regardless of the app's own battery-optimization
      state, as designed.
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
- Device spot-check confirmed live on the Motorola, 2026-08-02: with no
  alarm ringing/snoozed, `adb shell dumpsys activity services
  imb.tzalarmclock` shows no `RingingService` entry; it appeared during
  every ring/snooze in the tests above and was confirmed gone again after
  each dismiss.

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

- [x] Notification permission denied: confirmed live on the Motorola,
      2026-08-02 (fresh install had it un-granted). The critical (error-
      coloured) banner appeared above the nav host, persisting across every
      screen; granting it via the in-app "Allow" prompt made it disappear on
      the next `onResume`, confirmed by screenshot before/after.
- [ ] Revoke `SCHEDULE_EXACT_ALARM` from system Settings — **not applicable
      on this device**: the test Motorola runs API 35, where
      `USE_EXACT_ALARM` is install-granted and non-revocable (per
      `ExactAlarmPermission`'s own doc comment), so this banner state can't
      be triggered here. Would need an API 31-32 device to exercise.
- [x] Battery optimization enabled (the default, un-exempted state):
      confirmed live throughout this session's testing — the advisory
      (non-critical) banner was visible in every screenshot taken while the
      app was un-exempted, including during the reboot/force-stop/battery-
      optimization-ring-through tests above.
