# Great Lakes Lighthouses

**13 lighthouses** from Kewaunee through Rock Island on a **Raspberry Pi Pico 2 W** NeoPixel strip. Each LED uses that light’s **real color and flash characteristic** (USCG Light List / historic pattern), not weather colors.

The Android app has a **Lake Michigan catalog** (~100 pierheads and lighthouses, with color and flash already filled in). Search and add the ones on *your* strip, then **Save to Pico**. Restore defaults still loads the Kewaunee → Rock Island pack.

## Light characteristics

Wired **south to north**. Timing is the official (or last historic) rhythm:

| LED | Lighthouse | Characteristic | Meaning |
|-----|------------|----------------|---------|
| 0 | Kewaunee Pierhead | **F W** | Steady white |
| 1 | Algoma Pierhead | **Iso R 6s** | Red 3s on, 3s off |
| 2 | Sturgeon Bay Ship Canal Light | **Fl R 10s** | Red flash 1s, dark 9s |
| 3 | Sturgeon Bay North Pierhead | **Fl R 2.5s** | Red flash 0.5s, dark 2s |
| 4 | Sherwood Point | **Iso W 6s** | White 3s on, 3s off |
| 5 | Eagle Bluff | **Fl W 6s** | White flash 1s, dark 5s |
| 6 | Chambers Island | **Fl W 6s** | Current skeletal ATON (brick tower dark since 1961) |
| 7 | Baileys Harbor Range | **F R** | Fixed red front light (rear is fixed white) |
| 8 | Old Baileys Harbor (Birdcage) | **F W** | Historic fixed white; inactive since 1869 |
| 9 | Cana Island | **F W** | Fixed white |
| 10 | Plum Island Range | **F R** | Fixed red (front and rear) |
| 11 | Pilot Island | **Fl(2) W 6s** | White: on 1, off 1, on 1, off 3 |
| 12 | Pottawatomie (Rock Island) | **Fl W 4s** | White flash 0.5s, dark 3.5s (current ATON) |

Edit `pico/lighthouses.json` (`light.on_s` / `light.off_s`) if you want a different published timing.

## Hardware

See `pico/WIRING.txt`. Minimum:

- Pico 2 W
- WS2812 / NeoPixel strip (13 LEDs or longer)
- Data on **GPIO 0**, common **GND**, 5 V sized for the strip
- Optional: hold **GPIO 15** to GND for 3 seconds at boot to force setup Wi-Fi

## Flash the Pico 2 W

1. Install [MicroPython for Pico 2 W](https://micropython.org/download/RPI_PICO2_W/) (RP2350 + wireless).
2. Copy these files to the Pico root with Thonny or mpremote:
   - `pico/boot.py`
   - `pico/main.py`
   - `pico/wifi_manager.py`
   - `pico/lighthouses.json`
3. Power the board. On first boot it opens **GreatLakes-Setup** (password `door1234`).
4. Join that network on your phone and open **http://192.168.4.1**.
5. Enter home Wi-Fi, LED count, brightness, timezone (`-6` Central standard, `-5` daylight), then **Save & Reboot**.
6. After reboot the strip chases south→north in each light’s color, then runs the real flash patterns.

On the home LAN (and in setup AP mode) the Pico serves:

- `http://<pico-ip>/status`
- `GET/POST http://<pico-ip>/lighthouses` — app fetch/save of the editable list

In the app: set **Pico address** to `192.168.4.1` on GreatLakes-Setup, or the Pico’s LAN IP on home Wi-Fi. **Restore defaults** reloads the bundled Kewaunee → Rock Island set.

If you add more lights than the strip has chips, extra LEDs will not show until you wire them and set LED count.
