# NEON DUEL — Offline 1v1 Bluetooth Arena

Original two-phone Android game with Bluetooth Classic RFCOMM networking.

**Start with `START_HERE_BN.md` for Bangla setup instructions.**

### Requirements

- Two Android phones supporting Bluetooth Classic, Android 8.0+ (API 26+)
- Android Studio, compile SDK 35, Gradle 8.7, Java 17+
- Internet on the **development computer** the first time Android Studio downloads dependencies (not needed while playing)

This source project does not include an APK. The Gradle wrapper JAR is not bundled: on Windows, use `SETUP_WRAPPER_WINDOWS.bat` to fetch and SHA-256 verify the official Gradle 8.7 wrapper JAR. Then open the root folder in Android Studio and build an APK.

### Gameplay

Host = cyan (P1). Guest = pink (P2). A round lasts 45 seconds; 5 HP each. Hold FIRE, drag joystick to move, tap DASH to dodge (3.3s cooldown). Shots auto-aim, obstacles block movement and projectiles, green pickups restore up to 2 HP, gold pickups add shields. Damage doubles in the final 10 seconds. If the round timer expires, higher HP wins; a tie awards no point. First to 3 winning rounds takes the match, then either phone can request a new match.

### Connection and privacy

App requests only appropriate Android Bluetooth permissions (plus location permission for discovery on Android 11 and below). It does **not** request internet permission and does not upload gameplay data or collect analytics. Nearby devices must consent to pairing. Host socket service ID is constant and shared by both copies of the APK.

For network reliability, the host runs authoritative physics on the main thread at approximately 30 ticks/second. The guest transmits input commands at ~30Hz; host transmits world snapshots at ~15Hz. Bluetooth communication uses dedicated reader/writer threads and bounded message queues.

### Project files

`app/src/main/java/com/jahid/neonduel/{MainActivity,BluetoothLink,GameEngine,GameWire,ArenaView}.kt`

### Verification

The standalone `GameEngine.kt` was compiled and tested with local Kotlin/JVM smoke tests. A full Android build and real device-to-device Bluetooth interoperability were **not** tested in this environment because Android SDK and physical handsets are unavailable. Please test on two Android handsets before releasing or publishing.
