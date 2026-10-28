# NEON DUEL — দুই ফোনে Bluetooth দিয়ে 1v1 খেলার APK বানানোর নিয়ম

**এটি APK নয়; Android source project।** এখানে Android SDK না থাকায় আসল Bluetooth APK compile করা যায়নি।

## Android Studio ছাড়া GitHub-এ APK build

1. ZIP extract করো; `NeonDuelBluetooth` ফোল্ডারের ভেতরের ফাইলগুলো (লুকানো `.github` সহ) GitHub-এর নতুন repository-তে push/upload করো।
2. Repository-তে গিয়ে **Actions** → **Build Bluetooth Android APK** → **Run workflow**। `main`/`master`-এ push করলেও build শুরু হবে।
3. Build successful হলে run page-এর **Artifacts** থেকে `NeonDuel-Bluetooth-Debug-APK` download করো। ZIP খুলে `app-debug.apk` পাবে।
4. একই APK দুইটি Android ফোনে install করে Bluetooth চালু করো। প্রথম ফোনে **HOST A MATCH**, দ্বিতীয় ফোনে **JOIN A MATCH**। প্রয়োজনীয় Bluetooth permission ও pairing accept করো।

## Caveats

- GitHub build সফল হবে কি না এখান থেকে যাচাই করা যায়নি। Build error হলে Actions-এর log দেখে code ঠিক করতে হবে।
- দুইটি Android ফোনে Bluetooth সংযোগ, controls ও gameplay এখনও বাস্তবে পরীক্ষা করা হয়নি।
- Phone-এ Play Store থেকে না আসা APK install করার আগে APK-র উৎস সম্পর্কে নিশ্চিত হও।
- Bluetooth Classic support দরকার; mobile data বা Wi-Fi ছাড়াই খেলা পরিকল্পিত।
