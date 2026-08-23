# Runs before main.py. Placeholder for later OTA apply (same idea as MetarMap).
import os

PENDING_FILE = "update_pending.json"

try:
    if PENDING_FILE in os.listdir():
        print("boot: OTA pending file found (not applied in v1)")
except Exception as e:
    print("boot:", e)
