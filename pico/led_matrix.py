# Optional 8x32 WS2812 matrix on GPIO 1 (MetarMap pin / snake-column wiring).
# Scrolls one column at a time from the main loop so the lighthouse strip keeps flashing.

import machine
import neopixel
import utime as time

WIDTH = 32
HEIGHT = 8
PIN = 1
NUM_LEDS = WIDTH * HEIGHT
SCROLL_MS = 70
_scroll_ms = SCROLL_MS
# SNAKE_COLUMN: even columns top->bottom, odd columns bottom->top.
WIRING = "SNAKE_COLUMN"

# 4x6 (variable width) row bitmaps. Packed to columns at import; this file
# is only imported when DISPLAY_TYPE is LED_MATRIX.
_FONT_ROWS = {
    "A": [[0,1,1,0],[1,0,0,1],[1,0,0,1],[1,1,1,1],[1,0,0,1],[1,0,0,1]],
    "B": [[1,1,1,0],[1,0,0,1],[1,1,1,0],[1,0,0,1],[1,0,0,1],[1,1,1,0]],
    "C": [[0,1,1,0],[1,0,0,1],[1,0,0,0],[1,0,0,0],[1,0,0,1],[0,1,1,0]],
    "D": [[1,1,1,0],[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,1,1,0]],
    "E": [[1,1,1,1],[1,0,0,0],[1,1,1,0],[1,0,0,0],[1,0,0,0],[1,1,1,1]],
    "F": [[1,1,1,1],[1,0,0,0],[1,1,1,0],[1,0,0,0],[1,0,0,0],[1,0,0,0]],
    "G": [[0,1,1,0],[1,0,0,1],[1,0,0,0],[1,0,1,1],[1,0,0,1],[0,1,1,0]],
    "H": [[1,0,0,1],[1,0,0,1],[1,1,1,1],[1,0,0,1],[1,0,0,1],[1,0,0,1]],
    "I": [[1,1,1],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[1,1,1]],
    "J": [[0,0,1],[0,0,1],[0,0,1],[0,0,1],[1,0,1],[0,1,0]],
    "K": [[1,0,0,1],[1,0,1,0],[1,1,0,0],[1,0,1,0],[1,0,1,0],[1,0,0,1]],
    "L": [[1,0,0,0],[1,0,0,0],[1,0,0,0],[1,0,0,0],[1,0,0,0],[1,1,1,1]],
    "M": [[1,0,0,0,1],[1,1,0,1,1],[1,0,1,0,1],[1,0,0,0,1],[1,0,0,0,1],[1,0,0,0,1]],
    "N": [[1,0,0,0,1],[1,1,0,0,1],[1,0,1,0,1],[1,0,0,1,1],[1,0,0,0,1],[1,0,0,0,1]],
    "O": [[0,1,1,0],[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0]],
    "P": [[1,1,1,0],[1,0,0,1],[1,0,0,1],[1,1,1,0],[1,0,0,0],[1,0,0,0]],
    "Q": [[0,1,1,0],[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0],[0,0,0,1]],
    "R": [[1,1,1,0],[1,0,0,1],[1,0,0,1],[1,1,1,0],[1,0,0,1],[1,0,0,1]],
    "S": [[0,1,1,1],[1,0,0,0],[0,1,1,0],[0,0,0,1],[1,0,0,1],[0,1,1,0]],
    "T": [[1,1,1,1,1],[0,0,1,0,0],[0,0,1,0,0],[0,0,1,0,0],[0,0,1,0,0],[0,0,1,0,0]],
    "U": [[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,0,0,1],[1,0,0,1],[0,1,1,0]],
    "V": [[1,0,0,0,1],[1,0,0,0,1],[1,0,0,0,1],[0,1,0,1,0],[0,1,0,1,0],[0,0,1,0,0]],
    "W": [[1,0,0,0,1],[1,0,0,0,1],[1,0,0,0,1],[1,0,1,0,1],[1,1,0,1,1],[1,0,0,0,1]],
    "X": [[1,0,0,0,1],[0,1,0,1,0],[0,0,1,0,0],[0,0,1,0,0],[0,1,0,1,0],[1,0,0,0,1]],
    "Y": [[1,0,0,0,1],[0,1,0,1,0],[0,0,1,0,0],[0,0,1,0,0],[0,0,1,0,0],[0,0,1,0,0]],
    "Z": [[1,1,1,1,1],[0,0,0,1,0],[0,0,1,0,0],[0,1,0,0,0],[1,0,0,0,0],[1,1,1,1,1]],
    "0": [[0,1,1,0],[1,0,0,1],[1,0,1,1],[1,1,0,1],[1,0,0,1],[0,1,1,0]],
    "1": [[0,1,0],[1,1,0],[0,1,0],[0,1,0],[0,1,0],[1,1,1]],
    "2": [[0,1,1,0],[1,0,0,1],[0,0,1,0],[0,1,0,0],[1,0,0,0],[1,1,1,1]],
    "3": [[1,1,1,0],[0,0,0,1],[0,1,1,0],[0,0,0,1],[0,0,0,1],[1,1,1,0]],
    "4": [[0,0,1,0],[0,1,1,0],[1,0,1,0],[1,1,1,1],[0,0,1,0],[0,0,1,0]],
    "5": [[1,1,1,1],[1,0,0,0],[1,1,1,0],[0,0,0,1],[1,0,0,1],[0,1,1,0]],
    "6": [[0,1,1,0],[1,0,0,0],[1,1,1,0],[1,0,0,1],[1,0,0,1],[0,1,1,0]],
    "7": [[1,1,1,1],[0,0,0,1],[0,0,1,0],[0,1,0,0],[0,1,0,0],[0,1,0,0]],
    "8": [[0,1,1,0],[1,0,0,1],[0,1,1,0],[1,0,0,1],[1,0,0,1],[0,1,1,0]],
    "9": [[0,1,1,0],[1,0,0,1],[1,0,0,1],[0,1,1,1],[0,0,0,1],[0,1,1,0]],
    "=": [[0,0,0,0],[1,1,1,1],[0,0,0,0],[1,1,1,1],[0,0,0,0],[0,0,0,0]],
    "/": [[0,0,0,1],[0,0,1,0],[0,0,1,0],[0,1,0,0],[0,1,0,0],[1,0,0,0]],
    "-": [[0,0,0,0],[0,0,0,0],[1,1,1,1],[1,1,1,1],[0,0,0,0],[0,0,0,0]],
    "+": [[0,0,0,0],[0,0,1,0],[0,0,1,0],[1,1,1,1],[0,0,1,0],[0,0,1,0]],
    " ": [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
    ":": [[0,0,0],[0,1,0],[0,0,0],[0,0,0],[0,1,0],[0,0,0]],
    ".": [[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,1,0],[0,0,0]],
    ",": [[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,1,0],[1,0,0]],
    "!": [[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,0,0],[0,1,0]],
    "?": [[0,1,1,0],[1,0,0,1],[0,0,1,0],[0,1,0,0],[0,0,0,0],[0,1,0,0]],
    "$": [[0,1,1,1,0],[1,0,1,0,0],[0,1,1,1,0],[0,0,1,0,1],[0,1,1,1,0]],
    "@": [[0,1,1,0],[1,0,0,1],[1,0,1,1],[1,0,1,1],[1,0,0,0],[0,1,1,0]],
    "#": [[0,1,0,1,0],[1,1,1,1,1],[0,1,0,1,0],[1,1,1,1,1],[0,1,0,1,0],[0,0,0,0,0]],
    "%": [[1,0,0,0,1],[0,0,0,1,0],[0,0,1,0,0],[0,1,0,0,0],[1,0,0,0,1],[0,0,0,0,0]],
    "&": [[0,1,1,0],[1,0,0,1],[0,1,1,0],[1,0,0,1],[1,0,0,1],[0,1,1,1]],
    "*": [[0,0,0,0,0],[0,1,0,1,0],[0,0,1,0,0],[0,1,0,1,0],[0,0,0,0,0],[0,0,0,0,0]],
    "(": [[0,0,1],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,0,1]],
    ")": [[1,0,0],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[1,0,0]],
    "[": [[1,1,0],[1,0,0],[1,0,0],[1,0,0],[1,0,0],[1,1,0]],
    "]": [[0,1,1],[0,0,1],[0,0,1],[0,0,1],[0,0,1],[0,1,1]],
    "{": [[0,0,1],[0,1,0],[1,0,0],[0,1,0],[0,1,0],[0,0,1]],
    "}": [[1,0,0],[0,1,0],[0,0,1],[0,1,0],[0,1,0],[1,0,0]],
    "<": [[0,0,1],[0,1,0],[1,0,0],[0,1,0],[0,0,1],[0,0,0]],
    ">": [[1,0,0],[0,1,0],[0,0,1],[0,1,0],[1,0,0],[0,0,0]],
    "^": [[0,0,1,0,0],[0,1,0,1,0],[1,0,0,0,1],[0,0,0,0,0],[0,0,0,0,0],[0,0,0,0,0]],
    "_": [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[1,1,1,1]],
    "|": [[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,1,0]],
    "~": [[0,0,0,0,0],[0,1,1,0,1],[1,0,0,1,0],[0,0,0,0,0],[0,0,0,0,0],[0,0,0,0,0]],
    '"': [[1,0,1,0],[1,0,1,0],[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]],
    "'": [[0,1,0],[0,1,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]],
    "`": [[1,0,0],[0,1,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]],
}


def _pack_rows(rows):
    width = 0
    for row in rows:
        n = len(row)
        if n > width:
            width = n
    cols = [0] * width
    y = 0
    for row in rows:
        if y > 5:
            break
        x = 0
        for bit in row:
            if bit:
                cols[x] |= (1 << y)
            x += 1
        y += 1
    return tuple(cols)


_FONT = {}
for _ch, _rows in _FONT_ROWS.items():
    _FONT[_ch] = _pack_rows(_rows)
del _FONT_ROWS

_np = None
_index = None
_columns = []
_col_colors = []
_offset = 0
_last_ms = 0
_color = (255, 180, 48)
_text = ""
_sleep_cleared = False


def _pixel_index(x, y):
    if WIRING == "SNAKE_COLUMN":
        if x % 2 == 0:
            return x * HEIGHT + y
        return x * HEIGHT + (HEIGHT - 1 - y)
    if WIRING == "COLUMN_MAJOR":
        return x * HEIGHT + y
    if WIRING == "SNAKE_ROW":
        if y % 2 == 0:
            return y * WIDTH + x
        return y * WIDTH + (WIDTH - 1 - x)
    return y * WIDTH + x


def _build_indices():
    table = []
    for x in range(WIDTH):
        col = []
        for y in range(HEIGHT):
            col.append(_pixel_index(x, y))
        table.append(col)
    return table


def _text_columns(text):
    cols = []
    for ch in text.upper():
        glyph = _FONT.get(ch) or _FONT.get(" ")
        for byte in glyph:
            cols.append(byte)
        cols.append(0)
    for _ in range(WIDTH):
        cols.append(0)
    return cols


def active():
    return _np is not None


def init():
    global _np, _index, _columns, _col_colors, _offset, _last_ms
    try:
        _np = neopixel.NeoPixel(machine.Pin(PIN), NUM_LEDS)
        _index = _build_indices()
        _np.fill((0, 0, 0))
        _np.write()
        _columns = []
        _col_colors = []
        _offset = 0
        _last_ms = 0
        print("LED matrix GPIO", PIN, "x", NUM_LEDS)
        return True
    except Exception as e:
        _np = None
        print("LED matrix skipped:", e)
        return False


def set_text(text, color=(255, 180, 48)):
    set_segments([(text, color)])


def set_scroll_ms(ms):
    global _scroll_ms
    try:
        _scroll_ms = max(20, min(250, int(ms)))
    except Exception:
        _scroll_ms = SCROLL_MS


def set_segments(segments):
    global _columns, _col_colors, _offset, _color, _text
    parts = []
    for text, color in segments or []:
        t = str(text or "").strip()
        if t:
            parts.append((t, color or (255, 180, 48)))
    if not parts:
        _text = ""
        _columns = []
        _col_colors = []
        _offset = 0
        clear()
        return
    key = "|".join(p[0] + str(p[1]) for p in parts)
    if key == _text and _columns:
        return
    _text = key
    cols = []
    colors = []
    for text, color in parts:
        for ch in text.upper():
            glyph = _FONT.get(ch) or _FONT.get(" ")
            for byte in glyph:
                cols.append(byte)
                colors.append(color)
            cols.append(0)
            colors.append(color)
        for _ in range(3):
            cols.append(0)
            colors.append(color)
    for _ in range(WIDTH):
        cols.append(0)
        colors.append((0, 0, 0))
    _columns = cols
    _col_colors = colors
    _color = parts[0][1]
    _offset = 0


def clear():
    if _np is None:
        return
    _np.fill((0, 0, 0))
    _np.write()


def tick(level=8, sleeping=False):
    global _offset, _last_ms, _sleep_cleared
    if _np is None or _index is None:
        return False
    if sleeping:
        if not _sleep_cleared:
            clear()
            _sleep_cleared = True
        return False
    _sleep_cleared = False
    now = time.ticks_ms()
    if _last_ms and time.ticks_diff(now, _last_ms) < _scroll_ms:
        return False
    _last_ms = now
    if not _columns:
        return False
    n = len(_columns)
    span = max(1, n - WIDTH)
    scale = max(1, min(30, int(level)))
    _np.fill((0, 0, 0))
    for x in range(WIDTH):
        idx = _offset + x
        if idx >= n:
            continue
        bits = _columns[idx] << 1
        if not bits:
            continue
        rgb = _col_colors[idx] if idx < len(_col_colors) else _color
        pix = (rgb[0] * scale // 255, rgb[1] * scale // 255, rgb[2] * scale // 255)
        for y in range(HEIGHT):
            if bits & (1 << y):
                _np[_index[x][y]] = pix
    _np.write()
    _offset += 1
    if _offset >= span:
        _offset = 0
        return True
    return False
