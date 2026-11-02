# Torch Shake

A simple Android flashlight app (Kotlin, XML layouts, Material 3).

- **Normal** torch (stays on)
- **Blink** torch at Fast (150 ms), Medium (500 ms) or Slow (1000 ms)
- **Shake to toggle**, Motorola-style: chop the phone twice, even with the screen off
- Adjustable shake sensitivity

## How to run

1. Install Android Studio and open this folder (`TorchShake`).
2. Let Gradle sync. If it offers to upgrade the Android Gradle Plugin, accepting is fine.
3. Connect your phone (USB debugging on) and press Run. A real phone is needed: emulators have no flashlight.

## How it works

| File | Job |
|------|-----|
| `TorchManager.kt` | Owns the torch. Normal/blink logic, speeds, saved settings. Shared by the screen and the service. |
| `ShakeDetector.kt` | Reads the accelerometer; two sharp spikes within 700 ms count as one chop. |
| `TorchService.kt` | Foreground service that keeps listening when the app is closed or the screen is off. |
| `MainActivity.kt` | The screen: torch button, mode, speed, shake switch, sensitivity. |

## Making shake work reliably

Shake with the screen off needs the foreground service, which shows a notification.
Some phones (Xiaomi / POCO, Oppo, Vivo...) kill background apps aggressively. If shake stops working:

1. Tap **Battery settings** in the app and set Torch Shake to *unrestricted / don't optimise*.
2. In the phone's App info for Torch Shake, turn on **Autostart** and set battery saver to **No restrictions**.
3. Lock the app in the Recents screen (long-press the card, then lock).

## Tuning

- Too many false triggers: lower the sensitivity slider, or raise `REQUIRED_PEAKS` in `ShakeDetector.kt`.
- Not triggering: raise the slider.
- Blink speeds: edit the `BlinkSpeed` enum in `TorchManager.kt`.

## Ideas for next steps

- Quick Settings tile
- Home-screen widget
- SOS (morse) blink pattern
- Auto-off timer
