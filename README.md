# Bubbly Calc - Android

A native Kotlin + Jetpack Compose port of the Windows app. No Android Studio required to build it.

## Honesty check first

I have no Android SDK, emulator, or Kotlin compiler available in the environment I wrote this in -
only a plain Java runtime with no compiler at all. I carefully hand-reviewed this code multiple times
and fixed several real bugs I found that way (a dead button-press animation, a broken shake effect, a
scoping error, a missing-dependency icon, a main-thread network call), but **none of it has actually
been compiled**. Treat the first build the way we treated the first Windows build: there's a real
chance of a compile error on the first attempt. If that happens, copy the full error from the Actions
log (or Android Studio's "Build" panel) and send it back - that's enough to fix precisely, the same way
we iterated on the Windows version.

## Building with zero local installation (recommended)

1. Create a new repository on GitHub (can be private).
2. Upload every file in this folder, keeping the folder structure exactly as-is (including the
   hidden `.github` folder - make sure your upload method doesn't skip dotfiles).
3. GitHub will automatically run the included workflow and build a debug APK.
4. Go to the repo's **Actions** tab -> click the latest run -> download the `bubbly-calc-debug-apk`
   artifact at the bottom of the page. That's a `.apk` you can copy to your phone and install
   (you'll need to allow "install from unknown sources" for whichever app you use to open it).

If the build fails, click into the failed step to see the full log and send me the error text.

## Building locally instead (if you'd rather)

You don't need the Android Studio *IDE* - just its command-line SDK tools and a JDK 17:

```
sudo pacman -S jdk17-openjdk android-tools   # Arch; package names vary by distro
```

You'll also need the Android SDK's command-line tools and at least one platform installed via
`sdkmanager` (search "android sdkmanager cmdline-tools install" for your distro's exact steps -
this part genuinely does need the SDK itself, just not the IDE). Once `ANDROID_HOME` is set:

```
gradle wrapper --gradle-version 8.7   # generates the missing gradlew files, one time
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## What's included

Full standard + scientific calculator (same parser/grammar as Windows and Linux), the four color
themes, history, mute toggle, sound via `SoundPool` (Android's own API for many overlapping short
sounds - should behave well under spam by design), the unit/currency converter modes with the same
offline fallback rates as Windows, and a live-rate refresh when online.

## Known differences from the Windows version

- No window chrome obviously (it's a phone screen, not a desktop window) - no minimize/close buttons.
- The "history drawer" and "mode picker" are bottom sheets rather than the Windows sliding overlay -
  closer to how Android apps normally present this kind of panel.
- Button press animation uses Compose's spring physics (genuinely similar bounce to the WPF version,
  unlike the Linux/GTK port which couldn't do this at all).
