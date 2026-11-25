# Night City Holo-OS

An Android customization suite that turns a stock (non-rooted) phone into a neon "Kiroshi optics"
HUD. Android doesn't let third-party apps restyle the OEM system UI, so the app combines four pieces:

| Module | What it does |
| --- | --- |
| **A. Neural Link Calibration** | A one-click setup wizard. It walks through every permission and special access in order, re-checks each one when you come back from the system screen, then injects the sound pack. |
| **B. Audio & haptics engine** | Seven audio slots. It copies cues into `MediaStore` with ringtone/notification/alarm flags and sets the defaults through `RingtoneManager`. It plays per-app cues from a `NotificationListenerService`, plus charger and low-battery cues with staccato `VibrationEffect` waveforms. |
| **C. HUD launcher + live wallpaper** | A full home-screen replacement: Kiroshi chrono and telemetry panel, Holocall contacts widget, chamfered dock and an app drawer. Every app icon gets a duotone filter, so no icon pack is needed. Ships with a Canvas live wallpaper (rain, skyline parallax, unlock glitch). |
| **D. Manual overrides** | Deep links into the settings Android keeps sandboxed (keyboard, display/AOD, sound, battery optimization…). Includes hex copy buttons and a generated keyboard backdrop for Gboard's image themes. |

Stack: Kotlin 2.1, Jetpack Compose (Material 3 with custom HUD components), Hilt, Coroutines/Flow,
DataStore, MVVM + Clean Architecture. minSdk 29, targetSdk 35.

---

## Build

Requirements: JDK 17+ and the Android SDK (platform 35). Android Studio Ladybug or newer works,
or use the command line:

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew assembleRelease        # R8-minified; signed with the debug key until you add your own
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Drop in your audio (before building)

Put files in `app/src/main/res/raw/` using these **exact base names**. Any of
`.ogg .mp3 .wav .m4a .flac` works. Use one file per slot: two extensions for the same name is a
duplicate-resource build error.

| Slot | Filename | Used as |
| --- | --- | --- |
| Holocall Ringtone | `cue_holocall_ringtone.ogg` | system default ringtone |
| SMS / Notification Chirp | `cue_notification_chirp.ogg` | system default notification sound |
| Eddie Transfer | `cue_eddie_transfer.ogg` | banking/payment app notifications |
| New Gig / Priority Email | `cue_new_gig.ogg` | (priority) email notifications |
| Relic Malfunction | `cue_relic_malfunction.ogg` | battery drops below 15% |
| Neural Link Connected | `cue_neural_link.ogg` | charger plugged in |
| V's Apartment Alarm | `cue_apartment_alarm.ogg` | system default alarm + "Schedule alarm" |

The same checklist is in [`res/raw/audio_slots_todo.txt`](app/src/main/res/raw/audio_slots_todo.txt).

Every slot is optional. Each cue resolves through a fallback chain:

1. **Imported at runtime** (Audio tab → Import; the file is copied into app storage)
2. **Bundled** in `res/raw`
3. **Synthesized placeholder**: an original tone generated on-device, so the app builds and works with no audio at all

Cues are looked up by name at runtime, so missing files never break the build.
`res/raw/keep.xml` stops the release resource shrinker from stripping them. Only bundle audio you
have the rights to use.

## First run

1. Open **Holo-OS** → **Calibrate** → **Initiate one-click calibration**. The wizard opens each
   system screen in turn (notification access, Modify system settings, overlay, contacts, live
   wallpaper, default home). Back out of any optional step to skip it.
2. Once **System write access** is online, the sound pack is injected automatically. You can also
   use Calibrate → *Inject sound pack* or Audio → *Apply to system*.
3. Press Home: the HUD is now your launcher. Swipe up (or tap **NET ACCESS**) for the app drawer.
   The gear icon reopens the hub.
4. Work through the **Overrides** checklist for the settings an app can't change.

## Architecture

```
com.nightcity.holoos
├── domain/                 Pure Kotlin, no android.* imports
│   ├── model/              AudioCue, CueSource, HapticPattern, CalibrationStep, AppEntry, …
│   ├── repository/         Interfaces (URIs cross the boundary as Strings)
│   ├── logic/              NotificationCueClassifier, CueThrottle, LowBatteryAlarm (unit-tested)
│   └── usecase/            PlayCue, ApplySoundPack, PrepareAlarmTone, HandleNotification
├── data/                   Android implementations
│   ├── audio/              MediaStore exporter, RingtoneManager, MediaPlayer, haptics, tone synth
│   ├── apps/               LauncherApps (all profiles), IconStylizer (ColorMatrix duotone), icon cache
│   ├── permissions/        Live permission/role/special-access status
│   ├── system/ contacts/   Battery + RAM telemetry, starred-first contacts
│   ├── preferences/        DataStore
│   └── overrides/          Keyboard backdrop renderer
├── service/
│   ├── notification/       HoloNotificationListener (+ app notification channel)
│   ├── power/              PowerEventMonitor (charger / low battery)
│   ├── overlay/            Relic Malfunction banner (SYSTEM_ALERT_WINDOW)
│   └── wallpaper/          HoloWallpaperService + NightCityRenderer
├── feature/                Compose screens + @HiltViewModel per screen
│   ├── setup/ audio/ overrides/ hub/
│   └── launcher/           LauncherActivity (CATEGORY_HOME), HUD, drawer
├── core/designsystem/      HoloColors, typography, ChamferShape/HexagonShape, HudPanel, HudButton,
│                           neonBorder, glitch, ScanlineOverlay, SegmentedBar
└── di/                     Hilt modules
```

It's a single Gradle module with layers enforced by package. The domain layer has no Android
dependencies, so it can be split into its own module later without changes.

## Platform notes and limits

- **Power cues use runtime receivers.** `ACTION_POWER_CONNECTED` / `ACTION_BATTERY_LOW` /
  `ACTION_BATTERY_CHANGED` are not delivered to manifest receivers on API 26+.
  `PowerEventMonitor` registers them at runtime. The system keeps the notification listener bound,
  which keeps the process (and the receiver) alive. On aggressive OEMs (Xiaomi, Samsung, Huawei),
  also set battery optimization to *Unrestricted* (Overrides tab).
- **Relic Malfunction fires at < 15%.** It uses its own threshold with hysteresis, because OEMs
  fire `ACTION_BATTERY_LOW` anywhere from 15% to 20%. It fires once per discharge and re-arms on
  charge or at 20%.
- **Eddie Transfer layers over the bank app's own sound.** A listener can't silence another app's
  notification. For a clean cue, set that app's notification channel to silent in system settings.
  Banking apps are detected by package name; edit the list in Audio → *Eddie trigger apps*.
- **New Gig** fires for alerting email notifications. Add priority keywords to restrict it.
- **Gboard themes take an image, not hex codes.** Overrides → *Render backdrop* saves a HUD
  backdrop to `Pictures/NightCityHoloOS`.
- **Live wallpapers usually don't apply to the lock screen** on stock Android. Set a static one
  for it.
- **System keyboard and Always-On Display** can't be changed by third-party apps. Those are
  deep-link checklist items.
- **Reinstalling** loses ownership of previously exported MediaStore files. Re-applying creates
  fresh copies.
- **Play Store:** `WRITE_SETTINGS`, overlay and notification-listener use all need policy
  declarations if you ever publish.

## Trademarks and assets

This is an unofficial fan customization project, not affiliated with or endorsed by CD PROJEKT.
"Cyberpunk 2077" and in-universe names used as UI labels (Kiroshi, Militech, NetWatch, Relic,
eddies…) are trademarks of their owners. No game assets are included. All visuals are drawn
procedurally, the app icon is an original design, and the placeholder sounds are synthesized
on-device. Every label lives in `res/values/strings.xml`, so the whole suite can be renamed in
one place before you share it anywhere.

Fonts: Orbitron, Rajdhani and Share Tech Mono, bundled under the SIL Open Font License 1.1
(see `third_party/fonts/`).
