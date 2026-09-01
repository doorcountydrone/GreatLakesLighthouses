# Run before main.py. Check the setup button first — compiling main.py takes
# several seconds, so a 3s hold at plug-in was already over before main() looked.
# Then, if an OTA update was staged, swap *_new.* onto the live names.
#
# *_backup.* files are the previous copy, not from GitHub. Safe to delete after a
# successful boot on the new firmware if you need flash space.

import os
import json

PENDING_FILE = "update_pending.json"
FORCE_AP_FLAG = "force_ap.flag"
FORCE_AP_BUTTON_PIN = 15


def _backup_name(name):
    if "." in name:
        base, ext = name.rsplit(".", 1)
        return base + "_backup." + ext
    return name + "_backup"


try:
    import machine
    import utime as time

    pin = machine.Pin(FORCE_AP_BUTTON_PIN, machine.Pin.IN, machine.Pin.PULL_UP)
    if pin.value() == 0:
        t0 = time.ticks_ms()
        while pin.value() == 0:
            if time.ticks_diff(time.ticks_ms(), t0) >= 3000:
                with open(FORCE_AP_FLAG, "w") as f:
                    f.write("1")
                print("boot: setup button held — AP next")
                break
            time.sleep_ms(20)
except Exception as e:
    print("boot button:", e)


try:
    if PENDING_FILE in os.listdir():
        with open(PENDING_FILE, "r") as f:
            pending = json.load(f)
        files = pending.get("files", [])
        names = os.listdir()
        for entry in files:
            name = entry.get("name")
            temp = entry.get("temp")
            if not name or not temp:
                continue
            backup = _backup_name(name)
            try:
                if backup in names:
                    os.remove(backup)
                    names = os.listdir()
            except Exception as e:
                print("boot: remove old", backup, e)
            try:
                if name in names:
                    os.rename(name, backup)
                    names = os.listdir()
            except Exception as e:
                print("boot: backup", name, "failed:", e)
            try:
                if temp in names:
                    os.rename(temp, name)
                    print("boot: applied", name)
                    names = os.listdir()
            except Exception as e:
                print("boot: apply", temp, "failed:", e)
        try:
            os.remove(PENDING_FILE)
        except Exception:
            pass
except Exception as e:
    print("boot:", e)
