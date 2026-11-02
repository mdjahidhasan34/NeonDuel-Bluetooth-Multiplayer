# NEON DUEL: Offline Bot Mode Update

This update adds PLAY VS BOT (OFFLINE) on the main menu. A second phone and Bluetooth permissions are not required for this mode. Select ROOKIE, CHALLENGER, or VETERAN, then move with the joystick, hold FIRE, and press DASH. First to three rounds wins; PLAY AGAIN restarts.

Bluetooth HOST / JOIN is still in the app, but connection bugs have not been fixed or verified yet.

## Apply to your EXISTING VS Code project

1. Download `NeonDuel_Bot_Mode_Update.zip` and save it to Windows Downloads.
2. Keep VS Code open in `NeonDuel_2Player_Bluetooth_GitHub_Ready`, the folder that already has Git set up.
3. Open the VS Code terminal and run these commands:

```powershell
Expand-Archive -Path "$env:USERPROFILE\Downloads\NeonDuel_Bot_Mode_Update.zip" -DestinationPath "." -Force
git add .
git commit -m "feat: add offline bot battle mode"
git push origin main
```

4. GitHub Actions builds the new Android debug APK.
5. Download `NeonDuel-Bluetooth-Debug-APK` from Artifacts, extract it, and install `app-debug.apk`.
6. If Android says 'App not installed' due to a signature conflict, uninstall the prior debug version and install this one.

If the new build fails, open the failed GitHub Actions job and send the last red error lines.
