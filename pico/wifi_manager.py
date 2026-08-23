import gc
import json
import machine
import neopixel
import network
import socket
import utime as time

AP_SSID = "GreatLakes-Setup"
AP_PASSWORD = "door1234"
CONFIG_FILE = "wifi_config.json"
FORCE_AP_BUTTON_PIN = 15
AP_IDLE_REBOOT_S = 480
DEFAULT_LED_PIN = 0
DEFAULT_NUM_LEDS = 13
DEFAULT_BRIGHTNESS = 0.18
STARTUP_BRIGHTNESS = 0.2

DEFAULT_SLEEP = {
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

WEEKDAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
GPIO_NOTES = {
    0: " (default)",
    15: " (setup button)",
    21: " (LDR drive)",
    23: " (internal)",
    24: " (internal)",
    25: " (internal)",
    26: " (LDR)",
}

led = None
num_leds = DEFAULT_NUM_LEDS


def _clamp(n, lo, hi):
    return max(lo, min(hi, n))


def load_config():
    try:
        with open(CONFIG_FILE, "r") as f:
            cfg = json.load(f)
        if isinstance(cfg, dict):
            return cfg
    except Exception:
        pass
    return {}


def save_config(cfg):
    with open(CONFIG_FILE, "w") as f:
        json.dump(cfg, f)
    return True


def init_strip():
    global led, num_leds
    cfg = load_config()
    pin = _clamp(int(cfg.get("led_pin", DEFAULT_LED_PIN)), 0, 28)
    num_leds = _clamp(int(cfg.get("num_leds", DEFAULT_NUM_LEDS)), 1, 300)
    try:
        led = neopixel.NeoPixel(machine.Pin(pin), num_leds)
        print("WiFi manager strip: GPIO", pin, "x", num_leds)
    except Exception as e:
        led = None
        print("Strip init failed:", e)


def set_leds(r, g, b, brightness=None):
    if led is None:
        return
    if brightness is None:
        brightness = STARTUP_BRIGHTNESS
    rr = _clamp(int(r * brightness), 0, 255)
    gg = _clamp(int(g * brightness), 0, 255)
    bb = _clamp(int(b * brightness), 0, 255)
    for i in range(num_leds):
        led[i] = (rr, gg, bb)
    led.write()


def clear_leds():
    set_leds(0, 0, 0, 0)


def urldecode(s):
    s = s.replace("+", " ")
    out = []
    i = 0
    while i < len(s):
        if s[i] == "%" and i + 2 < len(s):
            try:
                out.append(chr(int(s[i + 1:i + 3], 16)))
                i += 3
                continue
            except Exception:
                pass
        out.append(s[i])
        i += 1
    return "".join(out)


def parse_urlencoded(body):
    params = {}
    if not body:
        return params
    for pair in body.split("&"):
        if "=" not in pair:
            continue
        k, v = pair.split("=", 1)
        params[urldecode(k)] = urldecode(v)
    return params


def parse_body(request):
    idx = request.find("\r\n\r\n")
    body = request[idx + 4:] if idx >= 0 else ""
    body = body.strip()
    if not body:
        return {}
    if body.startswith("{") or body.startswith("["):
        try:
            data = json.loads(body)
            return data if isinstance(data, dict) else {}
        except Exception:
            return {}
    return parse_urlencoded(body)


def _as_bool(v):
    return str(v).lower() in ("1", "on", "true", "yes")


def apply_fields(cfg, src):
    if src.get("ssid"):
        cfg["ssid"] = str(src["ssid"])
    if src.get("password") is not None and str(src.get("password")) != "":
        cfg["password"] = str(src["password"])
    if "num_leds" in src and str(src["num_leds"]) != "":
        cfg["num_leds"] = _clamp(int(src["num_leds"]), 1, 300)
    if "led_pin" in src and str(src["led_pin"]) != "":
        cfg["led_pin"] = _clamp(int(src["led_pin"]), 0, 28)
    if "brightness" in src and str(src["brightness"]) != "":
        cfg["brightness"] = max(0.02, min(1.0, float(src["brightness"])))
    if "beacon_pulse" in src:
        cfg["beacon_pulse"] = _as_bool(src["beacon_pulse"])
    if "sleep_enabled" in src:
        cfg["sleep_enabled"] = _as_bool(src["sleep_enabled"])
    if "weekend_mode_enabled" in src:
        cfg["weekend_mode_enabled"] = _as_bool(src["weekend_mode_enabled"])
    for key, lo, hi in (
        ("sleep_at_hour", 0, 23),
        ("sleep_at_minute", 0, 59),
        ("wake_at_hour", 0, 23),
        ("wake_at_minute", 0, 59),
        ("timezone_offset_hours", -12, 14),
        ("cycle_delay", 30, 3600),
        ("weekend_off_weekday", 0, 6),
        ("weekend_off_hour", 0, 23),
        ("weekend_off_minute", 0, 59),
        ("weekend_on_weekday", 0, 6),
        ("weekend_on_hour", 0, 23),
        ("weekend_on_minute", 0, 59),
    ):
        if key in src and str(src[key]) != "":
            cfg[key] = _clamp(int(float(src[key])), lo, hi)
    return cfg


def gpio_options(selected):
    parts = []
    sel = _clamp(int(selected), 0, 28)
    for n in range(29):
        mark = " selected" if n == sel else ""
        parts.append('<option value="%d"%s>GPIO %d%s</option>' % (n, mark, n, GPIO_NOTES.get(n, "")))
    return "".join(parts)


def weekday_options(selected):
    parts = []
    sel = _clamp(int(selected), 0, 6)
    for i, name in enumerate(WEEKDAYS):
        mark = " selected" if i == sel else ""
        parts.append('<option value="%d"%s>%s</option>' % (i, mark, name))
    return "".join(parts)


def merge_defaults(cfg):
    out = {
        "ssid": "",
        "password": "",
        "num_leds": DEFAULT_NUM_LEDS,
        "led_pin": DEFAULT_LED_PIN,
        "brightness": DEFAULT_BRIGHTNESS,
        "beacon_pulse": True,
        "cycle_delay": 300,
    }
    out.update(DEFAULT_SLEEP)
    out.update(cfg)
    return out


def test_wifi_connection(ssid, password):
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    try:
        wlan.disconnect()
    except Exception:
        pass
    wlan.connect(ssid, password)
    for _ in range(20):
        if wlan.isconnected():
            ip = wlan.ifconfig()[0]
            print("WiFi test OK:", ip)
            return True, ip
        time.sleep(1)
    print("WiFi test failed")
    return False, None


def send(conn, status, content_type, body):
    if isinstance(body, str):
        body = body.encode()
    hdr = "HTTP/1.1 %s\r\nContent-Type: %s\r\nContent-Length: %d\r\nConnection: close\r\n\r\n" % (
        status, content_type, len(body)
    )
    conn.send(hdr.encode())
    conn.send(body)


def send_html(conn, page):
    send(conn, "200 OK", "text/html; charset=utf-8", page)


def send_json(conn, obj, status="200 OK"):
    send(conn, status, "application/json", json.dumps(obj))


def setup_page():
    cfg = merge_defaults(load_config())
    checked_beacon = "checked" if cfg.get("beacon_pulse", True) else ""
    checked_sleep = "checked" if cfg.get("sleep_enabled", False) else ""
    checked_weekend = "checked" if cfg.get("weekend_mode_enabled", False) else ""
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Great Lakes Lighthouses</title>
<style>
body{font-family:Arial,sans-serif;background:#0B1F3A;color:#F4EBD0;margin:0;padding:16px}
h1{font-size:1.3rem;color:#E8A838}
h2{font-size:1rem;color:#E8A838;margin-top:18px}
label{display:block;margin-top:10px;font-size:.9rem}
input,select{width:100%;box-sizing:border-box;padding:8px;margin-top:4px;border-radius:6px;border:0}
.row{display:flex;gap:8px}.row>div{flex:1}
button{margin-top:16px;width:100%;padding:12px;background:#E8A838;border:0;border-radius:8px;font-weight:bold}
.note{font-size:.8rem;color:#A8B5C4;margin-top:12px}
</style></head><body>
<h1>Great Lakes Lighthouses</h1>
<p>Connect this Pico 2 W to your home Wi-Fi. Address: 192.168.4.1</p>
<form action="/configure" method="post">
<label>Home Wi-Fi name (SSID)</label>
<input name="ssid" value="%s" required>
<label>Password</label>
<input name="password" type="password" value="">
<div class="row">
<div><label>LED count</label><input name="num_leds" type="number" min="1" max="300" value="%s"></div>
<div><label>Strip data pin</label><select name="led_pin">%s</select></div>
</div>
<label>Brightness (0.05–1.0)</label>
<input name="brightness" type="number" step="0.01" min="0.05" max="1" value="%s">
<label>Refresh seconds</label>
<input name="cycle_delay" type="number" min="30" max="3600" value="%s">
<label><input name="beacon_pulse" type="checkbox" value="1" %s style="width:auto"> Beacon pulse on clear weather</label>
<h2>Sleep schedule</h2>
<label>Timezone offset from UTC (Central: -6 standard, -5 daylight)</label>
<input name="timezone_offset_hours" type="number" min="-12" max="14" value="%s">
<label><input name="sleep_enabled" type="checkbox" value="1" %s style="width:auto"> Turn LEDs off every night</label>
<div class="row">
<div><label>Off hour</label><input name="sleep_at_hour" type="number" min="0" max="23" value="%s"></div>
<div><label>Off minute</label><input name="sleep_at_minute" type="number" min="0" max="59" value="%s"></div>
</div>
<div class="row">
<div><label>On hour</label><input name="wake_at_hour" type="number" min="0" max="23" value="%s"></div>
<div><label>On minute</label><input name="wake_at_minute" type="number" min="0" max="59" value="%s"></div>
</div>
<h2>Weekend / long off</h2>
<label><input name="weekend_mode_enabled" type="checkbox" value="1" %s style="width:auto"> Extra off block (e.g. Fri 18:00 → Mon 06:00)</label>
<div class="row">
<div><label>Off weekday</label><select name="weekend_off_weekday">%s</select></div>
<div><label>Off hour</label><input name="weekend_off_hour" type="number" min="0" max="23" value="%s"></div>
<div><label>Off minute</label><input name="weekend_off_minute" type="number" min="0" max="59" value="%s"></div>
</div>
<div class="row">
<div><label>On weekday</label><select name="weekend_on_weekday">%s</select></div>
<div><label>On hour</label><input name="weekend_on_hour" type="number" min="0" max="23" value="%s"></div>
<div><label>On minute</label><input name="weekend_on_minute" type="number" min="0" max="59" value="%s"></div>
</div>
<button type="submit">Save &amp; Reboot</button>
</form>
<p class="note">13 lights, south to north, starting at LED 0 (Kewaunee) through LED 12 (Pottawatomie).</p>
</body></html>
""" % (
        cfg.get("ssid", ""),
        cfg.get("num_leds", DEFAULT_NUM_LEDS),
        gpio_options(cfg.get("led_pin", DEFAULT_LED_PIN)),
        cfg.get("brightness", DEFAULT_BRIGHTNESS),
        cfg.get("cycle_delay", 300),
        checked_beacon,
        cfg.get("timezone_offset_hours", -5),
        checked_sleep,
        cfg.get("sleep_at_hour", 22),
        cfg.get("sleep_at_minute", 0),
        cfg.get("wake_at_hour", 6),
        cfg.get("wake_at_minute", 0),
        checked_weekend,
        weekday_options(cfg.get("weekend_off_weekday", 4)),
        cfg.get("weekend_off_hour", 18),
        cfg.get("weekend_off_minute", 0),
        weekday_options(cfg.get("weekend_on_weekday", 0)),
        cfg.get("weekend_on_hour", 6),
        cfg.get("weekend_on_minute", 0),
    )


def success_page(ok, ip):
    ip_line = ("LAN IP: <b>%s</b> — save this for the Android app later." % ip) if ip else "Wi-Fi test failed. The Pico will retry on reboot."
    return """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Saved</title></head>
<body style="font-family:Arial;background:#0B1F3A;color:#F4EBD0;padding:24px">
<h1 style="color:#E8A838">%s</h1>
<p>%s</p>
<p>Reconnect your phone to home Wi-Fi after the Pico reboots.</p>
</body></html>""" % ("Saved" if ok else "Saved (check Wi-Fi)", ip_line)


def read_request(conn):
    conn.settimeout(2)
    data = b""
    while True:
        try:
            chunk = conn.recv(1024)
            if not chunk:
                break
            data += chunk
            if b"\r\n\r\n" in data:
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
                data = header + b"\r\n\r\n" + body
                break
        except OSError:
            break
    try:
        return data.decode()
    except Exception:
        return ""


def start_ap():
    ap = network.WLAN(network.AP_IF)
    ap.active(True)
    ap.config(essid=AP_SSID, password=AP_PASSWORD)
    for _ in range(20):
        if ap.active():
            break
        time.sleep(0.2)
    print("AP", AP_SSID, ap.ifconfig())
    return ap


def connect_saved_sta():
    cfg = load_config()
    ssid = cfg.get("ssid")
    password = cfg.get("password")
    if not ssid:
        return False
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    if not wlan.isconnected():
        wlan.connect(ssid, password)
        for _ in range(15):
            if wlan.isconnected():
                break
            time.sleep(1)
    if wlan.isconnected():
        print("STA connected", wlan.ifconfig()[0])
        return True
    print("STA connect failed")
    return False


def run_server(force_ap=False):
    cfg = load_config()
    has_creds = bool(cfg.get("ssid"))
    if has_creds and not force_ap:
        if connect_saved_sta():
            return
        print("Saved Wi-Fi failed; opening setup AP")
    start_ap()
    set_leds(40, 30, 0, STARTUP_BRIGHTNESS)
    addr = socket.getaddrinfo("0.0.0.0", 80)[0][-1]
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(addr)
    s.listen(2)
    s.settimeout(1)
    print("Setup server on 192.168.4.1")
    idle_since = time.time()
    while True:
        if has_creds and (time.time() - idle_since) > AP_IDLE_REBOOT_S:
            print("AP idle reboot (will retry home Wi-Fi)")
            machine.reset()
        conn = None
        try:
            conn, _client = s.accept()
        except OSError:
            gc.collect()
            continue
        idle_since = time.time()
        try:
            request = read_request(conn)
            first = request.split("\r\n", 1)[0] if request else ""
            if first.startswith("GET /status"):
                send_json(conn, {"ok": True, "mode": "setup", "name": "GreatLakesLighthouses"})
            elif first.startswith("GET /lighthouses"):
                try:
                    with open("lighthouses.json", "r") as f:
                        data = json.load(f)
                    items = data.get("lighthouses", []) if isinstance(data, dict) else []
                except Exception:
                    items = []
                send_json(conn, {"ok": True, "lighthouses": items})
            elif first.startswith("POST /lighthouses"):
                try:
                    payload = parse_body(request)
                    items = payload.get("lighthouses") if isinstance(payload, dict) else payload
                    if not isinstance(items, list):
                        raise ValueError("lighthouses list required")
                    cleaned = []
                    for i, raw in enumerate(items):
                        if isinstance(raw, dict):
                            entry = dict(raw)
                            entry["led"] = i
                            cleaned.append(entry)
                    with open("lighthouses.json", "w") as f:
                        json.dump({"version": 3, "order": "list", "lighthouses": cleaned})
                    send_json(conn, {"ok": True, "count": len(cleaned), "message": "saved"})
                except Exception as e:
                    send_json(conn, {"ok": False, "message": str(e)})
            elif first.startswith("GET /config"):
                out = merge_defaults(load_config())
                out.pop("password", None)
                out["ok"] = True
                send_json(conn, out)
            elif first.startswith("POST /update-config"):
                src = parse_body(request)
                new_cfg = merge_defaults(load_config())
                apply_fields(new_cfg, src)
                save_config(new_cfg)
                send_json(conn, {"ok": True, "message": "saved"})
                if str(src.get("reboot", "")).lower() in ("1", "true", "yes"):
                    conn.close()
                    conn = None
                    time.sleep(1)
                    machine.reset()
            elif first.startswith("POST /configure"):
                src = parse_body(request)
                if "application/json" not in request:
                    if "beacon_pulse" not in src:
                        src["beacon_pulse"] = False
                    if "sleep_enabled" not in src:
                        src["sleep_enabled"] = False
                    if "weekend_mode_enabled" not in src:
                        src["weekend_mode_enabled"] = False
                new_cfg = merge_defaults(load_config())
                apply_fields(new_cfg, src)
                save_config(new_cfg)
                ok, ip = False, None
                if new_cfg.get("ssid") and new_cfg.get("password"):
                    ok, ip = test_wifi_connection(new_cfg["ssid"], new_cfg["password"])
                if "application/json" in request:
                    send_json(conn, {"ok": True, "ip": ip, "message": "saved"})
                else:
                    send_html(conn, success_page(ok, ip))
                conn.close()
                conn = None
                set_leds(20, 0, 40)
                time.sleep(2)
                clear_leds()
                machine.reset()
            elif first.startswith("POST /reboot"):
                send_json(conn, {"ok": True, "message": "rebooting"})
                conn.close()
                conn = None
                machine.reset()
            else:
                send_html(conn, setup_page())
        except Exception as e:
            print("Request error:", e)
        finally:
            if conn is not None:
                try:
                    conn.close()
                except Exception:
                    pass
        gc.collect()


def start(force_ap=False):
    print("===== Great Lakes Lighthouses WiFi Manager =====")
    gc.collect()
    init_strip()
    set_leds(40, 30, 0, STARTUP_BRIGHTNESS)
    run_server(force_ap=force_ap)
