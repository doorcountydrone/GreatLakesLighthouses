import json
import re
from pathlib import Path

p = Path(__file__).with_name("wikidata_lh.json")
d = json.loads(p.read_text(encoding="utf-8"))
rows = d["results"]["bindings"]
pat = re.compile(r"Point\(([-\d.]+)\s+([-\d.]+)\)")
hits = []
for r in rows:
    c = r.get("coord", {}).get("value", "")
    m = pat.search(c)
    if not m:
        continue
    lon, lat = float(m.group(1)), float(m.group(2))
    if not (41.55 <= lat <= 46.15 and -88.25 <= lon <= -84.55):
        continue
    hits.append({
        "lat": lat,
        "lon": lon,
        "name": r.get("itemLabel", {}).get("value", ""),
        "char": r.get("char", {}).get("value", ""),
        "llnr": r.get("llnr", {}).get("value", ""),
    })
hits.sort(key=lambda x: (x["lat"], x["lon"]))
print("in bbox", len(hits), "with char", sum(1 for h in hits if h["char"]))
for h in hits:
    ch = h["char"] or "-"
    print(f"{h['lat']:.4f} {h['lon']:.4f} | {ch} | {h['name']}")
