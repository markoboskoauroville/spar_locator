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

It works as-is on GitHub Pages: Settings → Pages → deploy from the branch root.

## Data

`data/stores.json` is generated from the store feed behind https://www.spar.hr/lokacije/:

```sh
python3 scripts/fetch_stores.py
```

The feed includes the weekly schedule, date-specific overrides (Sundays when stores may open, public holidays) and temporary closures. spar.hr only publishes overrides about two months ahead, so the data needs regular refreshing. The `Refresh store data` GitHub Action does this daily. spar.hr sits behind Cloudflare, so if the Action starts failing, run the script locally and commit the result.
