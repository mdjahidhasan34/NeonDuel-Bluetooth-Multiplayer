# Friends Zone V4 — Android multiplayer game prototype

Friends Zone is an Android Kotlin arena shooter with offline Bot practice and host-based Wi-Fi hotspot matches. The host plays as player 1; up to nine guests can connect over a compatible local Wi-Fi hotspot without internet access. Intended modes include 2–10-player free-for-all and two-player teams. The Bluetooth legacy code remains present but its connectivity was not fixed or validated in this update.

## V4 branding changes

- Launcher label **Friends Zone**, branded app icon, and game poster in `app/src/main/res/`.
- Main menu title and in-game Wi-Fi room title updated.
- `versionCode = 4`, `versionName = "1.3"`. The Android `applicationId` remains `com.jahid.neonduel` to retain upgrade identity.
- GitHub Actions artifact: **Friends-Zone-Debug-APK**.

## Build

Push the source files, including `.github`, to GitHub. GitHub Actions runs `gradle --no-daemon :app:assembleDebug --stacktrace`. On a successful build, download the **Friends-Zone-Debug-APK** artifact and extract `app-debug.apk`. Android debug signing may differ between builds; if you get an install/signature conflict, uninstall the older debug APK first (this clears old app data).

## Testing limits

Core Kotlin test files live in `tests/`. A passing desktop smoke test is not a substitute for compiling the app and testing multiple physical Android devices over actual hotspot networks. Network behaviour depends on device manufacturer and Android OS. **No claim of 100% device compatibility.**

See `FRIENDS_ZONE_UPDATE_GUIDE_BN.md` for the Bengali installation, update and QA checklist.
