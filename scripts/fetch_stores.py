#!/usr/bin/env python3
"""Fetch all SPAR Croatia store locations from spar.hr and write a compact
JSON file that the map page (index.html) reads.

Usage:
    python3 scripts/fetch_stores.py              # fetch from spar.hr
    python3 scripts/fetch_stores.py raw.json     # normalise a saved response

Output: data/stores.json
"""
import json
import sys
import urllib.request
from pathlib import Path

SOURCE_URL = "https://www.spar.hr/lokacije/_jcr_content.stores.v2.html"
OUT = Path(__file__).resolve().parent.parent / "data" / "stores.json"

# Monday..Sunday, the order used in the output "week" array.
DAYS = ["ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota", "nedjelja"]



def fetch_raw():
    req = urllib.request.Request(
        SOURCE_URL,
        headers={
            "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36",
            "Accept": "application/json, */*",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def minutes(t):
    """Java-calendar-ish time object -> minutes after midnight (or None)."""
    if not t:
        return None
    return t["hourOfDay"] * 60 + t["minute"]


def date_str(t):
    # The API uses 0-based months (java.util.Calendar).
    return f"{t['year']:04d}-{t['month'] + 1:02d}-{t['dayOfMonth']:02d}"


def intervals(oh):
    """Opening-hours entry -> list of [open, close] minute pairs (may be empty)."""
    out = []
    for f, t in (("from1", "to1"), ("from2", "to2")):
        o, c = minutes(oh.get(f)), minutes(oh.get(t))
        if o is not None and c is not None:
            if c == 0:
                c = 24 * 60  # open until midnight
            out.append([o, c])
    return out


def city_name(raw):
    name = " ".join(raw.split()).title()
    return name


def normalise(store):
    by_day = {h["openingHours"]["dayType"]: intervals(h["openingHours"]) for h in store.get("shopHours") or []}
    special = {}
    for h in store.get("specialShopHours") or []:
        oh = h["openingHours"]
        if isinstance(oh.get("dayType"), dict):
            special[date_str(oh["dayType"])] = intervals(oh)

    closed = None
    tc = store.get("temporarilyClosed")
    if tc and tc.get("dateFrom") and tc.get("dateTo"):
        closed = {"from": date_str(tc["dateFrom"]), "to": date_str(tc["dateTo"]), "info": tc.get("info") or ""}

    plant = (store.get("plantType") or {}).get("name", "")
    return {
        "id": store["locationId"],
        "name": " ".join(store["name"].split()),
        "type": "INTERSPAR" if "INTERSPAR" in plant.upper() else "SPAR",
        "address": " ".join(store["address"].split()),
        "city": city_name(store["city"]),
        "zip": store.get("zipCode", ""),
        "lat": round(store["latitude"], 6),
        "lon": round(store["longitude"], 6),
        "phone": store.get("telephone") or "",
        "url": "https://www.spar.hr" + store["pageUrl"] if store.get("pageUrl") else "",
        # week[0] = Monday ... week[6] = Sunday; each a list of [open, close] in minutes.
        "week": [by_day.get(d, []) for d in DAYS],
        # "YYYY-MM-DD" -> intervals; an empty list means closed all day (holiday).
        "special": special,
        # Temporary closure (e.g. renovation), inclusive date range.
        "closed": closed,
    }


def main():
    raw = json.load(open(sys.argv[1], encoding="utf-8")) if len(sys.argv) > 1 else fetch_raw()
    stores = sorted((normalise(s) for s in raw if s.get("latitude") and s.get("longitude")),
                    key=lambda s: (s["city"], s["name"]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(stores, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Wrote {len(stores)} stores to {OUT}")


if __name__ == "__main__":
    main()
