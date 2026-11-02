# Handover — Spar Locator

## DM Finder and the GitHub builds (2.11.2026, phone session)

- **Two flavours** (`app/build.gradle.kts`, dimension `shop`): `spar` (com.mantra.spar, appVersion)
  and `dm` (com.mantra.dmfinder, dmVersion, "DM Finder"). Same code; each flavour has its list URL,
  site name, built-in list (`src/<flavour>/assets`) and icon template (`src/main/icon`, `src/dm/icon`).
- **dm's data**: `https://store-data-service.services.dmtech.com/stores/bbox/46.6,13.4,42.3,19.5?countryCode=HR`,
  the service behind dm.hr's store finder: 179 stores, 76 kB gzipped, week (weekDay 1=Mon…, Sunday
  absent = shut), extraClosingDates (holidays), extraOpeningDays (open Sundays), closingDate.
  `Stores.parse` tells the shapes apart (dm: an object with "stores"). Tests: 5 dm cases.
- **Mac: the build command changed.** `./gradlew assembleSparDebug` for Spar Locator
  (`assembleDebug` now builds both). Outputs under `app/build/outputs/apk/spar/`.
- **GitHub** (`build-gh-edition.yml`, on push): Spar GH (com.mantra.spar.gh) as release
  `spar-gh-vN`, DM Finder (com.mantra.dmfinder.gh) as `dm-vN`, GitHub's own key
  (secret SPAR_GH_KEYSTORE). `build-apk.yml` (Mac-key fallback) now builds the spar flavour.

## v3 (2.11.2026, phone session) — request 2 done in code, NOT YET COMPILED

Marko asked again on the phone: "the pin between plus and minus shows my current location, instead
of the list of cities." Done on this branch as `appVersion=3`:
- Pin key → location permission (asked on the first press only) → last known place at once, then a
  fresh fix (`LocationManagerCompat.getCurrentLocation`, fused → GPS → network, no Google Play);
  blue dot (`Marks.me`) on its own layer above the stores; map flies there at zoom 14 (`ME_ZOOM`).
  Pin key lit amber while it looks. Location off → toast + the system's location settings.
- City panel, `Face.City`, the saved city and the globe icon removed; the top line is all Croatia.
- The origin line and the version link moved to the bottom of the store panel.
- verify.py: check 8 allows the two location permissions, check 5 needs one panel. 47/47 pass.
**Not compiled**: the phone has no Android toolchain and CI must not race the Mac's key. Next: the
Mac builds (`assembleDebug`, lint), installs on the Nothing, Marko presses the pin.
Requests 1 and 3 and the round 1 fixes are still open.

## PARKED (1.11.2026, about 20:40) — start here

**State:** v2 (`appVersion=2`) is the code on branch `ccr-15fc9f4b-bi08v4`. v1 was built by GitHub
Actions (release v1, throwaway key). v2 is being built on Marko's Mac; round 1's results, whatever
was finished, are in `testing/round1/` (see ROUND 1 below). No v2 release exists yet.

**How the work is split** (Marko, 1.11.2026), the three sessions:
- **cloud session** (this one, "Spar locator map"): writes the code, coordinates, builds nothing.
- **Mac, "BUILD YOUR APK HERE"**: clone at `~/Developer/spar_locator`; builds with
  `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug`, installs over wireless adb on
  the **Nothing Phone 2a** (`_adb-tls-connect` instance `adb-0005534BC000140-…`; resolve with
  `dns-sd -L`, then `adb connect`). **Not** the Pixel 7 on USB (41241FDH2S10SJ). Screenshots go to
  `testing/roundN/` on the branch.
- **phone, "im markos nothing phone 2a"**: Marko's voice (`talk say --en`) and the real-network
  tester. Proved 1.11.2026: spar.hr answers from the phone's network (200, 617092 bytes).
- The cloud session reaches both with send_message (Marko allowed it 1.11.2026).

**Round 1 (Mac, 21:28, `testing/round1/`):** v2 built in 49 s (9.5 MB debug), installed on the
Nothing, no crash, live data from spar.hr, nothing under the camera hole. Found:
- **No hours labels on the Zagreb map** (shot3). Every store was shut (All Saints, 21:29), but labels
  must show for shut stores too ("–" or the day's hours): check `SparMap` (LABEL_ZOOM 12 vs the zoom
  the city fit lands on) and fix.
- The key-row icons have no content description (TalkBack, and tests tap by position).
- Add `android:enableOnBackInvokedCallback="true"` to the manifest (predictive back).

**Marko's three requests** (word for word in `momentaryupdates.md`, 21:40–21:50):
1. **INTERSPAR different**: a different size AND colour per store size; the colour of the inside
   ring still shows open (green) or shut (red).
2. **The pin key = my location by GPS.** The City list goes. Needs the location permission
   (verify.py check 8 allows only INTERNET today: change it with the reason).
3. **An icon in SPAR's look** (their red and green, round), not their fir-tree logo copied, because the
   APK is public; the real logo only if Marko decides so.

**Next session, in order:**
1. Do the round 1 fixes and Marko's three requests; raise appVersion; verify.py, Test 1, lint,
   compileReleaseKotlin; push.
2. Mac: pull, build, install on the Nothing, screenshots (first screen, City panel, Zagreb with
   labels, a store panel), camera-hole line, logcat.
3. Phone: read Marko the hand test, one step at a time (pick Zagreb, tap a store, Route, back).
4. When Marko says good: the Mac releases `vN` as `N-spar-locator-vN.apk`, `--latest`, keeps two.

## State (1.11.2026, v1)

An Android app replacing the first web version (index.html on GitHub Pages, removed in this change).
Built after Mantra ARKOD (`markoboskoauroville/mantra_arkod`) and to MANTRA_MANIFEST: Trail
language (kit copied: `Look.kt`, `Palette.kt`, `design/make_icons.py`), VTM map, the version under
the icon and in the city panel, the workflow's gates.

## What was decided, and why

- **Data straight from spar.hr on the phone**, not from Google Places. Google's terms forbid its
  place data on a non-Google map and storing it past 30 days, and spar.hr's own list has the holiday
  hours and renovation closures. Marko, 1.11.2026: "we build it without Google".
- **Three sources in order**: spar.hr now → the last good answer kept in the app's files → the copy
  built into the APK. An answer is kept only when it holds at least 50 stores, so a Cloudflare page
  never empties the map.
- **Croatian time always** (`Europe/Zagreb`), whatever the phone's zone.
- **Labels from zoom 12**, circles only below it, so Croatia is not covered in labels.
- **Rotation and tilt off**: a store map is read north up, and the tap test assumes it.
- **English words** on the screen (Mon…Sun, "open", "Route"). Change in `Screens.kt` if wanted.
- **Panel close is drawn in one place** (`Panel` in `Screens.kt`); verify.py checks it.

## Lessons from this build (each cost a round)

- R8 stops on `Missing class javax.annotation.CheckReturnValue` from VTM 0.25.0 when no other
  library brings JSR-305: `-dontwarn` in `proguard-rules.pro`.
- Lint's ObsoleteSdkInt asks to move `mipmap-anydpi-v26` to `mipmap-anydpi` at minSdk 26; done, AGP
  8.7.3 drops the adaptive icon ("mipmap/ic_launcher not found"). Excused for that folder in `lint.xml`.
- Lint wants both `dataExtractionRules` (12+) and `fullBackupContent` (8–11) with `allowBackup`.
- Compose lint: no `@Composable` modifier factories; `modifier` is the first optional parameter.

## Left to do

1. **Signing: the Mac's key** (android-app.md §1b, decided 1.11.2026). From v2 the APK is built on
   Marko's Mac ("BUILD YOUR APK HERE" session) and signed with the Mac's debug key
   (~/.android/debug.keystore), released by the Mac as `vN`, Latest. GitHub Actions runs by hand only,
   so it never races the Mac. v1 (GitHub, throwaway key) must be uninstalled once; after that every
   Mac build installs over the last. If CI must ever build again, it needs SPAR_KEYSTORE holding the
   SAME key the Mac signs with, or the phone will refuse the update.
2. Tests 2–4 on the phone (DELIVERY_RECORD.md, NOT TESTED).
3. `going-public.md`: the repository is public; history scanned 1.11.2026 (see DELIVERY_RECORD.md).
