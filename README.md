# NEON DUEL — Offline Bot and Bluetooth Battle (Android)

A native Android shooting arena game written in Kotlin. This revision adds **PLAY VS BOT (OFFLINE)** so the game can be tested on one phone without Bluetooth, Wi-Fi, mobile data, or a second player.

## Game modes

- **PLAY VS BOT (OFFLINE)**: choose Rookie, Challenger, or Veteran. Bot moves, shoots, dodges, and chases useful pickups. Joystick / FIRE / DASH controls and first-to-3-rounds matchmaking work as before. Tap PLAY AGAIN after the match.
- **HOST / JOIN over Bluetooth**: retained from the existing prototype. Connection reliability on two real phones is **not yet verified or repaired** in this update.

## Build an APK

The repository contains `.github/workflows/build-apk.yml`. Push this update to `main`; open GitHub Actions → Build Bluetooth Android APK → the latest successful run → Artifacts → NeonDuel-Bluetooth-Debug-APK. Extract the artifact ZIP to obtain `app-debug.apk`.

The Android package id remains `com.jahid.neonduel`. Each GitHub Actions build may use a different ephemeral Android debug signing key. If Android reports that the APK cannot be installed over your old version, uninstall the previous NEON DUEL debug APK first, then install the new one.

## Update locally (PowerShell, inside the existing Git repo)

Download `NeonDuel_Bot_Mode_Update.zip` into Downloads, then run:

```powershell
Expand-Archive -Path "$env:USERPROFILE\Downloads\NeonDuel_Bot_Mode_Update.zip" -DestinationPath "." -Force
git add .
git commit -m "feat: add offline bot battle mode"
git push origin main
```

Do **not** run `git init` again. This update does not require Bluetooth permissions for bot mode. Kotlin-only engine tests run separately from Android builds and cannot prove the Android application works on a device.
