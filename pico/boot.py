# Run before main.py. If an OTA update was staged, swap *_new.* onto the live names.
#
# *_backup.* files are the previous copy, not from GitHub. Safe to delete after a
# successful boot on the new firmware if you need flash space.

import os
import json

PENDING_FILE = "update_pending.json"


def _backup_name(name):
    if "." in name:
        base, ext = name.rsplit(".", 1)
        return base + "_backup." + ext
    return name + "_backup"


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
