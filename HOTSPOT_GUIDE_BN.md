# FRIENDS ZONE V3 — Wi-Fi Hotspot গেম চালানোর নিয়ম

এই আপডেটে **Free For All ২ থেকে ১০ জন** এবং **Duo Team (প্রতি দলে সর্বোচ্চ ২ জন)** যুক্ত করা হয়েছে। Host নিজেও খেলবে। Team mode-এ মোট ৪, ৬, ৮ বা ১০ জন লাগবে, কারণ প্রত্যেক দলে ২ জন থাকবে।

## আগের GitHub Project আপডেট

VS Code-এ `NeonDuel_2Player_Bluetooth_GitHub_Ready` ফোল্ডার খোলা রাখো। `NeonDuel_WiFi_V3_Update.zip` Downloads-এ রাখো। VS Code Terminal (PowerShell)-এ চালাও:

```powershell
Expand-Archive -Path "$env:USERPROFILE\Downloads\NeonDuel_WiFi_V3_Update.zip" -DestinationPath "." -Force
git add .
git commit -m "feat: add 10-player hotspot free for all and duo teams"
git push origin main
```

GitHub Actions-এ নতুন `Build Friends Zone Android APK` রান সফল হলে Artifacts থেকে `Friends-Zone-Debug-APK` নিয়ে ZIP extract করো, তারপর `app-debug.apk` ইনস্টল করো।

## Host কী করবে

1. Android Settings থেকে নিজের **Mobile Hotspot** চালু করবে।
2. FRIENDS ZONE → `HOST WI-FI — FREE FOR ALL (2–10)` অথবা `HOST WI-FI — DUO TEAMS (2/TEAM)` চাপবে।
3. Host-এর স্ক্রিনে IP address দেখানোর চেষ্টা করা হবে। Host-ও Player 1 হিসেবে খেলবে।
4. অন্যরা lobby-তে যোগ দিলে `SCENERY` বাটনে Day / Sunset / Night বেছে নিতে পারবে।
5. সবাই যোগ দেওয়ার পরে `START MATCH` চাপবে।

## Join কীভাবে করবে

1. নিজের ফোনের Wi-Fi খুলে Host-এর Hotspot-এ connect করবে।
2. FRIENDS ZONE → `JOIN WI-FI / HOTSPOT MATCH` চাপবে।
3. Host IP দিতে হবে। App স্বয়ংক্রিয়ভাবে Wi-Fi gateway IP দেখানোর চেষ্টা করবে; সেটি ঠিক না হলে Host-এর hotspot IP হাতে বসাবে।
4. Player list-এ নাম উঠলে Host game চালু করতে পারবে।

দুটি ফোনেই একই version-এর APK ব্যবহার করবে। ইন্টারনেট বা mobile data দরকার নেই, কিন্তু local Wi-Fi network চালু থাকতে হবে। কিছু ফোনের hotspot-এ guest-to-host connection বাধা পেতে পারে, অথবা gateway IP ভিন্ন হতে পারে।

**পরীক্ষার অবস্থা:** game logic-এর automated test এবং একটি কম্পিউটারে একাধিক local network connection-এর test পাস করেছে। সত্যিকারের Android phone hotspot, ১০ ফোনের multiplayer এবং APK installation এখনো পরীক্ষা করা হয়নি। Bluetooth পুরোনো connection সমস্যা এ আপডেটে ঠিক করা হয়নি।
