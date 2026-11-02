# TweakDeck

One app for deep customisation of a Realme 10 Pro+ (works on any Android 8+ phone):

| Area | What it does |
|---|---|
| **Live wallpaper engine** | Video wallpapers (loop, sound on/off, paused when hidden), parallax photo that tilts with the phone, animated scenes (aurora, starfield, matrix rain, colour flow), dim, speed, FPS cap, clock overlay with your own `.ttf/.otf` font |
| **System tweaks** | Font size and boldness, refresh rate, animation scales, brightness, screen timeout, touch delay, haptics, colour correction, private DNS and more |
| **Settings explorer** | Every key in the System, Secure and Global tables: search, view, edit, add, undo |
| **ADB command lab** | Builds the exact commands for what only the ADB shell can do: display density, resolution, debloat, permission control |
| **Launcher** | Full home screen: search, grid size, icon size, ADW/Nova icon packs, dock, hide apps, wallpaper shows through |
| **Safety net** | Back up every setting to a file, restore it, one-tap undo of your last change |

## 1. Get the APK

**Option A, no Android Studio (recommended).** Create a GitHub repo, push this folder, open the
**Actions** tab, wait for "Build APK", and download the `TweakDeck-debug-apk` artifact (unzip it to get `app-debug.apk`).

**Option B, on your PC.** Install JDK 17 and the Android SDK (or Android Studio), then:

```
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Install and unlock

With the phone connected and USB debugging on:

```
scripts/setup.sh app-debug.apk        # Windows: scripts\setup.bat app-debug.apk
```

That installs the app and runs the two one-time grants:

```
adb shell pm grant com.tweakdeck.app android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.tweakdeck.app WRITE_SETTINGS allow
```

**Realme UI note:** if `pm grant` says permission denied, open *Settings → Additional settings →
Developer options* and turn on **USB debugging (Security settings)** and **Disable permission
monitoring**, then run it again. Menu names can differ slightly between Realme UI versions.

## 3. Use it

1. Open TweakDeck. The top card should show two green ticks.
2. **Live wallpaper engine** → pick a type → **Apply as my live wallpaper**.
3. **Make TweakDeck my default launcher** if you want the launcher.
4. Take a **backup** before you experiment in the Settings explorer.

## What no app can do (honest limits)

- **Replace the system font itself.** Realme UI only allows that through its Themes store (or root).
  You can change font size and boldness, and use any font on the wallpaper clock.
- **Change density / resolution from inside the app.** Android only lets the ADB shell do that.
  The command lab generates the commands; paste them into your PC terminal.
- **Everything in Realme/ColorOS's own settings app.** Those live in vendor keys that the Settings
  explorer can show and edit, but the system may ignore or reset some of them.
- **Video wallpaper + clock overlay.** The video player owns the surface, so the clock overlay works
  on scenes and parallax photos only.
- **Icon packs:** icon replacement is supported; background/mask shapes from packs are not applied.

## Troubleshooting

- *"Missing permission" toast*: re-run the grant commands in step 2.
- *Wallpaper doesn't appear in the picker*: reinstall, then Settings → Wallpaper → Live wallpapers.
- *A setting change did nothing*: Realme UI may override it; try the Settings explorer or reboot.
- *Something broke*: Settings explorer → menu → **Undo last change**, or restore your backup.
- *Build error*: send me the full Gradle log and I'll fix it.
