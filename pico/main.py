import gc
import json
import machine
import neopixel
import network
import socket
import utime as time

try:
    import urequests
except ImportError:
    import requests as urequests

try:
    import ntptime
except ImportError:
    ntptime = None

FIRMWARE_VERSION = "0.5.0"
CONFIG_FILE = "wifi_config.json"
LIGHTHOUSE_FILE = "lighthouses.json"
FORCE_AP_BUTTON_PIN = 15
LED_PIN = 0
NUM_LEDS = 13
BRIGHTNESS = 0.18
MIN_BRIGHTNESS = 2
MAX_BRIGHTNESS = 46
BEACON_PULSE = True
CYCLE_DELAY = 300
LDR_DRIVE_PIN = 21
LDR_ADC_PIN = 26

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


def _clamp(n, lo, hi):
    return max(lo, min(hi, n))


def load_config():
    global LED_PIN, NUM_LEDS, BRIGHTNESS, MIN_BRIGHTNESS, MAX_BRIGHTNESS, BEACON_PULSE, CYCLE_DELAY, sleep_cfg
    try:
        with open(CONFIG_FILE, "r") as f:
            cfg = json.load(f)
    except Exception:
        cfg = {}
    LED_PIN = _clamp(int(cfg.get("led_pin", 0)), 0, 28)
    NUM_LEDS = _clamp(int(cfg.get("num_leds", 13)), 1, 300)
    BRIGHTNESS = max(0.02, min(1.0, float(cfg.get("brightness", 0.18))))
    if "max_brightness" in cfg:
        MAX_BRIGHTNESS = _clamp(int(cfg.get("max_brightness", 46)), 1, 255)
    else:
        MAX_BRIGHTNESS = _clamp(int(round(BRIGHTNESS * 255)), 1, 255)
    MIN_BRIGHTNESS = _clamp(int(cfg.get("min_brightness", 2)), 0, 255)
    if MIN_BRIGHTNESS > MAX_BRIGHTNESS:
        MIN_BRIGHTNESS = MAX_BRIGHTNESS
    BRIGHTNESS = max(0.02, min(1.0, MAX_BRIGHTNESS / 255.0))
    BEACON_PULSE = bool(cfg.get("beacon_pulse", True))
    CYCLE_DELAY = _clamp(int(cfg.get("cycle_delay", 300)), 30, 3600)
    for k in sleep_cfg:
        if k in cfg:
            sleep_cfg[k] = cfg[k]
    return cfg


def force_ap_held():
    pin = machine.Pin(FORCE_AP_BUTTON_PIN, machine.Pin.IN, machine.Pin.PULL_UP)
    if pin.value() != 0:
        return False
    print("AP button held — wait 3s")
    t0 = time.ticks_ms()
    while pin.value() == 0:
        if time.ticks_diff(time.ticks_ms(), t0) >= 3000:
            return True
        time.sleep_ms(20)
    return False


def connect_wifi(cfg):
    global wlan
    ssid = cfg.get("ssid")
    password = cfg.get("password")
    if not ssid:
        return False
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    if not wlan.isconnected():
        wlan.connect(ssid, password)
        for _ in range(20):
            if wlan.isconnected():
                break
            time.sleep(1)
    if wlan.isconnected():
        status["ip"] = wlan.ifconfig()[0]
        print("WiFi", status["ip"])
        return True
    return False


def load_lighthouses():
    global lighthouses
    try:
        with open(LIGHTHOUSE_FILE, "r") as f:
            raw = f.read()
        if raw.startswith("\ufeff"):
            raw = raw[1:]
        data = json.loads(raw)
        items = data.get("lighthouses", []) if isinstance(data, dict) else data
        if not isinstance(items, list):
            raise ValueError("lighthouses is not a list")
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


def _ldr_level():
    """0-255. Same mapping as MetarMap: high ADC (dark) -> closer to max."""
    level = MAX_BRIGHTNESS
    if ldr_adc is not None:
        try:
            raw = ldr_adc.read_u16()
            span = max(0, MAX_BRIGHTNESS - MIN_BRIGHTNESS)
            level = int((raw / 65535.0) * span + MIN_BRIGHTNESS)
        except Exception:
            pass
    return _clamp(level, MIN_BRIGHTNESS, MAX_BRIGHTNESS)


def scale_color(rgb, brightness=None):
    if brightness is not None:
        level = _clamp(int(round(float(brightness) * 255)), 0, 255)
    else:
        level = _ldr_level()
    r, g, b = rgb
    return (
        _clamp(int(r * level / 255), 0, 255),
        _clamp(int(g * level / 255), 0, 255),
        _clamp(int(b * level / 255), 0, 255),
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


def wx_bits(raw_text):
    bits = 0
    if not raw_text:
        return 0
    for tok in raw_text.upper().split():
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
    resp = urequests.get(url, timeout=timeout)
    text = resp.text
    resp.close()
    gc.collect()
    return text or ""


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


def _ingest_raw(text, wanted, found):
    for line in text.split("\n"):
        station, raw = _station_from_line(line, wanted)
        if not station:
            continue
        found[station] = {
            "category": _parse_flight_category(raw) or "VFR",
            "raw": raw,
            "wx": wx_bits(raw),
        }


def _ingest_xml(text, station, found):
    rt_start = text.find("<raw_text>")
    rt_end = text.find("</raw_text>", rt_start)
    if rt_start < 0 or rt_end < 0:
        return
    raw = text[rt_start + 10:rt_end].strip()
    if not raw:
        return
    fc_start = text.find("<flight_category>")
    fc_end = text.find("</flight_category>", fc_start)
    category = ""
    if fc_start >= 0 and fc_end > fc_start:
        category = text[fc_start + 17:fc_end].strip().upper()
    found[station] = {
        "category": category or _parse_flight_category(raw) or "VFR",
        "raw": raw,
        "wx": wx_bits(raw),
    }


def fetch_metars():
    ids = unique_stations()
    if not ids:
        return
    wanted = {}
    for sid in ids:
        wanted[sid] = True
    found = {}
    url = "https://aviationweather.gov/api/data/metar?ids=%s&hours=3&format=raw" % ",".join(ids)
    print("Fetch", url)
    try:
        _ingest_raw(_http_get_text(url), wanted, found)
    except Exception as e:
        print("METAR bulk failed:", e)
    missing = [sid for sid in ids if sid not in found]
    for sid in missing:
        try:
            one = "https://aviationweather.gov/api/data/metar?ids=%s&hours=6&format=raw" % sid
            print("Fetch", sid)
            _ingest_raw(_http_get_text(one, timeout=10), wanted, found)
        except Exception as e:
            print("METAR", sid, "raw failed:", e)
        if sid in found:
            continue
        try:
            xml_url = "https://aviationweather.gov/api/data/metar?ids=%s&hours=6&format=xml" % sid
            _ingest_xml(_http_get_text(xml_url, timeout=10), sid, found)
            if sid in found:
                print("Got", sid, "from XML")
        except Exception as e:
            print("METAR", sid, "xml failed:", e)
    status["stations"] = found
    status["last_fetch"] = time.time()
    still = [sid for sid in ids if sid not in found]
    print("Got", len(found), "stations", list(found.keys()))
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
    s.settimeout(0.02)
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
    cleaned = []
    for i, raw in enumerate(items):
        if not isinstance(raw, dict):
            continue
        entry = dict(raw)
        entry["led"] = i
        cleaned.append(entry)
    with open(LIGHTHOUSE_FILE, "w") as f:
        json.dump({"version": 3, "order": "list", "lighthouses": cleaned})
    lighthouses = cleaned
    n = max(1, len(cleaned))
    if n != NUM_LEDS:
        NUM_LEDS = n
        init_strip()
        try:
            cfg = load_config()
            cfg["num_leds"] = n
            with open(CONFIG_FILE, "w") as cf:
                json.dump(cfg)
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


def _http_send(conn, content_type, body):
    if isinstance(body, str):
        body = body.encode()
    hdr = (
        "HTTP/1.1 200 OK\r\nContent-Type: %s\r\nContent-Length: %d\r\n"
        "Connection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        % (content_type, len(body))
    )
    data = hdr.encode() + body
    while data:
        sent = conn.send(data)
        if not sent:
            break
        data = data[sent:]


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
    try:
        header, req_body = read_http(conn)
        line = header.split("\r\n", 1)[0] if header else ""
        path = _request_path(line)
        method = line.split(" ", 1)[0] if line else "GET"
        if method == "GET" and path == "/help":
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
                payload = json.loads(req_body) if req_body else {}
                items = payload.get("lighthouses") if isinstance(payload, dict) else payload
                count = apply_lighthouse_list(items)
                _http_send(conn, "application/json", json.dumps({"ok": True, "count": count, "message": "saved"}))
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
            }))
        elif method == "GET" and path == "/config":
            import wifi_manager
            out = wifi_manager.merge_defaults(load_config())
            out.pop("password", None)
            out["ok"] = True
            out["version"] = FIRMWARE_VERSION
            out["name"] = "GreatLakesLighthouses"
            _http_send(conn, "application/json", json.dumps(out))
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


def init_ldr():
    global ldr_adc
    try:
        machine.Pin(LDR_DRIVE_PIN, machine.Pin.OUT).value(1)
        ldr_adc = machine.ADC(machine.Pin(LDR_ADC_PIN))
        print("LDR on GPIO", LDR_ADC_PIN)
    except Exception as e:
        ldr_adc = None
        print("LDR skipped:", e)


def main():
    print("Great Lakes Lighthouses", FIRMWARE_VERSION)
    cfg = load_config()
    held = force_ap_held()
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
    startup_chase()
    paint_all((40, 30, 0))
    sync_ntp()
    fetch_metars()
    start_http()
    last_fetch = time.time()
    while True:
        handle_http()
        now = time.time()
        if now - last_fetch >= CYCLE_DELAY:
            if not in_sleep_window():
                fetch_metars()
            last_fetch = now
            gc.collect()
        render_frame()
        time.sleep_ms(20)


main()
