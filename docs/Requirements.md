# Time Zone Alarm Clock Requirements Spec
Time Zone Alarm Clock is a mobile app 
that helps travellers keep appointments 
by updating alarms when the device changes time zones.

The impetus for building this app is that 
I must manually update alarm times when I travel to visit family--
the out-of-the-box alarm clock app provided on the Motorola 
does not support time zones.
Manually updating alarm times is error-prone, so instead
I set the time zone of the device to manual and do not update
the time zone when I travel. 

The short name of the app is TzAlarmClock

## High-level Features
1. Summary page of existing alarms
2. Details page to create new and edit existing alarms
3. Alarm ringing page
3. App settings

## Detailed Design
Time Zone Alarm Clock is an app for Android 
targeting API level 31. 
The root package of the code in project is `imb.tzalarmclock`.

The app uses the `android.app.AlarmManager` class to
set alarms at the system level.

### Alarms Summary Page
The alarms summary presets 
1. A summary of existing alarms
2. Controls to edit and disable an alarm

The alarms summary shows an overview of each alarm. 

Alarms will be categorized into 4 groups: Today, Tomorrow, This Week, and Later.
The alarms in each group will be sorted in order of ascending time-to-ring

For each alarm, the following attributes will be shown:
- Alarm time in local time zone
- Alarm name
- And, **if the alarm is not disabled**:
  - Date on which alarm will next ring
  - Countdown to next occurrence
    This will be a "fuzzy" field.
    If the period is >= 1 d then the label will read "N d, M h"
    else if the period is >= 1 h then the label will read "N h"
    else if the period is < 1 h then the label will read "N min".
    The values of N and M will be calculated using bankers rounding.
    N and M will be integer values with zero decimals
    if the value M is zero then it will not be printed
    The labels "d", "h", "min" locale-independent labels will be
    used as they are accepted non-SI units per NIST SP 330.

If the alarm changes state from disabled to enabled
then the alarm summary panel will refresh

### Alarm Details Page
The alarm details page shows the alarm attributes:
- Name
- Alarm time
- Time zone, optional
- Recurrence, or
- Fixed date
- Specific ringtone
- Specific vibration setting

Additionally, a button will be available to delete the alarm.
If the user chooses the Android back button, then changes to the
alarm settings will be saved.

### Alarm Ringing Page
The alarm ringing page is displayed when an alarm rings.

This page should activate over the lock screen if the phone
is locked or on top of other apps if the phone is unlocked.

Usability is a primary concern for this page.
It will be common for users to have reduced visibility 
and congnition when an alarm is activated. The alarm time
and controls to stop or pause (snooze) the alarm should be
easy to read in low light conditions. 
Importantly, it should be difficult 
to accidentally stop an alarm 
when the intent is to pause an alarm.

### App Settings
The app setting page allow the user to modify 
settings that apply to all alarms.
The settings are saved in local storage 
whenever the settings page is closed
and loaded whenever the app is opened

The app settings are:
- Home (aka default) time zone
- Snooze period
- Max snooze count
- Default ring tone
- Alarm volume
- Alarm ringing volume escalation 
- Default vibration setting
- 12-/24-hour times

## Non-functional requirements
- Works even when phone is on silent/Do Not Disturb — this is the #1 complaint people have with alarm apps
- Survives phone restarts, battery optimization killing background processes, and OS updates
- Doesn't depend on internet connectivity to fire
- Battery-efficient (shouldn't drain the phone overnight)

## Functional Requirements
### Alarm Definition
The only temporal field required to define an alarm is time. 
If an alarm is defined with only time, 
then the alarm will ring at the next occurrence of that time in the local time zone,
irrespective of whether the time zone of the device changes after the alarm is defined,
or if the next expected occurrence is skipped because of a daylight savings time change.

An alarm may be assigned a time zone. 
In this case, the instant at which the alarm rings is fixed
and independent of the device time zone. 

An alarm may be set to occur at specific days:
1. On a specific date. 
   In this case the user will select the date using a standard date chooser.
2. Or, recurring on specific days or dates. 
  The user will be able to enable recurrence 
  for an alarm and select the pattern:
  - Specific days of every week
  - Specific days of every month
  For example, the user could designate that an alarm occurs at 0900 every Monday, Wednesday, and Friday. 
  Or at 1300 on the first and fifteenth day of every month. 

If an alarm is disabled then it will not ring.

### Alarm ring workflow
When an alarm rings, the alarm rining page will be displayed.
The alarm ringing page displays the current time, time zone, date,
and name of alarm.
The device will play the alarm ring tone at the alarm volume.
If the user selected vibration for this alarm then the device will vibrate if capable.

The user must choose to dismiss the alarm or to snooze.
As discussed earlier, it should be difficult 
to accidentally stop an alarm when the intent is to pause an alarm.

If the user snoozes the alarm then the alarm will be paused for the 
snooze duration. After the snooze duration the alarm will ring again.

If the user snoozes the alarm for the max number of snoozes,
then the user will not have the option to snooze and must dismiss.

After it is dismissed by the user, 
if there are no future occurrences of the alarm (i.e. if the alarm is not recurring)
then the alarm will change to a disabled state 

## Phase 2
Work for the next project phase:
- The alarm time for different days of a recurring alarm can be set independently.
  So, for example, the alarm may ring at 0900 on Tuesday and 1000 on Wednesday.
  This would also allow interesting possibilities 
  like defining the same alarm at different times in the same day
- Alarms are skipped when they occur on public holidays of some selected region.
  The selected region for public holidays may be different than the time zone
  of the alarm. A source for public holiday data would need to be investigated.
- An end date for alarm recurrence. Adding this setting will effectively
  allow a single-shot alarm to be a special case of a multi-shot alarm 
  and should allow the code to be simplified.
- Device screen transitions to "morning" brightness 
  in the minute before an alarm rings.
