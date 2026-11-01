#!/usr/bin/env python3
"""THE STRUCTURAL CHECKS (delivery-gate.md G3): what a compiler will not look at, counted.

Run from the repository root: python3 scripts/verify.py
Every check prints what it examined; a check that examined nothing is a failure, because a check
that cannot fail is not a check (checking-the-checks.md).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/mantra/spar"
RES = ROOT / "app/src/main/res"
failures = []
passed = 0


def check(ok, what):
    global passed
    if ok:
        passed += 1
    else:
        failures.append(what)
        print("FAIL  " + what)


kotlin = {p.name: p.read_text(encoding="utf-8") for p in sorted(SRC.glob("*.kt"))}
check(len(kotlin) >= 8, f"Kotlin files found: {len(kotlin)} (expected at least 8)")

# 1. The mechanism imports nothing from Android (android-app.md §1), so Test 1 runs on a desk.
for name in ["Stores.kt"]:
    text = kotlin.get(name, "")
    check(text != "", f"{name} exists")
    bad = re.findall(r"^import (android|androidx)\.", text, re.M)
    check(not bad, f"{name} imports nothing from Android (found {len(bad)})")

# 2. Every icon used has a drawable and an SVG, and each drawable is its SVG (language-trail.md §5).
used = set()
for text in kotlin.values():
    used |= set(re.findall(r"R\.drawable\.ic_(\w+)", text))
check(len(used) >= 5, f"icons used in the code: {len(used)}")
for name in sorted(used):
    drawable = RES / "drawable" / f"ic_{name}.xml"
    svg = ROOT / "design/icons" / f"{name}.svg"
    check(drawable.exists(), f"icon {name} has res/drawable/ic_{name}.xml")
    check(svg.exists(), f"icon {name} has design/icons/{name}.svg")
    if drawable.exists() and svg.exists():
        d_xml = re.search(r'android:pathData="([^"]+)"', drawable.read_text()).group(1)
        d_svg = re.search(r'<path d="([^"]+)"', svg.read_text()).group(1)
        check(d_xml == d_svg, f"ic_{name}.xml is drawn from {name}.svg")

# 3. One word on a control, never a sentence explaining it (mantra-design-language.md §1.3).
#    Only the words the app shows (its string literals) are searched: Look.kt's comments quote
#    Marko saying it, which is the reason for the rule, not a breach of it.
literals = 0
for name, text in kotlin.items():
    shown = re.findall(r'"((?:[^"\\\n]|\\.)*)"', text)
    literals += len(shown)
    hits = [w for w in shown if re.search(r"press to (change|show|hide)", w, re.I)]
    check(not hits, f"{name}: no 'press to change / show / hide' on screen ({len(hits)})")
check(literals >= 30, f"string literals examined: {literals}")

# 4. No comment opens a nested comment: Kotlin nests /* */, and a path like icons/*.svg in a KDoc
#    swallowed the rest of a file in Mantra Trail v91.
nested = 0
for name, text in kotlin.items():
    for block in re.findall(r"/\*.*?\*/", text, re.S):
        if "/*" in block[2:]:
            nested += 1
            print(f"      nested comment opener in {name}: {block[:60]!r}")
check(nested == 0, f"comments opening a nested comment: {nested}")

# 5. The way out is the ✕ at the end of every panel's top row (mantra-design-language.md §1.7).
screens = kotlin.get("Screens.kt", "")
panels = len(re.findall(r"\bPanel\(", screens)) - 1  # minus the definition
titles_with_close = len(re.findall(r"R\.drawable\.ic_close", screens))
check(panels >= 2, f"panels on the screen: {panels}")
check(titles_with_close == 1 and "fun Panel(" in screens, "every panel's close comes from one Panel title row")

# 6. One whole number, written once (versioning.md).
props = (ROOT / "gradle.properties").read_text()
m = re.search(r"^appVersion=(\d+)\s*$", props, re.M)
check(m is not None, "appVersion in gradle.properties is a whole number")
check("<!--VERSION-->" in (ROOT / "app/src/main/icon/ic_launcher_foreground.xml").read_text(),
      "the icon template has its VERSION line (version-visible.md)")

# 7. The version row opens the latest build of THIS repository (version-visible.md §3).
check("https://github.com/markoboskoauroville/spar_locator/releases/latest" in screens,
      "the version row links to this repository's latest release")

# 8. The app asks for the network and nothing else.
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
perms = re.findall(r'uses-permission android:name="([^"]+)"', manifest)
check(perms == ["android.permission.INTERNET"], f"permissions: {perms}")

# 9. No key in the source, of any shape (secrets.md, going-public.md).
shape = re.compile(r"AQ\.[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|sk-ant-[A-Za-z0-9_-]{20,}|gsk_[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,}")
keys = 0
scanned = 0
for p in ROOT.rglob("*"):
    if p.is_file() and ".git" not in p.parts and "build" not in p.parts and p.suffix in {".kt", ".kts", ".xml", ".py", ".yml", ".md", ".properties", ".json"}:
        scanned += 1
        keys += len(shape.findall(p.read_text(encoding="utf-8", errors="ignore")))
check(scanned >= 20 and keys == 0, f"files scanned for key shapes: {scanned}, keys found: {keys}")

# 10. The built-in store list is spar.hr's list, not a Cloudflare page.
asset = (ROOT / "app/src/main/assets/spar_stores.json").read_text(encoding="utf-8")
check(asset.lstrip().startswith("[") and asset.count('"locationId"') >= 50,
      f"the built-in list holds {asset.count(chr(34) + 'locationId' + chr(34))} stores")

print(f"verify.py: {passed} checks passed, {len(failures)} failed")
sys.exit(1 if failures else 0)
