"""Merge Canadian Coast Guard Inland Waters lights (Huron, Georgian Bay, North Channel, Erie)."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from add_uscg_greens import merge  # noqa: E402
from generate_catalog import (  # noqa: E402
    APP_CATALOG,
    assign_metars,
    load_stations,
    parse_characteristic,
    slug,
    write_catalog,
)

SRC = Path(__file__).with_name("ccg_inland") / "inland_waters.txt"
# List-of-Lights number ranges from the Inland Waters volume.
SECTIONS = (
    (403.4, 550.99, "Canada Ontario"),
    (552.0, 623.99, "Canada Erie"),
    (768.0, 813.99, "Canada Huron"),
    (814.0, 982.99, "Georgian Bay"),
    (983.0, 1041.99, "North Channel"),
    (1082.0, 1161.99, "Canada Superior"),
)
# Official Inland Waters positions missing from the 2026 text extract.
COORD_FALLBACK = {
    1111.0: (48 + 37 / 60 + 16.0 / 3600, -(86 + 59 / 60 + 45.0 / 3600)),
    1128.0: (48 + 20 / 60 + 24.0 / 3600, -(88 + 38 / 60 + 54.0 / 3600)),
}

LL_LINE = re.compile(r"^(?:#{1,6}\s*)?(?:\|+\s*)?(\d{3,4}(?:\.\d+)?)\s+(.+)$")
INLINE_COORD = re.compile(
    r"\b(4[1-8])\s+(\d{2})\s+(\d{2}(?:\.\d+)?)\s+0?(7[6-9]|8[0-9]|9[0-2])\s+(\d{2})\s+(\d{2}(?:\.\d+)?)\b"
)
LAT_ONLY = re.compile(r"\b(4[1-8])\s+(\d{2})\s+(\d{2}(?:\.\d+)?)\b")
LON_ONLY = re.compile(r"\b0?(7[6-9]|8[0-9]|9[0-2])\s+(\d{2})\s+(\d{2}(?:\.\d+)?)\b")
TABLE_LAT = re.compile(r"\|\s*(4[1-8])\s*\|\s*(\d{2})\s*\|\s*(\d{2}(?:\.\d+)?)\s*\|")
TABLE_LON = re.compile(r"\|\s*0?(7[6-9]|8[0-9]|9[0-2])\s*(?:\|\s*)+(\d{2})\s*\|\s*(\d{2}(?:\.\d+)?)")
BUOY_CONT = re.compile(r"^(?:light\s+)?buoy\b", re.I)
CHAR_RE = re.compile(
    r"\b(LFl|Iso|Oc|Q|F|Fl(?:\(\d+(?:\s*\+\s*\d+)?\))?|Mo\([A-Z]\))\s+"
    r"([WRGY]+)\s*(\d+(?:\.\d+)?s|\.{3})?",
    re.I,
)
JUNK_NAME = re.compile(
    r"^(No\.|Name|Location|LAKE |Characteristics|Focal|Seasonal|Year round|Private|Chart:|Edn |-----)",
    re.I,
)


def _dms(deg, minute, sec):
    return float(deg) + float(minute) / 60.0 + float(sec) / 3600.0


def _clean_name(raw):
    name = re.sub(r"[|*#]+", " ", raw)
    name = re.sub(r"\b(4[1-8]\s+\d{2}\s+\d{2}(?:\.\d+)?).*$", "", name)
    ch = CHAR_RE.search(re.sub(r"\s*\|\s*", " ", name))
    if ch:
        name = name[:ch.start()]
    name = re.sub(r"\b(Seasonal|Year round|Private aid|Winter spar|Operates|Flash )\b.*$", "", name, flags=re.I)
    name = re.sub(r"\s+", " ", name).strip(" .")
    name = re.sub(
        r"\b(On |At |Near |Off |E\. |W\. |N\. |S\. |SW\. |NW\. |NE\. |SE\. |Centre of |Marks |Entrance to |End of |Inner Bay|Northernmost |Westward of |Outer approach).*$",
        "",
        name,
    ).strip(" .")
    name = re.sub(r"(buoy\s+[A-Z0-9/]+)\b.*", r"\1", name, flags=re.I)
    name = re.sub(r"\bfront range light\b", "Range Front", name, flags=re.I)
    name = re.sub(r"\brear range light\b", "Range Rear", name, flags=re.I)
    name = re.sub(r"\s+", " ", name).strip(" .")
    if len(name) > 80:
        name = name[:80].rsplit(" ", 1)[0]
    return name


def _ll_num(line):
    m = LL_LINE.match(line)
    if not m:
        return None
    try:
        num = float(m.group(1))
    except ValueError:
        return None
    return num if _section_for(num) else None


def _coords(block_lines):
    block = "\n".join(block_lines)
    inline = INLINE_COORD.search(re.sub(r"[|]+", " ", block))
    if inline:
        return _dms(*inline.group(1, 2, 3)), -_dms(*inline.group(4, 5, 6))
    plat = plon = None
    for piece in block_lines:
        lm = TABLE_LAT.search(piece)
        if lm and plat is None:
            plat = _dms(lm.group(1), lm.group(2), lm.group(3))
            continue
        om = TABLE_LON.search(piece)
        if om and plat is not None and plon is None:
            return plat, -_dms(om.group(1), om.group(2), om.group(3))
        compact = re.sub(r"[|]+", " ", piece).strip()
        if plat is None:
            lm2 = LAT_ONLY.search(compact)
            if lm2 and not INLINE_COORD.search(compact):
                plat = _dms(lm2.group(1), lm2.group(2), lm2.group(3))
        if plat is not None and plon is None:
            om2 = LON_ONLY.search(compact)
            if om2:
                return plat, -_dms(om2.group(1), om2.group(2), om2.group(3))
    return plat, plon


def _short_name(name):
    s = re.sub(r"\s+lighted\s+(spar\s+)?buoy\b", " Buoy", name, flags=re.I)
    s = re.sub(r"\s+light buoy\b", " Buoy", s, flags=re.I)
    s = re.sub(r"\s+Lighthouse\b", "", s, flags=re.I)
    s = re.sub(r"\s+Light\b", "", s, flags=re.I)
    return s.strip() or name


def _section_for(num):
    for lo, hi, region in SECTIONS:
        if lo <= num <= hi:
            return region
    return None


def _parse_char(blob):
    blob = re.sub(r"\s*\|\s*", " ", blob)
    matches = list(CHAR_RE.finditer(blob))
    m = None
    for cand in reversed(matches):
        if cand.group(2).upper()[:1] in ("W", "R", "G"):
            m = cand
            break
    if not m:
        return None
    kind, color, period = m.group(1), m.group(2).upper(), m.group(3) or ""
    raw = f"{kind} {color} {period}".strip()
    raw = re.sub(r"\s+\.{3}$", "", raw)
    parsed = parse_characteristic(raw.replace("...", "").strip())
    if parsed:
        if color[:1] in ("W", "R", "G"):
            parsed["color"] = color[:1]
        return parsed
    col = "W"
    if "G" in color and "R" not in color:
        col = "G"
    elif "R" in color and "W" not in color:
        col = "R"
    return {
        "char": raw or f"Fl {col} 4s",
        "color": col,
        "period_s": 4.0,
        "on_s": [0.5],
        "off_s": [3.5],
    }


def parse_inland_text(text):
    lines = [ln.strip() for ln in text.splitlines()]
    records = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m = LL_LINE.match(line)
        num = None
        name = ""
        if m:
            try:
                num = float(m.group(1))
            except ValueError:
                num = None
            name = _clean_name(m.group(2))
        if num is None or _section_for(num) is None or not name or JUNK_NAME.match(name):
            i += 1
            continue
        extra = []
        k = i + 1
        while k < len(lines) and k <= i + 2:
            piece = re.sub(r"[|]+", " ", lines[k]).strip()
            if BUOY_CONT.match(piece):
                extra.append(re.split(r"\s+\d{2}\s+", piece, maxsplit=1)[0].strip(" ."))
                k += 1
                continue
            break
        if extra:
            name = _clean_name(name + " " + " ".join(extra))
        back = []
        k = i - 1
        while k >= 0 and len(back) < 8:
            if _ll_num(lines[k]) is not None:
                break
            back.insert(0, lines[k])
            k -= 1
        blob = [line]
        j = i + 1
        while j < len(lines) and j < i + 28:
            if _ll_num(lines[j]) is not None and j > i + 1:
                break
            blob.append(lines[j])
            j += 1
        lat, lon = _coords(blob)
        if lat is None or lon is None:
            fb = COORD_FALLBACK.get(num)
            if fb:
                lat, lon = fb
        if lat is None or lon is None:
            i += 1
            continue
        if not (41.2 <= lat <= 49.1 and -92.4 <= lon <= -76.0):
            i += 1
            continue
        parsed = _parse_char(line)
        if not parsed and INLINE_COORD.search(re.sub(r"[|]+", " ", line)):
            parsed = _parse_char("\n".join(back))
        if not parsed:
            parsed = _parse_char("\n".join(blob)) or _parse_char("\n".join(back + blob))
        if not parsed:
            i += 1
            continue
        if parsed.get("color") not in ("G", "R", "W"):
            i += 1
            continue
        if "(U.S.)" in "\n".join(blob) or "(U.S. )" in "\n".join(blob):
            i += 1
            continue
        parsed["source"] = "Canadian List of Lights, Inland Waters"
        region = _section_for(num)
        records.append(
            {
                "id": slug(name, lat, lon),
                "name": name,
                "short_name": _short_name(name),
                "lat": round(lat, 5),
                "lon": round(lon, 5),
                "region": region,
                "llnr": f"C-{num:g}",
                "light": parsed,
            }
        )
        i = j if j > i + 1 else i + 1
    # Dedup same CCG number (keep first with coords)
    seen = {}
    out = []
    for item in records:
        key = item["llnr"]
        if key in seen:
            continue
        seen[key] = True
        out.append(item)
    return out


def main():
    if not SRC.exists():
        raise SystemExit("Missing %s — official Inland Waters text" % SRC)
    incoming = parse_inland_text(SRC.read_text(encoding="utf-8"))
    src = json.loads(APP_CATALOG.read_text(encoding="utf-8"))
    existing = [
        item
        for item in (src.get("lighthouses") or [])
        if not str(item.get("llnr") or "").startswith("C-")
    ]
    merged, added = merge(existing, incoming)
    new_ids = {item["id"] for item in incoming}
    assign_metars([item for item in merged if item.get("id") in new_ids], load_stations())
    write_catalog(merged)
    by_region = {}
    for item in incoming:
        by_region[item["region"]] = by_region.get(item["region"], 0) + 1
    want = (
        "Point Clark",
        "Goderich Main",
        "Kincardine",
        "Chantry Island",
        "Cove Island",
        "Cabot Head",
        "Christian Island",
        "Pelee Passage",
        "Long Point",
        "Southeast Shoal",
        "Tobermory",
        "Southampton",
        "Nine Mile Point",
        "Main Duck",
        "False Ducks",
        "Prince Edward Point",
        "Scotch Bonnet",
        "Cobourg",
        "Oakville",
        "Port Dalhousie",
        "Toronto Harbour",
        "Ile Parisienne",
        "Coppermine Point",
        "Caribou Island",
        "Michipicoten Island",
        "Slate Islands",
        "Battle Island",
        "Trowbridge Island",
        "Thunder Bay Main",
        "Point Porphyry",
    )
    found = [
        item["name"]
        for item in incoming
        if any(w.lower() in item["name"].lower() for w in want)
    ]
    print("ccg parsed", len(incoming), "added", added, "total", len(merged), "by section", by_region)
    print("named towers", found)


if __name__ == "__main__":
    main()
