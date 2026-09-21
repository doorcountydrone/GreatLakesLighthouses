"""Build app catalog from Wikidata Great Lakes lighthouse query (already filtered)."""
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WD = Path(__file__).with_name("wikidata_lh.json")
STATIONS = Path(__file__).with_name("metar_stations.json")
APP_CATALOG = ROOT / "app" / "src" / "main" / "assets" / "catalog.json"
PICO_CATALOG = ROOT / "pico" / "catalog.json"
# All five Great Lakes (US and Canada). Canadian shores are not in USCG D9.
CATALOG_LAT = (41.30, 49.05)
CATALOG_LON = (-92.35, -76.15)


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

    m = re.search(r"FL\s*\(\s*2\s*\+\s*1\s*\)\s*[WRG].*?(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        rest = max(0.5, period - 2.5)
        return {
            "char": f"Fl(2+1) {color} {period:g}s",
            "color": color,
            "period_s": period,
            "on_s": [0.5, 0.5, 0.5],
            "off_s": [0.5, 1.0, rest],
        }

    if sl.startswith("QF") or sl.startswith("Q ") or sl.startswith("Q"):
        return {"char": f"Q {color}", "color": color, "period_s": 1.0, "on_s": [0.3], "off_s": [0.7]}

    m = re.search(r"LFL\s*[WRG]\s*(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        on = 2.0 if period >= 6 else max(1.0, period / 3)
        return {
            "char": f"LFl {color} {period:g}s",
            "color": color,
            "period_s": period,
            "on_s": [on],
            "off_s": [max(0.1, period - on)],
        }

    m = re.search(r"FL\s*[WRG]\s*(\d+(?:\.\d+)?)\s*S", sl)
    if m:
        period = float(m.group(1))
        on_s, off_s = fl_times(period, 1)
        return {"char": f"Fl {color} {period:g}s", "color": color, "period_s": period, "on_s": on_s, "off_s": off_s}

    if re.match(r"F\s*[WRG]\b", sl) or sl in ("F W", "F R", "F G"):
        return {"char": f"F {color}", "color": color, "period_s": 1.0, "on_s": [1.0], "off_s": [0.0]}

    return None


def in_catalog_box(lat, lon):
    return CATALOG_LAT[0] <= lat <= CATALOG_LAT[1] and CATALOG_LON[0] <= lon <= CATALOG_LON[1]


def in_huron_box(lat, lon):
    return 43.00 <= lat <= 46.30 and -84.80 < lon <= -81.60


def in_erie_box(lat, lon):
    return 41.30 <= lat <= 42.95 and -83.55 <= lon <= -78.75


def in_america_box(lat, lon):
    # US Lake America (Ontario): Fort Niagara to Tibbetts Point.
    return 43.15 <= lat <= 44.40 and -79.90 <= lon <= -76.15


def in_superior_box(lat, lon):
    # Whitefish Point to Duluth, Isle Royale, and the Ontario north shore.
    # East of -84.48 is St. Marys River; south of 46.40 is the Straits.
    return 46.40 <= lat <= 49.05 and -92.35 <= lon <= -84.48


def region(lat, lon):
    # Door County juts east of the rest of the Wisconsin shore, so a single
    # longitude cutoff puts Sturgeon Bay Canal, Baileys Harbor, Cana Island,
    # and Death's Door on the Michigan chip. Keep the peninsula and islands
    # on the Wisconsin side; Minneapolis Shoal / Escanaba stay Michigan.
    if in_superior_box(lat, lon):
        return "Lake Superior"
    if 44.55 <= lat < 45.50 and lon <= -86.80:
        if lon <= -87.55:
            return "Green Bay"
        return "Wisconsin / Illinois"
    if lon <= -87.55 and lat >= 44.4:
        return "Green Bay"
    if in_america_box(lat, lon):
        return "Lake America"
    if in_erie_box(lat, lon):
        return "Lake Erie"
    if lat >= 45.65 and lon >= -85.7:
        return "Straits / North"
    if in_huron_box(lat, lon) or lon > -84.55:
        return "Lake Huron"
    if lon <= -87.35:
        return "Wisconsin / Illinois"
    if lat < 41.85:
        return "Indiana / Chicago"
    return "Michigan"


def slug(name, lat, lon):
    base = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")
    return f"{base}_{int(lat * 10000)}_{int(abs(lon) * 10000)}"


def _km(lat1, lon1, lat2, lon2):
    radius = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * radius * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def load_stations():
    data = json.loads(STATIONS.read_text(encoding="utf-8"))
    return data.get("stations") or []


def nearest_metar(lat, lon, stations):
    ranked = sorted(stations, key=lambda s: _km(lat, lon, s["lat"], s["lon"]))
    if not ranked:
        return {"metar": "", "metar_fallback": "", "metar_name": ""}
    primary = ranked[0]
    fallback = ranked[1] if len(ranked) > 1 else ranked[0]
    return {
        "metar": primary["icao"],
        "metar_fallback": fallback["icao"],
        "metar_name": primary.get("name") or primary["icao"],
    }


def assign_metars(items, stations):
    for item in items:
        item.update(nearest_metar(float(item.get("lat") or 0), float(item.get("lon") or 0), stations))
    return items


_FRONT_RE = re.compile(r"\bfront\b", re.I)
_REAR_RE = re.compile(r"\brear\b", re.I)
_RANGE_WORD_RE = re.compile(r"\brange\b", re.I)
_FILLER_RE = re.compile(r"\b(the|of|and|a|light|lights|lighted|lighthouse|lt|no|number)\b", re.I)
_COMPASS_RE = re.compile(r"\b(east|west|north|south|northeast|northwest|southeast|southwest|ne|nw|se|sw)\b", re.I)
_NUM_RE = re.compile(r"\b\d+[a-z]?\b", re.I)
RANGE_PAIR_KM = 5.0
CHANNEL_PAIR_KM = 0.45


def _title_aid(s):
    small = {"and", "of", "the"}
    parts = []
    for i, word in enumerate((s or "").split()):
        low = word.lower()
        if low in small and i:
            parts.append(low)
        else:
            parts.append(low[:1].upper() + low[1:])
    return " ".join(parts)


def _range_role(name):
    text = str(name or "")
    if not _RANGE_WORD_RE.search(text):
        return None
    if re.search(r"\bpassing\b", text, re.I):
        return None
    front = bool(_FRONT_RE.search(text))
    rear = bool(_REAR_RE.search(text))
    if front == rear:
        return None
    return "front" if front else "rear"


def _range_base(name):
    s = str(name or "").lower()
    s = _FRONT_RE.sub(" ", s)
    s = _REAR_RE.sub(" ", s)
    s = _RANGE_WORD_RE.sub(" ", s)
    s = _FILLER_RE.sub(" ", s)
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def _channel_stem(name):
    s = str(name or "").lower()
    s = re.sub(r"\blighted\s+buoy\b", "buoy", s)
    s = _COMPASS_RE.sub(" ", s)
    s = _FILLER_RE.sub(" ", s)
    s = _NUM_RE.sub(" ", s)
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def _light_color(item):
    spec = item.get("light") if isinstance(item.get("light"), dict) else {}
    return str(spec.get("color") or "W").upper()


def _copy_light(item):
    spec = item.get("light")
    return dict(spec) if isinstance(spec, dict) else {"char": "F W", "color": "W", "period_s": 1.0, "on_s": [1.0], "off_s": [0.0]}


def _same_light(a, b):
    return (
        str(a.get("char") or "") == str(b.get("char") or "")
        and str(a.get("color") or "") == str(b.get("color") or "")
        and float(a.get("period_s") or 0) == float(b.get("period_s") or 0)
    )


def _aid_number(name):
    text = str(name or "").strip()
    m = re.search(r"(\d+[a-z]?)\s*$", text, re.I)
    if m:
        return m.group(1).upper()
    m = re.search(r"\blight\s+(\d+[a-z]?)\b", text, re.I)
    return m.group(1).upper() if m else ""


def _num_key(label):
    m = re.match(r"(\d+)([A-Z]?)$", str(label or ""), re.I)
    if not m:
        return (0, "")
    return (int(m.group(1)), m.group(2).upper())


def _iala_mates(green, red):
    gn = _aid_number(green.get("name"))
    rn = _aid_number(red.get("name"))
    if not gn or not rn:
        return True
    gv, gs = _num_key(gn)
    rv, rs = _num_key(rn)
    if gs != rs:
        return False
    return abs(gv - rv) == 1


def _merge_pair(a, b, kind):
    lat = round((float(a.get("lat") or 0) + float(b.get("lat") or 0)) / 2.0, 5)
    lon = round((float(a.get("lon") or 0) + float(b.get("lon") or 0)) / 2.0, 5)
    if kind == "range":
        front = a if _range_role(a.get("name")) == "front" else b
        rear = b if front is a else a
        base = _range_base(front.get("name")) or _range_base(rear.get("name"))
        name = (_title_aid(base) + " Range").strip()
        if not name.lower().endswith("range"):
            name = "Range Lights"
        short = name
        primary, other = _copy_light(front), _copy_light(rear)
        note = "Paired range front/rear. One LED."
    else:
        color_a = _light_color(a)
        green = a if color_a == "G" else b
        red = b if green is a else a
        stem = _channel_stem(green.get("name")) or _channel_stem(red.get("name"))
        nums = [n for n in (_aid_number(green.get("name")), _aid_number(red.get("name"))) if n]
        nums = sorted(set(nums), key=_num_key)
        label = "/".join(nums) if nums else "1/2"
        buoy = "buoy" in str(green.get("name") or "").lower() or "buoy" in str(red.get("name") or "").lower()
        stem = re.sub(r"\bbuoys?\b", " ", stem or "")
        stem = re.sub(r"\s+", " ", stem).strip()
        pretty = _title_aid(stem) or "Channel"
        name = pretty + (" Buoys " if buoy else " Light ") + label
        short = pretty + " " + label if len(pretty) < 28 else name
        primary, other = _copy_light(green), _copy_light(red)
        note = "Paired green and red. One LED shows both."
    primary["source"] = note
    item = {
        "id": slug(name, lat, lon),
        "name": name,
        "short_name": short,
        "lat": lat,
        "lon": lon,
        "region": a.get("region") or b.get("region") or "",
        "llnr": a.get("llnr") or b.get("llnr") or "",
        "light": primary,
        "pair": {
            "kind": kind,
            "members": [a.get("id") or "", b.get("id") or ""],
        },
        "metar": a.get("metar") or b.get("metar") or "",
        "metar_fallback": a.get("metar_fallback") or b.get("metar_fallback") or "",
        "metar_name": a.get("metar_name") or b.get("metar_name") or "",
    }
    if not _same_light(primary, other):
        other["source"] = note
        item["light_b"] = other
    return item


def pair_related_lights(items):
    """Collapse front/rear ranges and nearby green/red channel pairs onto one catalog row."""
    leftover = []
    used = set()
    range_items = []
    for i, item in enumerate(items):
        if item.get("pair"):
            leftover.append(item)
            used.add(i)
            continue
        role = _range_role(item.get("name"))
        if not role:
            continue
        base = _range_base(item.get("name"))
        if not base:
            continue
        range_items.append((i, role, base, item))

    paired = []

    def _pair_range_groups(groups):
        for group in groups.values():
            fronts = [(i, item) for i, role, item in group if role == "front"]
            rears = [(i, item) for i, role, item in group if role == "rear"]
            while fronts and rears:
                best = None
                best_km = RANGE_PAIR_KM
                for fi, front in fronts:
                    for ri, rear in rears:
                        if fi in used or ri in used:
                            continue
                        d = _km(front["lat"], front["lon"], rear["lat"], rear["lon"])
                        if d < best_km:
                            best_km = d
                            best = (fi, front, ri, rear)
                if best is None:
                    break
                fi, front, ri, rear = best
                paired.append(_merge_pair(front, rear, "range"))
                used.add(fi)
                used.add(ri)
                fronts = [(i, item) for i, item in fronts if i != fi]
                rears = [(i, item) for i, item in rears if i != ri]

    by_region_base = {}
    for i, role, base, item in range_items:
        by_region_base.setdefault((item.get("region") or "", base), []).append((i, role, item))
    _pair_range_groups(by_region_base)
    by_base = {}
    for i, role, base, item in range_items:
        if i in used:
            continue
        by_base.setdefault(base, []).append((i, role, item))
    _pair_range_groups(by_base)

    by_stem = {}
    for i, item in enumerate(items):
        if i in used:
            continue
        if _range_role(item.get("name")):
            continue
        stem = _channel_stem(item.get("name"))
        if not stem:
            continue
        color = _light_color(item)
        if color not in ("G", "R"):
            continue
        by_stem.setdefault((item.get("region") or "", stem), []).append((i, color, item))

    for group in by_stem.values():
        greens = [(i, item) for i, color, item in group if color == "G"]
        reds = [(i, item) for i, color, item in group if color == "R"]
        while greens and reds:
            best = None
            best_km = CHANNEL_PAIR_KM
            for gi, green in greens:
                for ri, red in reds:
                    if not _iala_mates(green, red):
                        continue
                    d = _km(green["lat"], green["lon"], red["lat"], red["lon"])
                    if d < best_km:
                        best_km = d
                        best = (gi, green, ri, red)
            if best is None:
                break
            gi, green, ri, red = best
            paired.append(_merge_pair(green, red, "channel"))
            used.add(gi)
            used.add(ri)
            greens = [(i, item) for i, item in greens if i != gi]
            reds = [(i, item) for i, item in reds if i != ri]

    for i, item in enumerate(items):
        if i not in used:
            leftover.append(item)
    out = leftover + paired
    out.sort(key=lambda x: (float(x.get("lat") or 0), float(x.get("lon") or 0)))
    return out


def write_catalog(items):
    items = pair_related_lights(items)
    out = {
        "version": 3,
        "area": "The Great Lakes (US and Canada)",
        "notes": "Search catalog in the app, then add lights to your LED list. Front/rear ranges and nearby green/red pairs share one LED. Includes named lighthouses plus US and Canadian lights and lighted buoys on all five Great Lakes. Each entry has the nearest METAR station. Not all entries are on the strip.",
        "count": len(items),
        "lighthouses": items,
    }
    text = json.dumps(out, indent=2)
    APP_CATALOG.write_text(text, encoding="utf-8")
    PICO_CATALOG.write_text(text, encoding="utf-8")
    n_range = sum(1 for i in items if (i.get("pair") or {}).get("kind") == "range")
    n_chan = sum(1 for i in items if (i.get("pair") or {}).get("kind") == "channel")
    print("wrote", APP_CATALOG, "and", PICO_CATALOG, "count", len(items))
    print("pairs", n_range, "range,", n_chan, "green/red")
    print("regions", {r: sum(1 for i in items if i["region"] == r) for r in sorted({i["region"] for i in items})})


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
        if not in_catalog_box(lat, lon):
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
    assign_metars(items, load_stations())
    write_catalog(items)


if __name__ == "__main__":
    if not WD.exists():
        stations = load_stations()
        src = APP_CATALOG if APP_CATALOG.exists() else PICO_CATALOG
        data = json.loads(src.read_text(encoding="utf-8"))
        items = data.get("lighthouses") or []
        for item in items:
            item["region"] = region(float(item.get("lat") or 0), float(item.get("lon") or 0))
        assign_metars(items, stations)
        write_catalog(items)
    else:
        main()
