from pathlib import Path
import xml.etree.ElementTree as ET

p = Path(__file__).resolve().parents[1]
m = ET.parse(p / "app/src/main/AndroidManifest.xml").getroot()
app = m.find("application")
a = "{http://schemas.android.com/apk/res/android}"
assert app.get(a + "label") == "Friends Zone"
assert app.get(a + "icon") == "@mipmap/ic_launcher"
assert (p / "app/src/main/res/mipmap-nodpi/ic_launcher.png").is_file()
assert (p / "app/src/main/res/drawable-nodpi/friends_zone_banner.jpg").is_file()
main = (p / "app/src/main/java/com/jahid/neonduel/MainActivity.kt").read_text()
assert 'label("FRIENDS",' in main and 'label("ZONE",' in main
assert 'FRIENDS ZONE — HOW TO PLAY' in main
assert 'FRIENDS ZONE — WI-FI ROOM' in main
assert "friends_zone_banner" in main
w = (p / ".github/workflows/build-apk.yml").read_text()
assert "Friends-Zone-Debug-APK" in w
print("Friends Zone branding / manifest / artwork / workflow smoke tests passed")
