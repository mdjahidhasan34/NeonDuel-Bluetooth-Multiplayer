# NEON DUEL Bluetooth APK — GitHub Actions দিয়ে তৈরি

এই source project-এ Android Bluetooth multiplayer implementation আছে, তবে এখনো ফোনে পরীক্ষা করা হয়নি। Android Studio না থাকলে GitHub-এর cloud runner-এ APK compile করার workflow `.github/workflows/build-apk.yml` দেওয়া আছে।

1. GitHub-এ একটি নতুন empty repository তৈরি করো।
2. এই ZIP Extract করে **ভেতরের সব ফাইল ও `.github` ফোল্ডারসহ** repository-এর root-এ upload করো। `.github` hidden folder যেন বাদ না যায়। Git দিয়ে push করলে সুবিধা।
3. GitHub repository → **Actions** → **Build Bluetooth Android APK** → **Run workflow** (অথবা `main`/`master`-এ push হলে স্বয়ংক্রিয়ভাবে build শুরু হবে)।
4. Build green হলে Actions run-এর নিচে **Artifacts** → `NeonDuel-Bluetooth-Debug-APK` নামের ZIP download করে unzip করো।
5. `app-debug.apk` দুইটি Android ফোনে install করে test করো। ফোন দুটি Bluetooth Classic support করতে হবে।

**বুঝে রাখো:** workflow এই পরিবেশে রান করা হয়নি; সফল build guaranteed নয়। GitHub Action log-এ Kotlin বা Android compile error এলে সেগুলো ঠিক করতে হবে। APK build successful হলেও দুইটি ফোনের Bluetooth connectivity পরীক্ষা করা প্রয়োজন। Test APK (debug) Play Store release-এর উপযোগী নয়।
