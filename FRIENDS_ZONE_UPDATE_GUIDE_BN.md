# Friends Zone — Android game rename + poster + icon (V4)

এই আপডেটে গেমের visible নাম **Friends Zone**। Main Menu, Wi-Fi Room, HOW TO PLAY, Android app launcher নাম, নতুন icon ও game poster যুক্ত করা হয়েছে। আগের package `com.jahid.neonduel` অপরিবর্তিত—এতে আগের APK-এর সঙ্গে একই app identity থাকে।

## VS Code দিয়ে GitHub-এ আপডেট

তোমার আগের VS Code project folder খুলবে (যেখানে `.git` আছে)। `Friends_Zone_V4_Update.zip` Downloads-এ রাখবে।

```powershell
Expand-Archive -Path "$env:USERPROFILE\Downloads\Friends_Zone_V4_Update.zip" -DestinationPath "." -Force
git status
git add .
git commit -m "feat: rebrand game as Friends Zone with artwork"
git push origin main
```

`Actions`-এ `Build Friends Zone Android APK` successful হলে `Friends-Zone-Debug-APK` artifact ডাউনলোড করে ZIP extract করো। ফোনে `app-debug.apk` install করো।

## কী পরীক্ষা হয়েছে এবং কী হয়নি

* Source-এর Kotlin offline bot/engine tests এবং 2–10-player FFA/duo-team tests চালানো হয়েছে।
* Hotspot connection বাস্তব ২–১০টি Android ফোনে যাচাই হয়নি।
* Android SDK না থাকায় এখানে full APK build সম্ভব হয়নি। GitHub Actions build-ই full compile পরীক্ষা করবে।
* এই poster promo artwork; এতে যে environment/weapon দেখা যাচ্ছে, সবগুলো implementation হিসেবে ধরে নেওয়া যাবে না।

## Recommended device test checklist

1. এক ফোনে offline bot ৫ মিনিট খেলে shooting, dash, score, rematch দেখো।
2. Host ফোন hotspot on করে Wi-Fi Host mode open করো।
3. দ্বিতীয় ফোন host hotspot join করে host-এর IP দিয়ে Connect করো।
4. ২ জনের FFA-তে shoot, movement, hit/score, winner দেখো।
5. এরপর ৪, ৬, ৮, ১০ জনের যোগ দেওয়া, disconnect/reconnect, load/lag পরীক্ষা করো।
6. একই টিমে friendly fire বন্ধ আছে কি না এবং team size ২-এর বেশি হচ্ছে কি না দেখো।
7. ছোট-বড় map, day/sunset/night display, menu icon সব পরীক্ষা করো।

প্রকৃত device tests ছাড়া 100% flawless বলা সম্ভব নয়।
