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
BRIGHTNESS_CAP = 30

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
    if "min_brightness" in src and str(src["min_brightness"]) != "":
        cfg["min_brightness"] = _clamp(int(float(src["min_brightness"])), 0, BRIGHTNESS_CAP)
    if "max_brightness" in src and str(src["max_brightness"]) != "":
        cfg["max_brightness"] = _clamp(int(float(src["max_brightness"])), 1, BRIGHTNESS_CAP)
        cfg["brightness"] = max(0.02, min(1.0, cfg["max_brightness"] / 255.0))
    elif "brightness" in src and str(src["brightness"]) != "":
        cfg["brightness"] = max(0.02, min(1.0, float(src["brightness"])))
        cfg["max_brightness"] = _clamp(int(round(cfg["brightness"] * 255)), 1, BRIGHTNESS_CAP)
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
        "min_brightness": 2,
        "max_brightness": _clamp(int(round(DEFAULT_BRIGHTNESS * 255)), 1, BRIGHTNESS_CAP),
        "beacon_pulse": True,
        "cycle_delay": 300,
    }
    out.update(DEFAULT_SLEEP)
    out.update(cfg)
    try:
        out["max_brightness"] = _clamp(int(out.get("max_brightness", 18)), 1, BRIGHTNESS_CAP)
        out["min_brightness"] = _clamp(int(out.get("min_brightness", 2)), 0, out["max_brightness"])
    except Exception:
        out["max_brightness"] = 18
        out["min_brightness"] = 2
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


def _send_all(conn, data):
    if isinstance(data, str):
        data = data.encode()
    while data:
        sent = conn.send(data)
        if not sent:
            break
        data = data[sent:]


def send(conn, status, content_type, body):
    if isinstance(body, str):
        body = body.encode()
    hdr = (
        "HTTP/1.1 %s\r\nContent-Type: %s\r\nContent-Length: %d\r\n"
        "Connection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        % (status, content_type, len(body))
    )
    _send_all(conn, hdr.encode() + body)


def send_json_file(conn, path, missing=None):
    try:
        import os
        size = os.stat(path)[6]
        hdr = (
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
            "Content-Length: %d\r\nConnection: close\r\n"
            "Access-Control-Allow-Origin: *\r\n\r\n" % size
        )
        _send_all(conn, hdr)
        with open(path, "rb") as f:
            while True:
                chunk = f.read(1024)
                if not chunk:
                    break
                _send_all(conn, chunk)
    except Exception:
        send_json(conn, missing if missing is not None else {"ok": False, "lighthouses": []})


def send_static_file(conn, path, content_type, missing=""):
    try:
        import os
        size = os.stat(path)[6]
        hdr = (
            "HTTP/1.1 200 OK\r\nContent-Type: %s\r\n"
            "Content-Length: %d\r\nConnection: close\r\n"
            "Access-Control-Allow-Origin: *\r\n\r\n" % (content_type, size)
        )
        _send_all(conn, hdr)
        with open(path, "rb") as f:
            while True:
                chunk = f.read(1024)
                if not chunk:
                    break
                _send_all(conn, chunk)
    except Exception:
        send(conn, "200 OK", content_type, missing or "<p>Copy help.html to the Pico.</p>")


def send_html(conn, page):
    send(conn, "200 OK", "text/html; charset=utf-8", page)


def send_json(conn, obj, status="200 OK"):
    send(conn, status, "application/json", json.dumps(obj))


def _html_attr(value):
    s = str(value)
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")
    return s


def setup_page():
    cfg = merge_defaults(load_config())
    checked_beacon = "checked" if cfg.get("beacon_pulse", True) else ""
    checked_sleep = "checked" if cfg.get("sleep_enabled", False) else ""
    checked_weekend = "checked" if cfg.get("weekend_mode_enabled", False) else ""
    page = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Great Lakes Lighthouses</title>
<style>
body{font-family:Arial,sans-serif;background:#0B1F3A;color:#F4EBD0;margin:0;padding:16px}
h1{font-size:1.3rem;color:#E8A838}
h2{font-size:1rem;color:#E8A838;margin-top:18px}
label{display:block;margin-top:10px;font-size:.9rem}
input,select{width:100%;box-sizing:border-box;padding:8px;margin-top:4px;border-radius:6px;border:0}
.row{display:flex;gap:8px}.row>div{flex:1}
button{margin-top:16px;width:100%;padding:12px;background:#E8A838;border:0;border-radius:8px;font-weight:bold;color:#0B1F3A}
.note{font-size:.8rem;color:#A8B5C4;margin-top:12px}
.tabs{display:flex;gap:8px;margin:14px 0}
.tabs button{margin:0;flex:1;background:#16324F;color:#F4EBD0}
.tabs button.on{background:#E8A838;color:#0B1F3A}
.actions{display:flex;gap:8px;flex-wrap:wrap}
.actions button{width:auto;margin-top:8px;padding:8px 12px}
.card{background:#16324F;border-radius:10px;padding:10px;margin-top:8px;display:flex;align-items:center;gap:8px}
.card.skip{opacity:.5}
.led{min-width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:bold;background:rgba(255,236,212,.2)}
.grow{flex:1;min-width:0}
.amber{color:#E8A838;font-size:.85rem}
.muted{color:#A8B5C4;font-size:.75rem}
.tiny{width:auto;margin:0;padding:6px 8px;background:#0B1F3A;color:#F4EBD0}
.use{display:flex;flex-direction:column;align-items:center;font-size:.7rem;color:#A8B5C4}
.use input{width:auto;margin:0}
#overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.55);padding:16px;overflow:auto;z-index:2}
#overlay .box{background:#0B1F3A;border:1px solid #E8A838;border-radius:10px;padding:16px;max-width:520px;margin:20px auto}
#catList{max-height:50vh;overflow:auto}
.hit{background:#16324F;border-radius:8px;padding:8px;margin-top:6px}
.hit.onmap{opacity:.45}
.status{color:#E8A838;font-size:.85rem;min-height:1.2em}
#panelSettings,#panelHelp{display:none}
</style></head><body>
<h1>Great Lakes Lighthouses</h1>
<p>Open this page on the Pico (setup: 192.168.4.1, or the Pico LAN IP).</p>
<div class="tabs">
<button type="button" id="tabLights" onclick="showTab('lights')">Lighthouses</button>
<button type="button" id="tabSettings" onclick="showTab('settings')">Pico settings</button>
<button type="button" id="tabHelp" onclick="showTab('help')">Help</button>
</div>
<div id="panelLights">
<p class="note">List order is LED order (south to north). Skip a light to leave that LED off. Save when the strip matches.</p>
<div class="actions">
<button type="button" onclick="saveLights()">Save list</button>
<button type="button" class="tiny" onclick="loadLights()">Reload</button>
<button type="button" class="tiny" onclick="restoreDefaults()">Restore defaults</button>
</div>
<div class="actions">
<button type="button" class="tiny" onclick="openCatalog()">Add from catalog</button>
<button type="button" class="tiny" onclick="openCustom()">Add custom</button>
</div>
<p class="status" id="lhStatus"></p>
<p id="lhCount"></p>
<div id="lhList"></div>
</div>
<div id="panelSettings">
<form action="/configure" method="post">
<h2>Home Wi-Fi</h2>
<label>Network name (SSID)</label>
<input name="ssid" value="__SSID__" placeholder="Your router name" autocomplete="off">
<label>Password</label>
<input name="password" type="password" value="" placeholder="Leave blank to keep the current password" autocomplete="off">
<p class="note">SSID and password are for the Pico to join your router, not for the GreatLakes-Setup network.</p>
<div class="row">
<div><label>LED count</label><input name="num_leds" type="number" min="1" max="300" value="__NUM_LEDS__"></div>
<div><label>Strip data pin</label><select name="led_pin">__GPIO_OPTS__</select></div>
</div>
<div class="row">
<div><label>Min brightness (0-30)</label><input name="min_brightness" type="number" min="0" max="30" value="__MINB__"></div>
<div><label>Max brightness (1-30)</label><input name="max_brightness" type="number" min="1" max="30" value="__MAXB__"></div>
</div>
<p class="note">Max 30 is plenty for a wall map. Min 2 keeps WS2812 colors correct in the dark.</p>
<label>Refresh seconds</label>
<input name="cycle_delay" type="number" min="30" max="3600" value="__CYCLE__">
<label><input name="beacon_pulse" type="checkbox" value="1" __BEACON__ style="width:auto"> Beacon pulse on clear weather</label>
<h2>Sleep schedule</h2>
<label>Timezone offset from UTC (Central: -6 standard, -5 daylight)</label>
<input name="timezone_offset_hours" type="number" min="-12" max="14" value="__TZ__">
<label><input name="sleep_enabled" type="checkbox" value="1" __SLEEP__ style="width:auto"> Turn LEDs off every night</label>
<div class="row">
<div><label>Off hour</label><input name="sleep_at_hour" type="number" min="0" max="23" value="__SH__"></div>
<div><label>Off minute</label><input name="sleep_at_minute" type="number" min="0" max="59" value="__SM__"></div>
</div>
<div class="row">
<div><label>On hour</label><input name="wake_at_hour" type="number" min="0" max="23" value="__WH__"></div>
<div><label>On minute</label><input name="wake_at_minute" type="number" min="0" max="59" value="__WM__"></div>
</div>
<h2>Weekend / long off</h2>
<label><input name="weekend_mode_enabled" type="checkbox" value="1" __WEEKEND__ style="width:auto"> Extra off block (e.g. Fri 18:00 to Mon 06:00)</label>
<div class="row">
<div><label>Off weekday</label><select name="weekend_off_weekday">__WDOFF__</select></div>
<div><label>Off hour</label><input name="weekend_off_hour" type="number" min="0" max="23" value="__WOH__"></div>
<div><label>Off minute</label><input name="weekend_off_minute" type="number" min="0" max="59" value="__WOM__"></div>
</div>
<div class="row">
<div><label>On weekday</label><select name="weekend_on_weekday">__WDON__</select></div>
<div><label>On hour</label><input name="weekend_on_hour" type="number" min="0" max="23" value="__WIH__"></div>
<div><label>On minute</label><input name="weekend_on_minute" type="number" min="0" max="59" value="__WIM__"></div>
</div>
<button type="submit">Save &amp; Reboot</button>
</form>
</div>
<div id="panelHelp">
<iframe src="/help" title="Help" style="width:100%;min-height:75vh;border:0;background:#0B1F3A"></iframe>
</div>
<div id="overlay" onclick="if(event.target.id==='overlay')closeModal()">
<div class="box" id="modalBox"></div>
</div>
<script>
var lights=[], catalog=null;
var PRESETS=[
{char:'F W',color:'W',period_s:1,on_s:[1],off_s:[0],label:'F W - steady white'},
{char:'F R',color:'R',period_s:1,on_s:[1],off_s:[0],label:'F R - steady red'},
{char:'F G',color:'G',period_s:1,on_s:[1],off_s:[0],label:'F G - steady green'},
{char:'Fl W 4s',color:'W',period_s:4,on_s:[0.5],off_s:[3.5],label:'Fl W 4s - white flash 4s'},
{char:'Fl W 6s',color:'W',period_s:6,on_s:[1],off_s:[5],label:'Fl W 6s - white flash 6s'},
{char:'Fl R 2.5s',color:'R',period_s:2.5,on_s:[0.5],off_s:[2],label:'Fl R 2.5s - red flash 2.5s'},
{char:'Fl R 6s',color:'R',period_s:6,on_s:[1],off_s:[5],label:'Fl R 6s - red flash 6s'},
{char:'Fl R 10s',color:'R',period_s:10,on_s:[1],off_s:[9],label:'Fl R 10s - red flash 10s'},
{char:'Iso W 6s',color:'W',period_s:6,on_s:[3],off_s:[3],label:'Iso W 6s - white 3s on/off'},
{char:'Iso R 6s',color:'R',period_s:6,on_s:[3],off_s:[3],label:'Iso R 6s - red 3s on/off'},
{char:'Fl(2) W 6s',color:'W',period_s:6,on_s:[1,1],off_s:[1,3],label:'Fl(2) W 6s - two white flashes'}
];
function showTab(name){
 document.getElementById('panelLights').style.display=name==='lights'?'block':'none';
 document.getElementById('panelSettings').style.display=name==='settings'?'block':'none';
 document.getElementById('panelHelp').style.display=name==='help'?'block':'none';
 document.getElementById('tabLights').className=name==='lights'?'on':'';
 document.getElementById('tabSettings').className=name==='settings'?'on':'';
 document.getElementById('tabHelp').className=name==='help'?'on':'';
}
function esc(s){return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
function say(m){document.getElementById('lhStatus').textContent=m||'';}
function charOf(lh){return (lh.light&&lh.light.char)||'';}
function colorOf(lh){return ((lh.light&&lh.light.color)||'W').toUpperCase();}
function swatch(c){return c==='R'?'#E23B3B':c==='G'?'#2ECC71':'#FFECD4';}
function items(data){return (data&&data.lighthouses)||[];}
function syncLedCount(){
 var n=document.querySelector('[name=num_leds]');
 if(n) n.value=Math.max(1,lights.length);
}
function render(){
 var on=0;
 lights.forEach(function(lh,i){lh.led=i; if(!lh.skip) on++;});
 document.getElementById('lhCount').textContent='List ('+lights.length+' lights, '+on+' on)';
 var html='';
 lights.forEach(function(lh,i){
  var c=colorOf(lh);
  html+='<div class="card'+(lh.skip?' skip':'')+'">';
  html+='<div><button type="button" class="tiny" onclick="move('+i+',-1)">Up</button><br>';
  html+='<button type="button" class="tiny" onclick="move('+i+',1)">Down</button></div>';
  html+='<div class="led" style="color:'+swatch(c)+'">'+i+'</div>';
  html+='<div class="grow"><div>'+esc(lh.name||lh.short_name||('LED '+i))+'</div>';
  html+='<div class="amber">'+esc(charOf(lh)||'-')+'</div>';
  if(lh.metar) html+='<div class="muted">'+esc(lh.metar)+'</div>';
  html+='</div><label class="use">Use<input type="checkbox" '+(lh.skip?'':'checked')+' onchange="setSkip('+i+',!this.checked)"></label>';
  html+='<button type="button" class="tiny" onclick="removeAt('+i+')">X</button></div>';
 });
 document.getElementById('lhList').innerHTML=html||'<p class="note">No lights yet. Add from the catalog or restore defaults.</p>';
 syncLedCount();
}
function move(i,d){var j=i+d; if(j<0||j>=lights.length) return; var t=lights[i]; lights[i]=lights[j]; lights[j]=t; render();}
function setSkip(i,skip){lights[i].skip=!!skip; render();}
function removeAt(i){say('Removed '+(lights[i].short_name||lights[i].name||'')); lights.splice(i,1); render();}
function slug(name){return String(name||'light').toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'')||'light';}
function getJson(url,cb,err){
 fetch(url).then(function(r){return r.json();}).then(cb).catch(function(e){if(err)err(e); else say('Failed: '+e);});
}
function loadLights(){
 say('Loading list...');
 getJson('/lighthouses',function(data){lights=items(data); render(); say('Loaded '+lights.length+' lights');},
  function(e){say('Could not load /lighthouses: '+e);});
}
function saveLights(){
 lights.forEach(function(lh,i){lh.led=i;});
 say('Saving...');
 fetch('/lighthouses',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({lighthouses:lights})})
  .then(function(r){return r.json();})
  .then(function(data){say(data.ok?'Saved '+(data.count||lights.length)+' lights. Reboot if the strip length changed.':'Save failed: '+(data.message||''));})
  .catch(function(e){say('Save failed: '+e);});
}
function restoreDefaults(){
 say('Restoring Kewaunee to Rock Island list...');
 getJson('/lighthouses-defaults',function(data){
  var next=items(data);
  if(!next.length){say('Copy lighthouses_defaults.json to the Pico for restore.'); return;}
  lights=next; render(); say('Restored '+lights.length+' lights. Save list to apply.');
 });
}
function onMap(entry){
 return lights.some(function(lh){
  if(lh.id&&entry.id&&lh.id===entry.id) return true;
  if(lh.name&&entry.name&&lh.name.toLowerCase()===entry.name.toLowerCase()) return true;
  return false;
 });
}
function openCatalog(){
 document.getElementById('modalBox').innerHTML='<h2>Lake Michigan catalog</h2><p class="note">Loading...</p>';
 document.getElementById('overlay').style.display='block';
 function paint(q){
  q=(q||'').toLowerCase();
  var hits=(catalog||[]).filter(function(e){
   if(!q) return true;
   return (e.name||'').toLowerCase().indexOf(q)>=0||(e.short_name||'').toLowerCase().indexOf(q)>=0||(e.region||'').toLowerCase().indexOf(q)>=0||((e.light&&e.light.char)||'').toLowerCase().indexOf(q)>=0;
  });
  var html='<h2>Lake Michigan catalog</h2><label>Search</label><input id="catQ" value="'+esc(q)+'" placeholder="Grand Haven, St. Joseph, Point Betsie">';
  html+='<p class="note">'+hits.length+' of '+(catalog||[]).length+'</p><div id="catList">';
  hits.forEach(function(e,idx){
   var used=onMap(e);
   html+='<div class="hit'+(used?' onmap':'')+'" data-i="'+idx+'"><b>'+esc(e.name)+'</b><div class="amber">'+esc((e.light&&e.light.char)||'')+' · '+esc(e.region||'')+'</div>';
    html+=used?'<div class="muted">Already on this map</div>':'<div class="muted">Tap to add</div>';
   html+='</div>';
  });
  html+='</div><button type="button" onclick="closeModal()">Done</button>';
  document.getElementById('modalBox').innerHTML=html;
  var box=document.getElementById('modalBox');
  box._hits=hits;
  document.getElementById('catQ').oninput=function(){paint(this.value);};
  document.getElementById('catList').onclick=function(ev){
   var row=ev.target.closest('.hit'); if(!row||row.className.indexOf('onmap')>=0) return;
   addCatalog(box._hits[parseInt(row.getAttribute('data-i'),10)]);
  };
 }
 if(catalog){paint(''); return;}
 getJson('/catalog',function(data){
  catalog=items(data);
  if(!catalog.length){document.getElementById('modalBox').innerHTML='<h2>Catalog</h2><p class="note">Copy catalog.json to the Pico (same folder as main.py), then reload.</p><button type="button" onclick="closeModal()">Close</button>'; return;}
  paint('');
 }, function(){document.getElementById('modalBox').innerHTML='<h2>Catalog</h2><p class="note">Copy catalog.json to the Pico, then reload this page.</p><button type="button" onclick="closeModal()">Close</button>';});
}
function addCatalog(e){
 if(!e||onMap(e)) return;
 lights.push({id:e.id,name:e.name,short_name:e.short_name||e.name,led:lights.length,lat:e.lat||0,lon:e.lon||0,metar:e.metar||'',metar_fallback:e.metar_fallback||'',water:e.region||'',active:true,skip:false,light:e.light||{char:'F W',color:'W',period_s:1,on_s:[1],off_s:[0]}});
 render(); say('Added '+e.name); openCatalog();
}
function openCustom(){
 var opts=PRESETS.map(function(p,i){return '<option value="'+i+'">'+esc(p.label)+'</option>';}).join('');
 document.getElementById('modalBox').innerHTML='<h2>Add lighthouse</h2><label>Name</label><input id="cName"><label>Characteristic</label><select id="cPreset">'+opts+'</select><label>Nearby METAR (optional)</label><input id="cMetar" maxlength="4" placeholder="KSUE, KMTW, K3D2"><div class="actions"><button type="button" onclick="addCustom()">Add</button><button type="button" class="tiny" onclick="closeModal()">Cancel</button></div>';
 document.getElementById('overlay').style.display='block';
}
function addCustom(){
 var name=(document.getElementById('cName').value||'').trim();
 if(!name) return;
 var p=PRESETS[parseInt(document.getElementById('cPreset').value,10)]||PRESETS[0];
 var metar=(document.getElementById('cMetar').value||'').trim().toUpperCase();
 lights.push({id:slug(name)+'_'+Date.now()%100000,name:name,short_name:name,led:lights.length,metar:metar,metar_fallback:metar,active:true,skip:false,light:{char:p.char,color:p.color,period_s:p.period_s,on_s:p.on_s.slice(),off_s:p.off_s.slice()}});
 render(); say('Added '+name); closeModal();
}
function closeModal(){document.getElementById('overlay').style.display='none';}
showTab('__TAB__');
loadLights();
</script>
</body></html>
"""
    page = page.replace("__SSID__", _html_attr(cfg.get("ssid", "")))
    page = page.replace("__NUM_LEDS__", _html_attr(cfg.get("num_leds", DEFAULT_NUM_LEDS)))
    page = page.replace("__GPIO_OPTS__", gpio_options(cfg.get("led_pin", DEFAULT_LED_PIN)))
    max_b = cfg.get("max_brightness")
    if max_b is None:
        max_b = int(round(float(cfg.get("brightness", DEFAULT_BRIGHTNESS)) * 255))
    page = page.replace("__MINB__", _html_attr(cfg.get("min_brightness", 2)))
    page = page.replace("__MAXB__", _html_attr(max_b))
    page = page.replace("__CYCLE__", _html_attr(cfg.get("cycle_delay", 300)))
    page = page.replace("__BEACON__", checked_beacon)
    page = page.replace("__TZ__", _html_attr(cfg.get("timezone_offset_hours", -5)))
    page = page.replace("__SLEEP__", checked_sleep)
    page = page.replace("__SH__", _html_attr(cfg.get("sleep_at_hour", 22)))
    page = page.replace("__SM__", _html_attr(cfg.get("sleep_at_minute", 0)))
    page = page.replace("__WH__", _html_attr(cfg.get("wake_at_hour", 6)))
    page = page.replace("__WM__", _html_attr(cfg.get("wake_at_minute", 0)))
    page = page.replace("__WEEKEND__", checked_weekend)
    page = page.replace("__WDOFF__", weekday_options(cfg.get("weekend_off_weekday", 4)))
    page = page.replace("__WOH__", _html_attr(cfg.get("weekend_off_hour", 18)))
    page = page.replace("__WOM__", _html_attr(cfg.get("weekend_off_minute", 0)))
    page = page.replace("__WDON__", weekday_options(cfg.get("weekend_on_weekday", 0)))
    page = page.replace("__WIH__", _html_attr(cfg.get("weekend_on_hour", 6)))
    page = page.replace("__WIM__", _html_attr(cfg.get("weekend_on_minute", 0)))
    page = page.replace("__TAB__", "settings" if not cfg.get("ssid") else "lights")
    return page


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
            elif first.startswith("GET /help"):
                send_static_file(
                    conn,
                    "help.html",
                    "text/html; charset=utf-8",
                    "<p>Copy help.html to the Pico, then reload.</p>",
                )
            elif first.startswith("GET /catalog"):
                send_json_file(conn, "catalog.json", {"ok": False, "lighthouses": []})
            elif first.startswith("GET /lighthouses-defaults"):
                send_json_file(conn, "lighthouses_defaults.json", {"ok": False, "lighthouses": []})
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
