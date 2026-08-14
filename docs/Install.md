# Install

There are two ways to get TzAlarmClock onto a device: install a prebuilt
release, or build it from source.

## Install a prebuilt release

1. Go to the project's [Releases page](https://github.com/iboisvert/tzalarmclock/releases)
   and download the `.apk` from the latest release.
2. On the device, allow installing apps from the source you downloaded it
   with (browser, file manager, etc.) — Android prompts for this
   automatically the first time you open an APK it didn't get from a store.
3. Open the downloaded file to install.

Every release APK is signed with the project's release key, so installing
a new release over an existing install upgrades in place and keeps your
alarms. Requires Android 12 (API 31) or later.

## Build from source

Prerequisites:

- JDK 21.
- Android SDK with platform 37 installed.

Steps:

```
git clone git@github.com:iboisvert/tzalarmclock.git
cd tzalarmclock
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`; install
it with `adb install app-debug.apk` or by copying it to the device.

A debug build is signed with the Android debug key, which is a different
signature than the release APKs from the Releases page — installing one
over the other fails rather than upgrading, so pick one build type per
device and stick with it.

### `assembleRelease` needs the private `secrets` submodule

`secrets` (see `.gitmodules`) holds the release-signing keystore and is a
separate private repository — not something an outside contributor has
access to. `assembleDebug` (and everything else) works fine without it;
only `assembleRelease`/`bundleRelease` need it, and fail with a clear error
telling you so if it's missing. If you do have access, fetch it first with
`git submodule update --init`.
