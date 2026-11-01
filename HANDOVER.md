# Handover — Spar Locator

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
