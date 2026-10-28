# NEON DUEL — Bluetooth Multiplayer Android Game

এই ZIP-এ একটি **native Android Studio Kotlin game project** আছে। এটি সরাসরি install করার APK নয়। Android Studio-তে build করে APK বানাতে হবে।

## গেমে কী আছে?

- **দুইটি Android ফোনে Bluetooth Classic multiplayer**, Wi-Fi বা ইন্টারনেট ছাড়াই খেলা
- 1v1 neon arena battle; moving joystick, hold-to-fire, auto-aim
- Dash করে attack dodge; 3.3-second cooldown
- Green health pickup এবং gold shield pickup
- 45-second round; শেষ 10 সেকেন্ডে **double damage**
- 5 HP প্রতি round, 3 round জিতলে ম্যাচ জয়
- Rematch, scoreboard, Bluetooth reconnect/quit flows
- Original code-drawn graphics, external image assets বা account লাগে না

## Windows PC-তে APK বানানোর নিয়ম

1. ZIP-টি Extract করো।
2. [Android Studio](https://developer.android.com/studio) ইনস্টল করো (Android SDK এবং Android SDK Platform 35 সহ)।
3. ইন্টারনেট কানেকশন রেখে `SETUP_WRAPPER_WINDOWS.bat` একবার চালাও। এটা Gradle-এর অফিসিয়াল distribution server থেকে wrapper JAR নামিয়ে SHA-256 যাচাই করবে।
4. Android Studio → **Open** → `NeonDuelBluetooth` ফোল্ডারটি নির্বাচন করো (ভেতরের `app` ফোল্ডার নয়)।
5. Gradle sync শেষ করো; দরকার হলে Gradle JDK হিসেবে **Embedded JDK 17 বা 21** নির্বাচন করো।
6. **Build → Build Bundle(s) / APK(s) → Build APK(s)** ক্লিক করো।
7. Debug APK এই path-এ পাওয়া যাবে: `app/build/outputs/apk/debug/app-debug.apk`
8. APK-টি দুইটি Android ফোনে install করো। Unknown sources installation permission লাগতে পারে।

প্রথমবার PC-তে Gradle ও Android build components download করার জন্য ইন্টারনেট লাগবে। **Game খেলার সময় ইন্টারনেট লাগবে না।**

## দুই ফোনে কানেক্ট করে খেলবে যেভাবে

1. দুটি ফোনেই Bluetooth চালু করো। দুই ফোনে একই APK install থাকতে হবে।
2. প্রথম ফোনে **HOST A MATCH** চাপো, Nearby devices permission দাও এবং discoverable হতে বললে Allow করো।
3. দ্বিতীয় ফোনে **JOIN A MATCH** চাপো, প্রথম ফোনের নাম দেখে নির্বাচন করো। নাম না পেলে **SCAN NEARBY PHONES** চাপো।
4. Android pairing confirmation আসলে দুই ফোনে Pair/Allow করো।
5. কানেক্ট হলে দুই ফোনেই arena খুলবে। বাম joystick দিয়ে চলবে; ডান **FIRE** চেপে ধরলে গুলি যাবে; **DASH** একবার চাপলে দ্রুত এগোবে।
6. একজন তিনটি round জিতলে **PLAY AGAIN** দিয়ে নতুন ম্যাচ শুরু করবে।

**কানেকশন না হলে:** Android Bluetooth Settings-এ গিয়ে ফোন দুটি আগে Pair করো। Host যেন game-এ HOST screen খুলে রাখে; তারপর guest JOIN থেকে paired phone নির্বাচন করো। অন্য Bluetooth app-এ কানেক্ট হলে Disconnect করো। Airplane mode বন্ধ রাখো। Android 11 বা তার নিচে scanning-এর জন্য Location permission এবং Location toggle দরকার হতে পারে।

## সীমাবদ্ধতা ও সতর্কতা

- এটি **prototype/source project**। এই পরিবেশে Android SDK ও দুইটি physical Android device ছিল না, তাই সম্পূর্ণ APK build, Bluetooth hardware connection ও দুই ফোনের gameplay **পরীক্ষা করা সম্ভব হয়নি**।
- Pure Kotlin game engine-এর local simulation/snapshot/rematch smoke tests পাস করেছে।
- Bluetooth Classic (RFCOMM) দরকার; BLE-only device, iPhone/iOS, web browser, বা দূর থেকে internet multiplayer সমর্থিত নয়।
- Host ফোন gameplay physics নির্ধারণ করে; guest state sync পায়। Connection বিচ্ছিন্ন হলে ম্যাচ পুনরায় শুরু করতে হবে। Background-এ game পাঠিয়ে দিলে সংযোগ স্থির থাকবে এমন নিশ্চয়তা নেই।

## Code structure

- `MainActivity.kt`: Menu, permission handling, host/join, device discovery, connection UI, game loop
- `BluetoothLink.kt`: Bluetooth Classic socket threads, read/write, disconnect handling
- `GameEngine.kt`: Original Android-independent game simulation
- `GameWire.kt`: JSON protocol (input/state/rematch)
- `ArenaView.kt`: Canvas-based neon arena rendering, HUD, joystick, buttons, game-over screen
- `tests/EngineSmokeTest.kt`: Pure Kotlin test without Android dependencies

## Game logic test (optional)

PC-তে `kotlinc` থাকলে project root থেকে:

```bash
kotlinc app/src/main/java/com/jahid/neonduel/GameEngine.kt tests/EngineSmokeTest.kt -include-runtime -d engine-test.jar
java -jar engine-test.jar
```
