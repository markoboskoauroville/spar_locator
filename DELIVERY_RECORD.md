# Delivery record — Spar Locator v1

    artefact   1-spar-locator-v1.apk, from the GitHub Actions run on the commit the release names
    built      GitHub Actions (Marko's Mac was not reachable from this cloud session), never on a desk

## MEASURED (1.11.2026, in the cloud session)

    Test 1        33 cases, 0 failures (JUnit, Stores.kt against spar.hr's real answer of
                  1.11.2026 and hand-written edges). Harness proved: with spar.hr's 0-based
                  month read as 1-based, 8 cases went red, the right 8.
    verify.py     50 checks, 0 failed; the on-screen-words check planted once and seen to fail.
    compile       ./gradlew :app:compileReleaseKotlin, warnings as errors: green.
    lint          :app:lintRelease, warnings as errors: "No issues found" (after the fixes in
                  HANDOVER.md).
    APK           :app:assembleRelease (unsigned, thrown away): 1.2 MB, versionCode 1,
                  versionName 1, one permission (INTERNET), libvtm-jni for arm64, armv7, x86_64.
    icon          rendered and measured: symbol 0.566 of the mask (band 0.55–0.60), inside the
                  42-unit box; read at 48 px on a light ground.
    spar.hr       the store list answers this app's own user agent: 200, 617 kB, 144 stores.
    the numbers   1.11.2026 (All Saints): 0 of 144 open. Wednesday 4.11.2026 20:45: 140 open,
                  4 shut for renovation. 63 cities, Zagreb 51.

## NOT TESTED

- **Nothing has run on a phone or an emulator.** No map has been drawn, no tile fetched by the app,
  no tap tried. VTM needs a GPU; the cloud emulator is software-only and its network needs the
  session proxy (cloud-android.md §6), so the screen test is on the phone.
- **The tap test** (`SparMap.storeAt`) is arithmetic that has never met a finger: which store a tap
  on a label picks where two touch.
- **The fly-to a city** (`BoundingBox` with margins) has not been seen: whether the top line and the
  key row cover stores at the edge.
- **spar.hr from a phone on mobile data** was not tried; Cloudflare may treat it differently from
  this sandbox's address. If it refuses, the app keeps the saved or built-in list and the city panel
  says "offline".
- **OpenStreetMap's tiles through TileHttp** are proven in Mantra ARKOD, not in this app.
- **The Route button** (a `geo:` link) has not been tapped.
- **Test 3** (no network at the first start, spar.hr down, a day change at midnight while open) and
  **Test 4** (v1 → v2 over the top) are not done; Test 4 cannot pass until SPAR_KEYSTORE exists.
- **System bars**: insets come from `WindowInsets.safeDrawing`; not looked at on the Pixel 7 with
  the camera hole, in either navigation mode.
