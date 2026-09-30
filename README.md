# Expenses & Notes — Shake to Open

## Run it
1. Open this folder in Android Studio (Koala or newer). Let it sync Gradle.
2. Plug in your phone (USB debugging on) and press Run.
3. In the app, tap **Grant permission** and allow "Display over other apps".
4. Close the app, shake your phone twice firmly — it opens.

## How shake-to-open works
- `ShakeService` is a foreground service (persistent notification) that listens to the accelerometer.
- `ShakeDetector` fires after 2 jolts > 2.7g within 1 second. Lower `THRESHOLD_G` for more sensitivity.
- Android 10+ blocks apps from opening themselves from the background; the
  "Display over other apps" permission is what allows it.
- `BootReceiver` restarts the listener after reboot.

## Limits
- Many phones pause sensors when the screen is off, so it works best with the screen on/unlocked.
- Some brands (Xiaomi, Oppo, Vivo, Huawei, Samsung) kill background services. Set battery
  usage for this app to "Unrestricted" / disable battery optimization.
- Google Play reviews `specialUse` foreground services; fine for personal use / sideloading.
