"""Merge USCG Vol. 7 green and red lights (Lake Michigan + Green Bay) into the catalog."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_catalog import (  # noqa: E402
    APP_CATALOG,
    assign_metars,
    load_stations,
    parse_characteristic,
    region,
    slug,
    write_catalog,
)

USCG_DIR = Path(__file__).with_name("uscg_d9")
SKIP_WATERWAYS = ("indian river", "crooked lake", "burt lake")
KEEP_TYPES = ("LT", "LTMA", "LB")
COLORS = ("G", "R")


def _in_box(lat, lon):
    return 41.55 <= lat <= 46.15 and -88.25 <= lon <= -84.55


def _color_char(raw, want):
    if not raw:
        return False
    sl = str(raw).upper()
    if want == "G":
        return bool(re.search(r"\bG\b", sl)) and "RG" not in sl
    if want == "R":
        return bool(re.search(r"\bR\b", sl)) and "WR" not in sl and "RG" not in sl
    return False


def _short_name(name):
    s = name.strip()
    s = re.sub(r"\s+Lighted Buoy\b", " Buoy", s, flags=re.I)
    s = re.sub(r"\s+Lighthouse\b", "", s, flags=re.I)
    s = re.sub(r"\s+Light Station\b", "", s, flags=re.I)
    s = re.sub(r"\s+Light\b", "", s, flags=re.I)
    return s.strip() or name


def _llnr(num):
    if num is None or str(num).strip() == "":
        return ""
    if isinstance(num, float) and num == int(num):
        text = str(int(num))
    else:
        text = str(num).strip()
    if text.startswith("7-"):
        return text
    return "7-" + text


def load_uscg_features():
    feats = []
    files = sorted(USCG_DIR.glob("lightListD09_*.geojson"))
    if not files:
        raise SystemExit("Download District 9 GeoJSON into tools/uscg_d9/ first.")
    for path in files:
        data = json.loads(path.read_text(encoding="utf-8"))
        feats.extend(data.get("features") or [])
    return feats


def uscg_color_items(want):
    items = []
    for feat in load_uscg_features():
        geom = feat.get("geometry") or {}
        coords = geom.get("coordinates") or []
        if len(coords) < 2:
            continue
        lon, lat = float(coords[0]), float(coords[1])
        if not _in_box(lat, lon):
            continue
        pr = feat.get("properties") or {}
        if str(pr.get("INACTIVE") or "0") not in ("0", "0.0", ""):
            continue
        if (pr.get("DESCRIPTION_TYPE") or "").upper() not in KEEP_TYPES:
            continue
        raw_char = (pr.get("LIGHT_CHAR") or "").strip()
        if not _color_char(raw_char, want):
            continue
        waterway = (pr.get("HWATERWAY_NAME") or "").lower()
        if any(skip in waterway for skip in SKIP_WATERWAYS):
            continue
        name = (pr.get("NAME") or "").strip()
        if not name:
            continue
        parsed = parse_characteristic(raw_char)
        if not parsed:
            period = 4.0 if want == "G" else 4.0
            parsed = {
                "char": raw_char or f"Fl {want} 4s",
                "color": want,
                "period_s": period,
                "on_s": [0.5],
                "off_s": [3.5],
            }
        parsed["color"] = want
        parsed["source"] = "USCG Light List Vol. 7"
        items.append(
            {
                "id": slug(name, lat, lon),
                "name": name,
                "short_name": _short_name(name),
                "lat": round(lat, 5),
                "lon": round(lon, 5),
                "region": region(lat, lon),
                "llnr": _llnr(pr.get("LIGHT_LIST_NUMBER")),
                "light": parsed,
            }
        )
    return items


def _same_aid(a, b):
    la, lb = (a.get("llnr") or "").strip(), (b.get("llnr") or "").strip()
    if la and lb:
        return la == lb
    return a.get("id") == b.get("id")


def merge(existing, incoming):
    out = list(existing)
    added = 0
    for item in incoming:
        if any(_same_aid(item, old) for old in out):
            continue
        out.append(item)
        added += 1
    out.sort(key=lambda x: (x["lat"], x["lon"]))
    return out, added


def main():
    src = json.loads(APP_CATALOG.read_text(encoding="utf-8"))
    existing = src.get("lighthouses") or []
    incoming = []
    for color in COLORS:
        incoming.extend(uscg_color_items(color))
    merged, added = merge(existing, incoming)
    assign_metars(merged, load_stations())
    write_catalog(merged)
    greens = sum(1 for i in merged if (i.get("light") or {}).get("color") == "G")
    reds = sum(1 for i in merged if (i.get("light") or {}).get("color") == "R")
    print(
        "existing",
        len(existing),
        "uscg laterals",
        len(incoming),
        "added",
        added,
        "total greens",
        greens,
        "total reds",
        reds,
    )


if __name__ == "__main__":
    main()
