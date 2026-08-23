"""Build app catalog from Wikidata Great Lakes lighthouse query (already filtered)."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WD = Path(__file__).with_name("wikidata_lh.json")


def parse_characteristic(raw: str):
    if not raw:
        return None
    s = raw.strip()
    s = s.replace("Flevery", " every ")
    s = re.sub(r"\s+", " ", s)
    sl = s.upper()

    # Cleanup common Wikidata wording
    sl = sl.replace("FF W", "F W").replace("FF R", "F R")
    sl = sl.replace("1 FL EVERY 6 SEC", "FL W 6S")
    sl = sl.replace("FL W, 1 FL EVERY 6 SEC", "FL W 6S")
    sl = sl.replace("FL W, 1 EVERY 6 SEC", "FL W 6S")
    if "EVERY 6 SEC" in sl and "FL" in sl:
        sl = "FL W 6S"
    if sl.startswith("F W LIGHT"):
        sl = "F W"

    color = "W"
    if re.search(r"\bG\b", sl) and "RG" not in sl:
        color = "G"
    if re.search(r"\bR\b", sl) and "WR" not in sl:
        color = "R"
    if "WR" in sl or "ALT" in sl:
        color = "W"

    def fl_times(period, flashes=1):
        period = float(period)
        if flashes == 1:
            on = 0.5 if period <= 4 else 1.0
            return [on], [max(0.1, period - on)]
        on = 0.5
        gap = 1.0
        rest = max(0.5, period - flashes * on - (flashes - 1) * gap)
        ons = [on] * flashes
        offs = [gap] * (flashes - 1) + [rest]
        return ons, offs

    m = re.search(r"FL\s*\((\d+)\)\s*[WRG].*?(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        n, period = int(m.group(1)), float(m.group(2))
        on_s, off_s = fl_times(period, n)
        return {"char": f"Fl({n}) {color} {period:g}s", "color": color, "period_s": period, "on_s": on_s, "off_s": off_s}

    m = re.search(r"ISO\s*[WRG]\s*(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        half = period / 2.0
        return {"char": f"Iso {color} {period:g}s", "color": color, "period_s": period, "on_s": [half], "off_s": [half]}

    m = re.search(r"OC\s*[WRG]\s*(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        off = 1.0 if period >= 4 else 0.5
        on = max(0.5, period - off)
        return {"char": f"Oc {color} {period:g}s", "color": color, "period_s": period, "on_s": [on], "off_s": [off]}

    m = re.search(r"ALT\s*WR\s*(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        return {"char": f"Alt WR {period:g}s", "color": "W", "period_s": period, "on_s": [period / 2], "off_s": [period / 2]}

    if sl.startswith("QF") or sl.startswith("Q "):
        return {"char": "Q W", "color": "W", "period_s": 1.0, "on_s": [0.3], "off_s": [0.7]}

    m = re.search(r"FL\s*[WRG]\s*(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        on_s, off_s = fl_times(period, 1)
        return {"char": f"Fl {color} {period:g}s", "color": color, "period_s": period, "on_s": on_s, "off_s": off_s}

    if re.match(r"F\s*[WRG]\b", sl) or sl in ("F W", "F R", "F G"):
        return {"char": f"F {color}", "color": color, "period_s": 1.0, "on_s": [1.0], "off_s": [0.0]}

    return None


def region(lat, lon):
    if lon <= -87.55 and lat >= 44.4:
        return "Green Bay"
    if lat >= 45.65 and lon >= -85.7:
        return "Straits / North"
    if lon <= -87.35:
        return "Wisconsin / Illinois"
    if lat < 41.85:
        return "Indiana / Chicago"
    return "Michigan"


def slug(name, lat, lon):
    base = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")
    return f"{base}_{int(lat * 10000)}_{int(abs(lon) * 10000)}"


def main():
    data = json.loads(WD.read_text(encoding="utf-8"))
    pat = re.compile(r"Point\(([-\d.]+)\s+([-\d.]+)\)")
    seen = {}
    for r in data["results"]["bindings"]:
        c = r.get("coord", {}).get("value", "")
        m = pat.search(c)
        if not m:
            continue
        lon, lat = float(m.group(1)), float(m.group(2))
        if not (41.55 <= lat <= 46.15 and -88.25 <= lon <= -84.55):
            continue
        name = r.get("itemLabel", {}).get("value", "").strip()
        if not name or name.startswith("Q"):
            continue
        key = (round(lat, 4), round(lon, 4), name.lower())
        raw_char = r.get("char", {}).get("value", "")
        parsed = parse_characteristic(raw_char)
        if key in seen and seen[key].get("light") and not parsed:
            continue
        if not parsed:
            parsed = {"char": raw_char or "F W", "color": "W", "period_s": 1.0, "on_s": [1.0], "off_s": [0.0]}
            if not raw_char:
                parsed["source"] = "Characteristic not listed; using steady white until confirmed"
            else:
                parsed["source"] = "Wikidata (unparsed): " + raw_char
        else:
            parsed["source"] = "Wikidata / USCG-style characteristic"
        seen[key] = {
            "id": slug(name, lat, lon),
            "name": name,
            "short_name": name.replace(" Light", "").replace(" Lighthouse", "").replace(" Light Station", ""),
            "lat": round(lat, 5),
            "lon": round(lon, 5),
            "region": region(lat, lon),
            "llnr": r.get("llnr", {}).get("value", ""),
            "light": parsed,
        }

    items = sorted(seen.values(), key=lambda x: (x["lat"], x["lon"]))
    out = {
        "version": 1,
        "area": "Lake Michigan and adjoining waters (Green Bay, Straits approaches)",
        "notes": "Search catalog in the app, then add lights to your LED list. Not all entries are on the strip.",
        "count": len(items),
        "lighthouses": items,
    }
    dest = ROOT / "app" / "src" / "main" / "assets" / "catalog.json"
    dest.write_text(json.dumps(out, indent=2), encoding="utf-8")
    print("wrote", dest, "count", len(items))
    print("regions", {r: sum(1 for i in items if i["region"] == r) for r in sorted({i["region"] for i in items})})


if __name__ == "__main__":
    main()
