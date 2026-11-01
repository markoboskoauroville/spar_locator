# SPAR Locator

A one-page map of every SPAR store in Croatia (SPAR supermarkets and INTERSPAR hypermarkets), built on OpenStreetMap.

- Pick your city at the top and the map zooms to it.
- Each store is a circle: **green = open now**, **red = closed now**. INTERSPAR stores are the bigger circles.
- The label above each circle shows today's hours, e.g. `7–21`. `–` means closed all day (Sunday, a public holiday or a renovation).
- Click a store to see its address, phone number and this week's hours.

"Now" always means Croatian time (Europe/Zagreb), wherever you are. The colours update every 30 seconds.

## Running it

It is a static site with no build step:

```sh
python3 -m http.server 8000
# open http://localhost:8000
```

## Publishing (GitHub Pages)

The `Publish site` GitHub Action puts the map online at
https://markoboskoauroville.github.io/spar_locator/ on every push.

One-time setup: in the repository go to **Settings → Pages → Build and deployment → Source** and choose **GitHub Actions**. Then re-run the latest `Publish site` run from the **Actions** tab, or push any change.

## Data

`data/stores.json` is generated from the store feed behind https://www.spar.hr/lokacije/:

```sh
python3 scripts/fetch_stores.py
```

The feed includes the weekly schedule, date-specific overrides (Sundays when stores may open, public holidays) and temporary closures. spar.hr only publishes overrides about two months ahead, so the data needs regular refreshing. The `Publish site` Action re-downloads it every day at 03:17 UTC and republishes the site. spar.hr sits behind Cloudflare. If it blocks the download, the site keeps showing the last saved data; run the script on your own computer and commit the result.
