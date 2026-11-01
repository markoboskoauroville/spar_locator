# Round 1: v2 (f5e93c6) on the Nothing Phone 2a, 1.11.2026 21:28–21:30

Built on Marko's Mac (session "BUILD YOUR APK HERE"), installed over wireless adb.

## Build
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug` (first run, online): **49 s**, exit 0.
- `app/build/outputs/apk/debug/app-debug.apk`: **9,498,975 bytes (9.5 MB)**.
- `local.properties` written with `sdk.dir=~/Library/Android/sdk` (platforms 34 and 35 present); it is gitignored.

## Phone
- Nothing Phone 2a, model A142, product PacmanIND, 1084×2412, serial `192.168.1.103:42925` (adb-0005534BC000140-vG9lQ0).
- First `adb connect` said "No route to host" although the port was open (`nc` succeeded): the adb server had been started by launchd at 02:53 and was blocked by macOS Local Network privacy. `adb kill-server; adb start-server` from the terminal fixed it; no pairing needed.
- `com.mantra.spar` was **not installed**, so no uninstall was needed.

## Screens
- **shot1.png** first screen: map of Croatia, 144 red circles, pill "Croatia 0/144 open 21:28", key row − / pin / +. Data loaded live from spar.hr.
- **shot2.png** City panel: "All of Croatia 144 · 0 open" ticked, cities A–Ž with counts, footer "spar.hr 1.11. 21:28" and "v2". Zagreb is far down (7 swipes); a search box or Zagreb first might help.
- **shot3.png** Zagreb: "Zagreb 0/51 open 21:29", map zoomed on Zagreb. **No hours labels above the circles.** Every store is closed (Sunday 1.11., All Saints, 21:29), so maybe labels show only for open stores; if they should also show when closed, this is a bug.
- **shot4.png** store panel: "SPAR supermarket Zagreb Savska", Savska 58, phone, Mon 26.10.–Sat 31.10. 7–21, Sun 1.11. "–" (bold, today), Route button. Back (keyevent 4) closed it.

## Cutout (portrait)
`mDisplayCutout=DisplayCutout{insets=Rect(0, 126 - 0, 0) ... boundingRect={Bounds=[Rect(0, 0 - 0, 0), Rect(454, 0 - 540, 126), ...]`, `cutoutSpec={M540.2,33C524.1,33...`
(An earlier read, taken while the phone was lying sideways, showed it on the right: insets right 126, Rect(2286, 454 - 2412, 540).)
The top pill sits at y 179–222, below the 126 px cutout, so nothing is hidden by the camera hole.

## Logcat
- `logcat -d -t 400 | grep -iE "AndroidRuntime|FATAL|com.mantra.spar|vtm"`: **0 lines**. Crash buffer empty.
- App's own pid: only warnings, `HWUI: Image decoding logging dropped!` (tile decoding, harmless) and
  `WindowOnBackDispatcher: OnBackInvokedCallback is not enabled ... Set 'android:enableOnBackInvokedCallback="true"'` (predictive back; worth adding to the manifest).

## Anything odd
- The three key-row icons have **no content-description** (uiautomator shows no text for them): TalkBack users cannot tell them apart, and tests have to tap by position (middle key at 543,2160).
- No hours labels on the Zagreb map (see shot3).
