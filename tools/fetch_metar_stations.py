"""Download AWC METAR stations for the Lake Michigan catalog bbox."""
import json
import ssl
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
URL = "https://aviationweather.gov/api/data/stationinfo?bbox=41.4,-88.6,46.3,-84.4&format=json"


def has_metar(station):
    site_type = station.get("siteType")
    if isinstance(site_type, list):
        return any("METAR" in str(item).upper() for item in site_type)
    return "METAR" in str(site_type).upper()


def main():
    req = urllib.request.Request(URL, headers={"User-Agent": "GreatLakesLighthouses/1.0"})
    ctx = ssl._create_unverified_context()
    with urllib.request.urlopen(req, timeout=45, context=ctx) as resp:
        data = json.loads(resp.read().decode())
    stations = []
    for row in data:
        icao = (row.get("icaoId") or "").strip().upper()
        if not icao.startswith("K"):
            continue
        if not has_metar(row):
            continue
        stations.append({
            "icao": icao,
            "name": (row.get("site") or icao).strip(),
            "lat": float(row["lat"]),
            "lon": float(row["lon"]),
        })
    stations.sort(key=lambda item: item["icao"])
    dest = ROOT / "tools" / "metar_stations.json"
    dest.write_text(
        json.dumps({"area": "Lake Michigan METAR stations", "count": len(stations), "stations": stations}, indent=2),
        encoding="utf-8",
    )
    print("wrote", dest, "count", len(stations))


if __name__ == "__main__":
    main()
