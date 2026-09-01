import gc
import json
import math
import os
import machine
import neopixel
import network
import socket
import utime as time

machine.freq(230_000_000)

try:
    import urequests
except ImportError:
    import requests as urequests

try:
    import ntptime
except ImportError:
    ntptime = None

try:
    import sans18
    import writer
    fonts_available = True
except ImportError:
    fonts_available = False
    print("OLED fonts skipped (copy writer.py and sans18.py)")

FIRMWARE_VERSION = "0.6.17"
CONFIG_FILE = "wifi_config.json"
LIGHTHOUSE_FILE = "lighthouses.json"
FORCE_AP_BUTTON_PIN = 15
FORCE_AP_FLAG = "force_ap.flag"
LED_PIN = 0
NUM_LEDS = 13
BRIGHTNESS = 0.18
MIN_BRIGHTNESS = 0
MAX_BRIGHTNESS = 18
BEACON_PULSE = True
CYCLE_DELAY = 300
LDR_ADC_PIN = 26
LDR_DRIVE_PIN = 21
OLED_SDA_PIN = 16
OLED_SCL_PIN = 17
OLED_VCC_PIN = 18
OLED_GND_PIN = 19
MATRIX_IDLE_MS = 15000
MATRIX_IDLE_COLOR = (40, 200, 210)
MATRIX_SCROLL_SPEED = 7
BRIGHTNESS_CAP = 30
DISPLAY_TYPE = "NONE"
MATRIX_SCROLL = "WEATHER"

# Navigation-light colors (not METAR categories). White is warm lantern, not cool RGB white.
LIGHT_RGB = {
    "W": (255, 236, 180),
    "R": (255, 12, 0),
    "G": (0, 220, 70),
}
CATEGORY_COLOR = {
    "VFR": (0, 255, 0),
    "MVFR": (0, 0, 255),
    "IFR": (255, 0, 0),
    "LIFR": (255, 0, 128),
    "": (255, 255, 255),
}

# Same present-weather tokens as MarksMetarMap (exact METAR words).
WX_TAGS = (
    "BR", "-RA", "RA", "+RA", "-SN", "SN", "+SN", "SHSN", "LTG", "DSNT",
    "WND", "FG", "FZFG", "FZFD", "CLR", "CC", "CA", "CG", "VCTS", "TS",
    "$", "FC", "+FC", "TORNADO",
)
WX_SKIP_SCROLL = ("CLR", "$")

# Static ticker colors from MetarMap LED effects (rain cyan, snow/fog white, lightning yellow).
WX_COLOR = {
    "BR": (0, 255, 240),
    "-RA": (0, 255, 139),
    "RA": (0, 255, 139),
    "+RA": (0, 255, 139),
    "-SN": (255, 255, 255),
    "SN": (255, 255, 255),
    "+SN": (255, 255, 255),
    "SHSN": (255, 255, 255),
    "LTG": (255, 255, 0),
    "DSNT": (255, 255, 0),
    "WND": (255, 247, 0),
    "FG": (255, 255, 255),
    "FZFG": (255, 255, 255),
    "FZFD": (0, 255, 180),
    "CC": (255, 255, 255),
    "CA": (255, 255, 255),
    "CG": (255, 255, 255),
    "VCTS": (255, 255, 255),
    "TS": (255, 0, 0),
    "FC": (255, 0, 0),
    "+FC": (255, 0, 0),
    "TORNADO": (255, 0, 0),
}

# Ticker words. Matching still uses the METAR token.
WX_LABEL = {
    "BR": "Mist",
    "-RA": "Light Rain",
    "RA": "Rain",
    "+RA": "Heavy Rain",
    "-SN": "Light Snow",
    "SN": "Snow",
    "+SN": "Heavy Snow",
    "SHSN": "Snow Showers",
    "LTG": "Lightning",
    "DSNT": "Distant",
    "WND": "Wind",
    "FG": "Fog",
    "FZFG": "Freezing Fog",
    "FZFD": "Freezing",
    "CC": "Cloud Lightning",
    "CA": "Air Lightning",
    "CG": "Ground Lightning",
    "VCTS": "Thunder Nearby",
    "TS": "Thunderstorm",
    "FC": "Funnel Cloud",
    "+FC": "Tornado",
    "TORNADO": "Tornado",
}

WX_FOG = ("FG", "BR", "FZFG", "HZ")
WX_RAIN = ("-RA", "RA", "+RA", "-DZ", "DZ", "+DZ", "SHRA")
WX_SNOW = ("-SN", "SN", "+SN", "SHSN", "-PE", "PE")
WX_STORM = ("TS", "VCTS", "FC", "+FC", "TORNADO")
WX_LTG = ("LTG", "DSNT", "CC", "CA", "CG")
WX_WIND = ("WND",)

lighthouses = []
strip = None
wlan = None
http_sock = None
http_sock_8080 = None
status = {
    "version": FIRMWARE_VERSION,
    "ip": None,
    "last_fetch": None,
    "stations": {},
}
update_available = False
update_info = None
_ota_btn = None
_ota_down_ms = None
sleep_cfg = {
    "sleep_enabled": False,
    "sleep_at_hour": 22,
    "sleep_at_minute": 0,
    "wake_at_hour": 6,
    "wake_at_minute": 0,
    "timezone_offset_hours": -5,
    "weekend_mode_enabled": False,
    "weekend_off_weekday": 4,
    "weekend_off_hour": 18,
    "weekend_off_minute": 0,
    "weekend_on_weekday": 0,
    "weekend_on_hour": 6,
    "weekend_on_minute": 0,
}
clock_trusted = False
ldr_adc = None
_ldr_filt = None
_ldr_out = None
_ldr_raw = 0
_ldr_last_ms = 0
_ldr_print_ms = 0
# GPIO 21 HIGH -- LDR -- GPIO 26 -- 10k -- GND. Wide default so boot uses the
# real ADC immediately; ends expand if a reading goes outside.
LDR_ADC_LO = 500
LDR_ADC_HI = 50000
LDR_SAMPLE_MS = 400
LDR_TAU_S = 0.4
_adc_lo = LDR_ADC_LO
_adc_hi = LDR_ADC_HI


def _clamp(n, lo, hi):
    return max(lo, min(hi, n))


def load_config():
    global LED_PIN, NUM_LEDS, BRIGHTNESS, MIN_BRIGHTNESS, MAX_BRIGHTNESS, BEACON_PULSE, CYCLE_DELAY, sleep_cfg, DISPLAY_TYPE, MATRIX_SCROLL, MATRIX_SCROLL_SPEED, _ldr_filt, _ldr_out, _ldr_last_ms, _adc_lo, _adc_hi
    old_min = MIN_BRIGHTNESS
    old_max = MAX_BRIGHTNESS
    try:
        with open(CONFIG_FILE, "r") as f:
            cfg = json.load(f)
    except Exception:
        cfg = {}
    LED_PIN = _clamp(int(cfg.get("led_pin", 0)), 0, 28)
    NUM_LEDS = _clamp(int(cfg.get("num_leds", 13)), 1, 300)
    kind = str(cfg.get("display_type", "NONE")).strip().upper()
    DISPLAY_TYPE = kind if kind in ("NONE", "OLED", "LED_MATRIX") else "NONE"
    scroll = str(cfg.get("matrix_scroll", "WEATHER")).strip().upper()
    MATRIX_SCROLL = scroll if scroll in ("WEATHER", "ALL") else "WEATHER"
    try:
        MATRIX_SCROLL_SPEED = _clamp(int(cfg.get("matrix_scroll_speed", 7)), 1, 10)
    except Exception:
        MATRIX_SCROLL_SPEED = 7
    BRIGHTNESS = max(0.02, min(1.0, float(cfg.get("brightness", 0.18))))
    if "max_brightness" in cfg:
        MAX_BRIGHTNESS = _clamp(int(cfg.get("max_brightness", 18)), 1, BRIGHTNESS_CAP)
    else:
        MAX_BRIGHTNESS = _clamp(int(round(BRIGHTNESS * 255)), 1, BRIGHTNESS_CAP)
    MIN_BRIGHTNESS = _clamp(int(cfg.get("min_brightness", 0)), 0, BRIGHTNESS_CAP)
    if MIN_BRIGHTNESS > MAX_BRIGHTNESS:
        MIN_BRIGHTNESS = MAX_BRIGHTNESS
    BRIGHTNESS = max(0.02, min(1.0, MAX_BRIGHTNESS / 255.0))
    BEACON_PULSE = bool(cfg.get("beacon_pulse", True))
    CYCLE_DELAY = _clamp(int(cfg.get("cycle_delay", 300)), 30, 3600)
    for k in sleep_cfg:
        if k in cfg:
            sleep_cfg[k] = cfg[k]
    print("Display", DISPLAY_TYPE, "scroll", MATRIX_SCROLL, "speed", MATRIX_SCROLL_SPEED, "bright", MIN_BRIGHTNESS, "-", MAX_BRIGHTNESS)
    if old_min != MIN_BRIGHTNESS or old_max != MAX_BRIGHTNESS:
        _ldr_filt = None
        _ldr_out = None
        _ldr_last_ms = 0
        _adc_lo = LDR_ADC_LO
        _adc_hi = LDR_ADC_HI
    return cfg


def force_ap_held():
    pin = machine.Pin(FORCE_AP_BUTTON_PIN, machine.Pin.IN, machine.Pin.PULL_UP)
    if pin.value() == 0:
        print("AP button down at startup")
        return True
    return False


def consume_force_ap_flag():
    try:
        if FORCE_AP_FLAG in os.listdir():
            os.remove(FORCE_AP_FLAG)
            print("Setup button held — opening AP")
            return True
    except Exception as e:
        print("force AP flag:", e)
    return False


def request_force_ap_reboot():
    try:
        with open(FORCE_AP_FLAG, "w") as f:
            f.write("1")
    except Exception as e:
        print("force AP flag write:", e)
    machine.reset()


def connect_wifi(cfg):
    global wlan
    ssid = cfg.get("ssid")
    password = cfg.get("password")
    if not ssid:
        return False
    # Leftover AP from setup mode or a previous Thonny run blocks STA (CYW43 EPERM).
    try:
        ap = network.WLAN(network.AP_IF)
        if ap.active():
            ap.active(False)
            time.sleep_ms(500)
    except Exception:
        pass
    wlan = network.WLAN(network.STA_IF)
    for attempt in range(3):
        try:
            try:
                wlan.active(False)
            except Exception:
                pass
            time.sleep_ms(300)
            wlan.active(True)
            time.sleep(1)
            if not wlan.isconnected():
                wlan.connect(ssid, password or "")
                for _ in range(20):
                    if wlan.isconnected():
                        break
                    time.sleep(1)
            if wlan.isconnected():
                status["ip"] = wlan.ifconfig()[0]
                print("WiFi", status["ip"])
                return True
        except OSError as e:
            print("WiFi retry", attempt + 1, e)
            time.sleep_ms(500)
    return False


def load_lighthouses():
    global lighthouses
    try:
        import wifi_manager
        items = wifi_manager.load_lighthouse_list()
        if not items:
            raise ValueError("empty list")
        items.sort(key=lambda x: int(x.get("led", 0)))
        lighthouses = items
        print("Loaded", len(lighthouses), "lighthouses")
    except Exception as e:
        print("lighthouses.json failed:", e)
        print("Re-copy pico/lighthouses.json to the Pico as UTF-8 text.")
        lighthouses = []


def init_strip():
    global strip
    strip = neopixel.NeoPixel(machine.Pin(LED_PIN), NUM_LEDS)
    for i in range(NUM_LEDS):
        strip[i] = (0, 0, 0)
    strip.write()
    print("Strip GPIO", LED_PIN, "x", NUM_LEDS)


def _ldr_level_from_t(t):
    if t is None:
        t = 1.0
    level = t * (MAX_BRIGHTNESS - MIN_BRIGHTNESS) + MIN_BRIGHTNESS
    return _clamp(int(level + 0.5), MIN_BRIGHTNESS, MAX_BRIGHTNESS)


def _pwm_from_slider(level):
    """0 = off. 1-8 stay very dim (raw WS2812 counts). 30 = full."""
    if level <= 0:
        return 0
    if level <= 8:
        return int(level)
    return _clamp(int(8 + (level - 8) * (255 - 8) / (BRIGHTNESS_CAP - 8)), 0, 255)


def _ldr_refresh():
    """Map this board's live dark..bright ADC onto min..max so min=2 is actually 2 when covered."""
    global _ldr_filt, _ldr_out, _ldr_raw, _ldr_last_ms, _ldr_print_ms, _adc_lo, _adc_hi
    if ldr_adc is None:
        _ldr_out = float(MAX_BRIGHTNESS)
        return int(_ldr_out)
    now = time.ticks_ms()
    if _ldr_last_ms and _ldr_filt is not None and time.ticks_diff(now, _ldr_last_ms) < LDR_SAMPLE_MS:
        out = _ldr_level_from_t(_ldr_filt)
        _ldr_out = float(out)
        return out
    _ldr_last_ms = now
    try:
        acc = 0
        for _ in range(4):
            acc += ldr_adc.read_u16()
        raw = acc >> 2
    except Exception:
        _ldr_out = float(MAX_BRIGHTNESS)
        return int(_ldr_out)
    _ldr_raw = raw
    if raw < _adc_lo:
        _adc_lo = raw
    if raw > _adc_hi:
        _adc_hi = raw
    span = float(_adc_hi - _adc_lo)
    if span < 1:
        span = 1
    t = (raw - _adc_lo) / span
    if t < 0.0:
        t = 0.0
    elif t > 1.0:
        t = 1.0
    _ldr_filt = t
    out = _ldr_level_from_t(_ldr_filt)
    _ldr_out = float(out)
    if time.ticks_diff(now, _ldr_print_ms) > 2500:
        _ldr_print_ms = now
        print("LDR", raw, "lo", int(_adc_lo), "hi", int(_adc_hi), "->", out, "min", MIN_BRIGHTNESS, "max", MAX_BRIGHTNESS)
    return out


def _ldr_level():
    return _ldr_refresh()


def scale_color(rgb, brightness=None):
    r, g, b = rgb
    peak = max(r, g, b)
    if peak <= 0:
        return (0, 0, 0)
    if brightness is not None:
        pwm = _clamp(int(round(float(brightness) * 255)), 0, 255)
    else:
        pwm = _pwm_from_slider(_ldr_level())
    return (
        _clamp(r * pwm // peak, 0, 255),
        _clamp(g * pwm // peak, 0, 255),
        _clamp(b * pwm // peak, 0, 255),
    )


def paint_all(rgb):
    c = scale_color(rgb)
    for i in range(NUM_LEDS):
        strip[i] = c
    strip.write()


def startup_chase():
    count = min(NUM_LEDS, max(1, len(lighthouses)))
    for i in range(count):
        for j in range(NUM_LEDS):
            strip[j] = (0, 0, 0)
        lh = lighthouses[i] if i < len(lighthouses) else None
        color = light_color(lh) if lh else (255, 180, 60)
        strip[i] = scale_color(color, 0.28)
        strip.write()
        time.sleep_ms(160)
    paint_all((0, 0, 0))


def _metar_id(sid):
    """AWC wants K + 3-char FAA ids (3D2 -> K3D2, 2P2 -> K2P2)."""
    sid = str(sid or "").strip().upper()
    if not sid:
        return ""
    if len(sid) == 3:
        return "K" + sid
    return sid


def unique_stations():
    ids = []
    for lh in lighthouses:
        sid = _metar_id(lh.get("metar", ""))
        fb = _metar_id(lh.get("metar_fallback", "KSUE"))
        if sid and sid not in ids:
            ids.append(sid)
        if fb and fb not in ids:
            ids.append(fb)
    return ids


def _parse_flight_category(raw_text):
    if not raw_text:
        return ""
    raw = raw_text.strip().upper()
    vis_m = 10.0
    ceiling_ft = 10000
    i = raw.find("SM")
    if i > 0:
        start = i
        while start > 0 and (raw[start - 1].isdigit() or raw[start - 1] in "/.M "):
            start -= 1
        tok = raw[start:i].strip()
        if tok.startswith("P") or tok.startswith("M"):
            tok = tok[1:]
        try:
            if "/" in tok:
                if " " in tok:
                    parts = tok.split()
                    whole = int(parts[0]) if parts[0].isdigit() else 0
                    a, b = parts[1].split("/", 1)
                    vis_m = whole + int(a) / max(1, int(b))
                else:
                    a, b = tok.split("/", 1)
                    vis_m = int(a) / max(1, int(b))
            else:
                vis_m = float(tok)
        except Exception:
            pass
    for prefix in ("BKN", "OVC", "VV"):
        idx = 0
        while True:
            idx = raw.find(prefix, idx)
            if idx < 0:
                break
            idx += len(prefix)
            if idx + 3 <= len(raw) and raw[idx:idx + 3].isdigit():
                h = int(raw[idx:idx + 3]) * 100
                if h < ceiling_ft:
                    ceiling_ft = h
            idx += 1
    if ceiling_ft < 500 or vis_m < 1.0:
        return "LIFR"
    if ceiling_ft < 1000 or vis_m < 3.0:
        return "IFR"
    if ceiling_ft < 3000 or vis_m < 5.0:
        return "MVFR"
    return "VFR"


def _metar_body(raw_text):
    """Present-weather side of a METAR only. RMK is history / lightning notes."""
    raw = str(raw_text or "").strip().upper()
    if not raw:
        return ""
    i = raw.find(" RMK")
    if i < 0:
        i = raw.find(" RMK ")
    if i >= 0:
        raw = raw[:i]
    return raw


def wx_codes(raw_text):
    """Present-weather WX_TAGS only (before RMK), for the matrix/OLED ticker."""
    if not raw_text:
        return []
    toks = _metar_body(raw_text).split()
    out = []
    for tag in WX_TAGS:
        if tag in WX_SKIP_SCROLL:
            continue
        if tag in toks:
            out.append(tag)
    return out


def wx_bits(raw_text):
    bits = 0
    if not raw_text:
        return 0
    for tok in _metar_body(raw_text).split():
        if tok in WX_FOG:
            bits |= 1
        elif tok in WX_RAIN:
            bits |= 2
        elif tok in WX_SNOW:
            bits |= 4
        elif tok in WX_STORM or tok in WX_LTG:
            bits |= 8
        elif tok in WX_WIND:
            bits |= 16
    return bits


def _http_get_text(url, timeout=12):
    gc.collect()
    resp = None
    try:
        resp = urequests.get(url, timeout=timeout)
        return resp.text or ""
    finally:
        if resp is not None:
            try:
                resp.close()
            except Exception:
                pass
        gc.collect()


def _station_from_line(line, wanted):
    line = line.strip()
    if not line:
        return None, None
    parts = line.split()
    if not parts:
        return None, None
    first = parts[0].upper()
    if first in ("METAR", "SPECI"):
        station = parts[1].upper() if len(parts) > 1 else ""
    else:
        station = first
    if station in wanted:
        return station, line
    return None, None


def _metar_obs_minutes(line):
    """ddhhmmZ from a METAR line, as minutes, for newest-wins."""
    for tok in str(line).upper().split():
        if len(tok) == 7 and tok.endswith("Z") and tok[:6].isdigit():
            return int(tok[0:2]) * 1440 + int(tok[2:4]) * 60 + int(tok[4:6])
    return -1


def _metar_is_newer(new_line, old_line):
    n = _metar_obs_minutes(new_line)
    o = _metar_obs_minutes(old_line)
    if n < 0:
        return False
    if o < 0:
        return True
    # Month wrap: day 1 after day 28–31.
    if o - n > 20000:
        return True
    if n - o > 20000:
        return False
    return n > o


def _store_metar(found, station, raw, category=""):
    prev = found.get(station)
    if prev and not _metar_is_newer(raw, prev.get("raw") or ""):
        return
    found[station] = {
        "category": category or _parse_flight_category(raw) or "VFR",
        "raw": raw,
        "wx": wx_bits(raw),
    }


def _metar_records(text):
    """One complete METAR/SPECI per item, even if AWC glued several on one line."""
    text = str(text or "").replace("\r", "\n")
    records = []
    for chunk in text.split("\n"):
        chunk = chunk.strip()
        if not chunk:
            continue
        start = 0
        up = chunk.upper()
        while start < len(chunk):
            rest = up[start + 1:]
            nxt_m = rest.find(" METAR ")
            nxt_s = rest.find(" SPECI ")
            nxt = -1
            if nxt_m >= 0 and nxt_s >= 0:
                nxt = min(nxt_m, nxt_s)
            elif nxt_m >= 0:
                nxt = nxt_m
            elif nxt_s >= 0:
                nxt = nxt_s
            if nxt < 0:
                piece = chunk[start:].strip()
                if piece:
                    records.append(piece)
                break
            end = start + 1 + nxt
            piece = chunk[start:end].strip()
            if piece:
                records.append(piece)
            start = end + 1
    return records


def _ingest_raw(text, wanted, found):
    for line in _metar_records(text):
        station, raw = _station_from_line(line, wanted)
        if station:
            _store_metar(found, station, raw)


def _ingest_xml(text, station, found):
    idx = 0
    while True:
        rt_start = text.find("<raw_text>", idx)
        rt_end = text.find("</raw_text>", rt_start)
        if rt_start < 0 or rt_end < 0:
            return
        raw = text[rt_start + 10:rt_end].strip()
        idx = rt_end + 1
        if not raw:
            continue
        next_rt = text.find("<raw_text>", rt_end)
        search_end = next_rt if next_rt >= 0 else len(text)
        fc_start = text.find("<flight_category>", rt_end)
        fc_end = text.find("</flight_category>", fc_start)
        category = ""
        if fc_start >= 0 and fc_end > fc_start and fc_start < search_end:
            category = text[fc_start + 17:fc_end].strip().upper()
        _store_metar(found, station, raw, category)


def fetch_metars():
    global _matrix_need_text, _oled_msgs
    ids = unique_stations()
    if not ids:
        return
    wanted = {}
    for sid in ids:
        wanted[sid] = True
    found = {}
    # No hours = current observation only (hours=1 returns every report in the last hour).
    url = "https://aviationweather.gov/api/data/metar?ids=%s&format=raw" % ",".join(ids)
    print("Fetch", url)
    try:
        _ingest_raw(_http_get_text(url), wanted, found)
    except Exception as e:
        print("METAR bulk failed:", e)
    missing = [sid for sid in ids if sid not in found]
    for sid in missing:
        try:
            one = "https://aviationweather.gov/api/data/metar?ids=%s&format=raw" % sid
            print("Fetch", sid)
            _ingest_raw(_http_get_text(one, timeout=10), wanted, found)
        except Exception as e:
            print("METAR", sid, "raw failed:", e)
        if sid in found:
            continue
        try:
            xml_url = "https://aviationweather.gov/api/data/metar?ids=%s&format=xml" % sid
            _ingest_xml(_http_get_text(xml_url, timeout=10), sid, found)
            if sid in found:
                print("Got", sid, "from XML")
        except Exception as e:
            print("METAR", sid, "xml failed:", e)
    status["stations"] = found
    status["last_fetch"] = time.time()
    _matrix_need_text = True
    _oled_msgs = []
    still = [sid for sid in ids if sid not in found]
    print("Got", len(found), "stations", list(found.keys()))
    for sid in found:
        raw = found[sid].get("raw", "")
        codes = wx_codes(raw)
        print(sid, raw[:72])
        print(" ", "wx", " ".join(WX_LABEL.get(c, c) for c in codes) if codes else "none")
    if still:
        print("No METAR yet:", still)


def station_for(lh):
    stations = status.get("stations") or {}
    primary = _metar_id(lh.get("metar", ""))
    fallback = _metar_id(lh.get("metar_fallback", "KSUE"))
    if primary in stations:
        return stations[primary]
    raw = str(lh.get("metar", "")).strip().upper()
    if raw in stations:
        return stations[raw]
    if fallback in stations:
        return stations[fallback]
    return None


def local_parts():
    """Local weekday (0=Mon), hour, minute using timezone_offset_hours from UTC."""
    t = time.gmtime(time.time() + int(sleep_cfg.get("timezone_offset_hours", -5)) * 3600)
    return t[6], t[3], t[4]


def _in_daily_sleep(hour, minute):
    if not sleep_cfg.get("sleep_enabled"):
        return False
    sh = int(sleep_cfg.get("sleep_at_hour", 22))
    sm = int(sleep_cfg.get("sleep_at_minute", 0))
    wh = int(sleep_cfg.get("wake_at_hour", 6))
    wm = int(sleep_cfg.get("wake_at_minute", 0))
    now = hour * 60 + minute
    sleep_at = sh * 60 + sm
    wake_at = wh * 60 + wm
    if sleep_at == wake_at:
        return False
    if sleep_at < wake_at:
        return sleep_at <= now < wake_at
    return now >= sleep_at or now < wake_at


def _week_minutes(wd, hour, minute):
    return int(wd) * 1440 + int(hour) * 60 + int(minute)


def _in_weekend(weekday, hour, minute):
    if not sleep_cfg.get("weekend_mode_enabled"):
        return False
    start = _week_minutes(
        sleep_cfg.get("weekend_off_weekday", 4),
        sleep_cfg.get("weekend_off_hour", 18),
        sleep_cfg.get("weekend_off_minute", 0),
    )
    end = _week_minutes(
        sleep_cfg.get("weekend_on_weekday", 0),
        sleep_cfg.get("weekend_on_hour", 6),
        sleep_cfg.get("weekend_on_minute", 0),
    )
    cur = _week_minutes(weekday, hour, minute)
    if start < end:
        return start <= cur < end
    if start > end:
        return cur >= start or cur < end
    return False


def in_sleep_window():
    if not clock_trusted:
        return False
    wd, h, m = local_parts()
    return _in_weekend(wd, h, m) or _in_daily_sleep(h, m)


def sync_ntp():
    global clock_trusted
    if ntptime is None:
        return
    try:
        ntptime.settime()
        clock_trusted = True
        print("NTP ok")
    except Exception as e:
        print("NTP failed:", e)


def light_spec(lh):
    spec = lh.get("light")
    return spec if isinstance(spec, dict) else {}


def light_color(lh):
    code = str(light_spec(lh).get("color", "W")).upper()
    return LIGHT_RGB.get(code, LIGHT_RGB["W"])


def characteristic_on(lh, now_ms):
    """True when this aid would be lit, using on_s/off_s pairs that fill period_s."""
    spec = light_spec(lh)
    if not spec:
        return True
    on_s = spec.get("on_s") or [1.0]
    off_s = spec.get("off_s") or [0.0]
    period_s = float(spec.get("period_s") or 0)
    if period_s <= 0:
        period_s = 0
        n = max(len(on_s), len(off_s))
        for i in range(n):
            period_s += float(on_s[i] if i < len(on_s) else 0)
            period_s += float(off_s[i] if i < len(off_s) else 0)
    if period_s <= 0:
        return True
    # Fixed light: no eclipse
    if len(off_s) == 1 and float(off_s[0]) <= 0 and len(on_s) == 1:
        return True
    t = (now_ms % int(period_s * 1000)) / 1000.0
    cursor = 0.0
    n = max(len(on_s), len(off_s))
    for i in range(n):
        on = float(on_s[i] if i < len(on_s) else 0)
        if t < cursor + on:
            return on > 0
        cursor += on
        off = float(off_s[i] if i < len(off_s) else 0)
        if t < cursor + off:
            return False
        cursor += off
    return False


def render_frame():
    if in_sleep_window():
        paint_all((0, 0, 0))
        return
    now_ms = time.ticks_ms()
    _ldr_refresh()
    used = {}
    for lh in lighthouses:
        led_i = int(lh.get("led", 0))
        if led_i < 0 or led_i >= NUM_LEDS:
            continue
        if lh.get("skip"):
            strip[led_i] = (0, 0, 0)
            used[led_i] = True
            continue
        if characteristic_on(lh, now_ms):
            strip[led_i] = scale_color(light_color(lh))
        else:
            strip[led_i] = (0, 0, 0)
        used[led_i] = True
    for i in range(NUM_LEDS):
        if i not in used:
            strip[i] = (0, 0, 0)
    strip.write()


def _listen_http(port):
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("0.0.0.0", port))
    s.listen(2)
    # After HTTPS (METAR/OTA), settimeout on listen sockets is often ignored on
    # Pico W and accept() blocks forever — Thonny looks stuck, lights freeze.
    try:
        s.setblocking(False)
    except Exception:
        s.settimeout(0)
    return s


def start_http():
    global http_sock, http_sock_8080
    http_sock = _listen_http(80)
    try:
        http_sock_8080 = _listen_http(8080)
        print("HTTP on port 80 and 8080")
    except Exception as e:
        http_sock_8080 = None
        print("HTTP on port 80 (8080 failed:", e, ")")
    ip = status.get("ip")
    if ip:
        print("Open http://%s/  or  http://%s:8080/" % (ip, ip))


def lighthouse_payload():
    return list(lighthouses)


def apply_lighthouse_list(items):
    global lighthouses, NUM_LEDS
    if not isinstance(items, list):
        return 0
    import wifi_manager
    cleaned = wifi_manager.save_lighthouse_list(items)
    lighthouses = cleaned
    n = max(1, len(cleaned))
    if n != NUM_LEDS:
        NUM_LEDS = n
        init_strip()
        try:
            cfg = load_config()
            cfg["num_leds"] = n
            with open(CONFIG_FILE, "w") as cf:
                json.dump(cfg, cf)
        except Exception as e:
            print("num_leds save:", e)
    print("Saved", len(cleaned), "lighthouses")
    return len(cleaned)


def read_http(conn):
    conn.settimeout(2)
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = conn.recv(1024)
        if not chunk:
            break
        data += chunk
        if len(data) > 16384:
            break
    if b"\r\n\r\n" not in data:
        return data.decode(), ""
    header, body = data.split(b"\r\n\r\n", 1)
    clen = 0
    for line in header.split(b"\r\n"):
        if line.lower().startswith(b"content-length:"):
            try:
                clen = int(line.split(b":", 1)[1].strip())
            except Exception:
                clen = 0
    while len(body) < clen:
        more = conn.recv(1024)
        if not more:
            break
        body += more
    try:
        return header.decode(), body.decode()
    except Exception:
        return "", ""


def _http_send(conn, content_type, body, status="200 OK"):
    if isinstance(body, str):
        body = body.encode()
    hdr = (
        "HTTP/1.1 %s\r\nContent-Type: %s\r\nContent-Length: %d\r\n"
        "Connection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        % (status, content_type, len(body))
    )
    data = hdr.encode() + body
    while data:
        sent = conn.send(data)
        if not sent:
            break
        data = data[sent:]


def _handle_start_update(conn):
    global update_available, update_info
    try:
        import updater
        if update_available and update_info:
            has_update = True
            version_info = update_info
        else:
            has_update, version_info = updater.check_for_new_version(FIRMWARE_VERSION)
            update_available = has_update
            update_info = version_info
        if has_update and version_info:
            _http_send(conn, "text/plain", "Installing...", "200 OK")
            try:
                conn.close()
            except Exception:
                pass
            time.sleep_ms(200)
            updater.install_pending_update(version_info)
            return
        print("OTA POST /start-update: no newer firmware")
        _http_send(conn, "text/plain", "No update available.", "409 Conflict")
    except Exception as e:
        print("OTA POST /start-update error:", e)
        try:
            _http_send(conn, "text/plain", "Update error.", "500 Internal Server Error")
        except Exception:
            pass


def check_for_ota():
    global update_available, update_info
    print("OTA: checking for newer firmware...")
    try:
        import updater
        has_update, version_info = updater.check_for_new_version(FIRMWARE_VERSION)
        update_available = has_update
        update_info = version_info
        if has_update:
            print("OTA: new version", version_info.get("version"), "- install from the app, browser, or a short tap on the setup button")
            try:
                paint_all((255, 160, 0))
                time.sleep(2)
            except Exception:
                pass
        else:
            print("OTA: device firmware current (or check unreachable)")
    except Exception as e:
        print("OTA check error:", e)
        update_available = False
        update_info = None


def init_ota_button():
    global _ota_btn
    try:
        _ota_btn = machine.Pin(FORCE_AP_BUTTON_PIN, machine.Pin.IN, machine.Pin.PULL_UP)
    except Exception as e:
        _ota_btn = None
        print("OTA button skipped:", e)


def poll_ota_button():
    global _ota_down_ms
    if _ota_btn is None:
        return
    pressed = _ota_btn.value() == 0
    now = time.ticks_ms()
    if pressed:
        if _ota_down_ms is None:
            _ota_down_ms = now
        elif time.ticks_diff(now, _ota_down_ms) >= 3000:
            print("Setup button held 3s — reboot to AP")
            _ota_down_ms = None
            request_force_ap_reboot()
        return
    if _ota_down_ms is None:
        return
    held = time.ticks_diff(now, _ota_down_ms)
    _ota_down_ms = None
    if not update_available or held < 50 or held > 800:
        return
    print("OTA: setup button tap - installing")
    try:
        import updater
        updater.install_pending_update(update_info)
    except Exception as e:
        print("OTA button install:", e)


def _request_path(line):
    parts = line.split()
    if len(parts) < 2:
        return "/"
    return parts[1].split("?", 1)[0]


def handle_http():
    for sock in (http_sock, http_sock_8080):
        if sock is None:
            continue
        try:
            conn, _addr = sock.accept()
        except OSError:
            continue
        _handle_conn(conn)


def _handle_conn(conn):
    global _matrix_need_text
    try:
        header, req_body = read_http(conn)
        line = header.split("\r\n", 1)[0] if header else ""
        path = _request_path(line)
        method = line.split(" ", 1)[0] if line else "GET"
        if method == "OPTIONS":
            import wifi_manager
            wifi_manager.send_options(conn)
        elif method == "GET" and path == "/help":
            import wifi_manager
            wifi_manager.send_static_file(
                conn,
                "help.html",
                "text/html; charset=utf-8",
                "<p>Copy help.html to the Pico, then reload.</p>",
            )
        elif method == "GET" and path == "/catalog":
            import wifi_manager
            wifi_manager.send_json_file(conn, "catalog.json", {"ok": False, "lighthouses": []})
        elif method == "GET" and path == "/lighthouses-defaults":
            import wifi_manager
            wifi_manager.send_json_file(conn, "lighthouses_defaults.json", {"ok": False, "lighthouses": []})
        elif method == "GET" and path == "/lighthouses":
            _http_send(conn, "application/json", json.dumps({"ok": True, "lighthouses": lighthouse_payload()}))
        elif method == "POST" and path == "/lighthouses":
            try:
                import wifi_manager
                if req_body and req_body.lstrip()[:1] == "{":
                    payload = json.loads(req_body)
                else:
                    payload = wifi_manager.parse_body("\r\n\r\n" + (req_body or ""))
                items = payload.get("lighthouses") if isinstance(payload, dict) else payload
                if isinstance(items, str):
                    items = json.loads(items)
                count = apply_lighthouse_list(items)
                if req_body and req_body.lstrip()[:1] == "{":
                    _http_send(conn, "application/json", json.dumps({"ok": True, "count": count, "message": "saved"}))
                else:
                    import wifi_manager
                    _http_send(conn, "text/html; charset=utf-8", wifi_manager.setup_page())
            except Exception as e:
                _http_send(conn, "application/json", json.dumps({"ok": False, "message": str(e)}))
        elif method == "GET" and path == "/status":
            _http_send(conn, "application/json", json.dumps({
                "ok": True,
                "name": "GreatLakesLighthouses",
                "version": FIRMWARE_VERSION,
                "ip": status.get("ip"),
                "last_fetch": status.get("last_fetch"),
                "stations": list((status.get("stations") or {}).keys()),
                "lights": len(lighthouses),
                "update_available": update_available,
                "update_version": (update_info or {}).get("version", "") if update_available else "",
            }))
        elif method == "GET" and path == "/config":
            import wifi_manager
            out = wifi_manager.merge_defaults(load_config())
            out.pop("password", None)
            out["ok"] = True
            out["version"] = FIRMWARE_VERSION
            out["name"] = "GreatLakesLighthouses"
            out["update_available"] = update_available
            if update_info:
                out["update_version"] = update_info.get("version", "")
            _http_send(conn, "application/json", json.dumps(out))
        elif method == "POST" and path == "/start-update":
            _handle_start_update(conn)
        elif method == "POST" and path in ("/update-config", "/configure"):
            import wifi_manager
            is_json = bool(req_body) and req_body.lstrip()[:1] == "{"
            try:
                if is_json:
                    payload = json.loads(req_body) if req_body else {}
                else:
                    payload = wifi_manager.parse_body("\r\n\r\n" + (req_body or ""))
                    if "beacon_pulse" not in payload:
                        payload["beacon_pulse"] = False
                    if "sleep_enabled" not in payload:
                        payload["sleep_enabled"] = False
                    if "weekend_mode_enabled" not in payload:
                        payload["weekend_mode_enabled"] = False
                if not isinstance(payload, dict):
                    raise ValueError("object required")
                old_pin = LED_PIN
                cfg = wifi_manager.merge_defaults(load_config())
                wifi_manager.apply_fields(cfg, payload)
                wifi_manager.save_config(cfg)
                load_config()
                _matrix_need_text = True
                if LED_PIN != old_pin:
                    init_strip()
                reboot = (not is_json) or str(payload.get("reboot", "")).lower() in ("1", "true", "yes")
                if is_json:
                    _http_send(conn, "application/json", json.dumps({"ok": True, "message": "saved", "version": FIRMWARE_VERSION}))
                else:
                    _http_send(conn, "text/html; charset=utf-8", wifi_manager.success_page(True, status.get("ip")))
                if reboot:
                    conn.close()
                    time.sleep(1)
                    machine.reset()
                    return
            except Exception as e:
                if is_json:
                    _http_send(conn, "application/json", json.dumps({"ok": False, "message": str(e)}))
                else:
                    _http_send(conn, "text/html; charset=utf-8", "<p>Save failed: %s</p>" % e)
        elif method == "POST" and path == "/reboot":
            _http_send(conn, "application/json", json.dumps({"ok": True, "message": "rebooting"}))
            conn.close()
            time.sleep(1)
            machine.reset()
            return
        elif method == "GET" and path in ("/", "/index.html"):
            import wifi_manager
            _http_send(conn, "text/html; charset=utf-8", wifi_manager.setup_page())
        elif method == "GET" and path == "/favicon.ico":
            _http_send(conn, "text/plain", "")
        else:
            _http_send(conn, "application/json", json.dumps({"ok": True, "see": ["/status", "/lighthouses", "/catalog", "/config"]}))
    except Exception as e:
        print("HTTP", e)
        try:
            _http_send(conn, "text/plain", "HTTP error: %s" % e)
        except Exception:
            pass
    try:
        conn.close()
    except Exception:
        pass


oled = None
_oled_wri = None
_oled_last_ms = 0
_oled_msgs = []
_oled_msg_i = 0
_oled_x = 128
_oled_blank_until = 0
_matrix_need_text = True
_matrix_ip_done = False
_matrix_idle_until = 0
_matrix_idle_pass = False


def init_matrix():
    global _matrix_need_text
    if DISPLAY_TYPE != "LED_MATRIX":
        return
    try:
        import led_matrix
        if led_matrix.init():
            _matrix_need_text = True
    except Exception as e:
        print("LED matrix skipped:", e)


def _matrix_light_segments():
    segs = []
    weather_only = MATRIX_SCROLL == "WEATHER"
    first = True
    for lh in lighthouses:
        if lh.get("skip"):
            continue
        name = str(lh.get("short_name") or lh.get("name") or "").strip()
        if not name:
            continue
        info = station_for(lh) or {}
        codes = wx_codes(info.get("raw") or "")
        if weather_only and not codes:
            continue
        if not first:
            segs.append(("-", (255, 180, 48)))
        first = False
        cat = str(info.get("category") or "").strip().upper()
        color = CATEGORY_COLOR.get(cat) or CATEGORY_COLOR[""]
        segs.append((name, color))
        for code in codes:
            segs.append((WX_LABEL.get(code, code), WX_COLOR.get(code) or (255, 255, 255)))
    return segs


def refresh_matrix():
    global _matrix_need_text, _matrix_ip_done, _matrix_idle_until, _matrix_idle_pass
    try:
        import led_matrix
    except ImportError:
        return
    if not led_matrix.active():
        return
    led_matrix.set_scroll_ms(197 - 17 * MATRIX_SCROLL_SPEED)
    if in_sleep_window():
        led_matrix.tick(0, True)
        return
    now = time.ticks_ms()
    segs = []
    if not _matrix_ip_done:
        ip = status.get("ip")
        if ip:
            segs.append((str(ip), (255, 180, 48)))
    segs.extend(_matrix_light_segments())
    idle = not segs
    if idle:
        if _matrix_idle_until and time.ticks_diff(now, _matrix_idle_until) < 0:
            return
        segs = [("GREAT LAKES LIGHTHOUSES", MATRIX_IDLE_COLOR)]
    else:
        _matrix_idle_until = 0
    if _matrix_need_text:
        led_matrix.set_segments(segs)
        _matrix_need_text = False
        _matrix_idle_pass = idle
    pwm = _pwm_from_slider(_ldr_level())
    if led_matrix.tick(pwm, False):
        _matrix_need_text = True
        _matrix_ip_done = True
        if _matrix_idle_pass:
            _matrix_idle_until = time.ticks_add(now, MATRIX_IDLE_MS)
            led_matrix.set_segments([])


def init_oled():
    global oled, _oled_wri
    _oled_wri = None
    if DISPLAY_TYPE != "OLED":
        try:
            machine.Pin(OLED_VCC_PIN, machine.Pin.OUT).value(0)
        except Exception:
            pass
        oled = None
        return
    try:
        machine.Pin(OLED_GND_PIN, machine.Pin.OUT).value(0)
        machine.Pin(OLED_VCC_PIN, machine.Pin.OUT).value(1)
        time.sleep_ms(80)
        import ssd1306
        i2c = machine.I2C(0, sda=machine.Pin(OLED_SDA_PIN), scl=machine.Pin(OLED_SCL_PIN), freq=400000)
        oled = ssd1306.SSD1306_I2C(128, 64, i2c)
        oled.contrast(128)
        oled.fill(0)
        # Dual-color 128x64: yellow y=0-15, blue y=16-63. sans18 is ~18px.
        _oled_print_centered(0, "Great Lakes")
        _oled_print(32, "v" + FIRMWARE_VERSION)
        oled.show()
        print("OLED on SDA", OLED_SDA_PIN, "SCL", OLED_SCL_PIN, "font", "sans18" if fonts_available else "8x8")
    except Exception as e:
        oled = None
        _oled_wri = None
        print("OLED skipped:", e)


def _oled_writer():
    global _oled_wri
    if not fonts_available or oled is None:
        return None
    if _oled_wri is None:
        try:
            try:
                _oled_wri = writer.Writer(oled, sans18, verbose=False)
            except TypeError:
                _oled_wri = writer.Writer(oled, sans18)
            if hasattr(_oled_wri, "row_clip"):
                _oled_wri.row_clip = True
        except Exception as e:
            print("OLED writer:", e)
            return None
    return _oled_wri


def _oled_print(y, text):
    text = str(text)
    w = _oled_writer()
    if w is None:
        oled.text(text[:16], 0, y, 1)
        return
    # Same Writer as MarksMetarMap: set_textpos(x, y), not (row, col).
    try:
        w.set_textpos(0, y)
    except Exception:
        try:
            writer.Writer.set_textpos(oled, 0, y)
        except Exception:
            oled.text(text[:16], 0, y, 1)
            return
    w.printstring(text)


def _oled_ch_w(ch):
    w = _oled_writer()
    if w is not None:
        try:
            n = w.stringlen(ch)
            if n > 0:
                return n
        except Exception:
            pass
    return 11 if fonts_available else 8


def _oled_str_w(text):
    n = 0
    for ch in text:
        n += _oled_ch_w(ch)
    return n


def _oled_print_at(x, y, text):
    # Draw only glyphs that fit on this row. Writer wraps a too-wide
    # character onto the next line (left side, under the scroll).
    w = _oled_writer()
    cx = x
    for ch in text:
        cw = _oled_ch_w(ch)
        nxt = cx + cw
        if nxt <= 0:
            cx = nxt
            continue
        if cx >= 128:
            break
        if cx >= 0 and nxt <= 128:
            if w is not None:
                try:
                    w.set_textpos(cx, y)
                    w.printstring(ch)
                except Exception:
                    pass
            else:
                oled.text(ch, cx, y, 1)
        cx = nxt


def _oled_print_centered(y, text):
    text = str(text)
    tw = _oled_str_w(text)
    x = 0 if tw >= 128 else (128 - tw) // 2
    _oled_print_at(x, y, text)


def _oled_messages():
    msgs = []
    if not _matrix_ip_done:
        ip = status.get("ip")
        if ip:
            msgs.append(("IP", str(ip)))
    cur = []
    for text, _color in _matrix_light_segments():
        if text == "-":
            if cur:
                msgs.append((cur[0], "  ".join(cur)))
                cur = []
            continue
        cur.append(text)
    if cur:
        msgs.append((cur[0], "  ".join(cur)))
    if not msgs:
        msgs.append(("Great Lakes", "GREAT LAKES LIGHTHOUSES"))
    return msgs


def refresh_oled():
    global _oled_last_ms, _oled_msgs, _oled_msg_i, _oled_x, _oled_blank_until, _matrix_ip_done
    if oled is None:
        return
    now = time.ticks_ms()
    if in_sleep_window():
        if _oled_last_ms and time.ticks_diff(now, _oled_last_ms) < 1000:
            return
        _oled_last_ms = now
        try:
            oled.fill(0)
            _oled_print_centered(0, "Sleep")
            oled.show()
        except Exception as e:
            print("OLED:", e)
        return
    if _oled_blank_until and time.ticks_diff(now, _oled_blank_until) < 0:
        return
    if _oled_blank_until:
        _oled_blank_until = 0
        _oled_x = 128
    step_ms = max(40, 140 - 10 * MATRIX_SCROLL_SPEED)
    if _oled_last_ms and time.ticks_diff(now, _oled_last_ms) < step_ms:
        return
    _oled_last_ms = now
    try:
        if not _oled_msgs or _oled_msg_i >= len(_oled_msgs):
            _oled_msgs = _oled_messages()
            _oled_msg_i = 0
            _oled_x = 128
        title, msg = _oled_msgs[_oled_msg_i]
        tw = _oled_str_w(msg)
        oled.fill(0)
        _oled_print_centered(0, title)
        if _oled_x < 128 and _oled_x + tw > 0:
            _oled_print_at(_oled_x, 32, msg)
        oled.show()
        _oled_x -= max(2, MATRIX_SCROLL_SPEED)
        if _oled_x + tw < 0:
            oled.fill(0)
            _oled_print_centered(0, title)
            oled.show()
            pause = MATRIX_IDLE_MS if msg == "GREAT LAKES LIGHTHOUSES" else 800
            _oled_blank_until = time.ticks_add(now, pause)
            _oled_msg_i += 1
            _oled_x = 128
            if _oled_msg_i >= len(_oled_msgs):
                _oled_msgs = []
                _matrix_ip_done = True
    except Exception as e:
        print("OLED:", e)


def init_ldr():
    global ldr_adc
    try:
        machine.Pin(LDR_DRIVE_PIN, machine.Pin.OUT).value(1)
        time.sleep_ms(500)
        ldr_adc = machine.ADC(0)
        level = _ldr_refresh()
        print("LDR drive GPIO", LDR_DRIVE_PIN, "HIGH, ADC0 GPIO", LDR_ADC_PIN, "now", _ldr_raw, "->", level)
    except Exception as e:
        ldr_adc = None
        print("LDR skipped:", e)


def main():
    print("Great Lakes Lighthouses", FIRMWARE_VERSION)
    cfg = load_config()
    held = consume_force_ap_flag() or force_ap_held()
    if held or not cfg.get("ssid"):
        import wifi_manager
        wifi_manager.start(force_ap=held or not cfg.get("ssid"))
        return
    if not connect_wifi(cfg):
        import wifi_manager
        wifi_manager.start(force_ap=True)
        return
    init_strip()
    load_lighthouses()
    init_ldr()
    init_oled()
    init_matrix()
    startup_chase()
    paint_all((40, 30, 0))
    sync_ntp()
    fetch_metars()
    init_ota_button()
    check_for_ota()
    gc.collect()
    start_http()
    print("Running")
    last_fetch = time.time()
    while True:
        try:
            handle_http()
            poll_ota_button()
            now = time.time()
            if now - last_fetch >= CYCLE_DELAY:
                if not in_sleep_window():
                    fetch_metars()
                last_fetch = now
                gc.collect()
            render_frame()
            refresh_oled()
            refresh_matrix()
        except KeyboardInterrupt:
            raise
        except Exception as e:
            print("loop:", e)
        time.sleep_ms(20)


main()
