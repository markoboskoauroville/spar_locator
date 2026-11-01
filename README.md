# Spar Locator

An Android app: every SPAR and INTERSPAR in Croatia on OpenStreetMap. Choose your city; each store
is a circle, **green when it is open now, red when it is shut**, with today's opening and closing
hours above it (`7–21`; `–` when it does not open today). Open and shut are Croatian time and change
on the minute, including public holidays, the Sundays a shop may open, and shops shut for renovation.

Tap a store for its address, phone and this week's hours, and **Route** to open it in your maps app.

## How to install

Download the latest APK on the phone and open it:
**https://github.com/markoboskoauroville/spar_locator/releases/latest**

The first builds are test builds signed with a throwaway key until `SPAR_KEYSTORE` is set (see
HANDOVER.md). Such a build must be uninstalled before the next one is installed.

## Where the data comes from

The list behind https://www.spar.hr/lokacije/ , asked from the phone (`Source.kt`). The last good
answer is kept on the phone, and a copy read on 1.11.2026 is built into the APK, so the app works
without a signal. spar.hr publishes holiday hours about two months ahead.

## How it is built

Kotlin, Jetpack Compose, VTM (mapsforge's OpenGL map renderer) with OpenStreetMap's tiles, in the
Trail design language of MANTRA_MANIFEST. Built and released by GitHub Actions
(`.github/workflows/build-apk.yml`): gates G1, G2, G3 (`scripts/verify.py`), G5, Test 1, the signed
APK, release `vN` as `N-spar-locator-vN.apk`, the two newest kept. The version is `appVersion` in
`gradle.properties`, and every change raises it.

Checks to run before a push:

```sh
python3 scripts/verify.py
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:compileReleaseKotlin
```

Map data © OpenStreetMap contributors. Store data: spar.hr. Not affiliated with SPAR.
