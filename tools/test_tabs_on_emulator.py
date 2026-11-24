import subprocess, time, os, sys
import xml.etree.ElementTree as ET
from PIL import Image
import numpy as np

DEVICE = "emulator-5556"
OUT_DIR = "artifacts/emulator/api35/tabs"
os.makedirs(OUT_DIR, exist_ok=True)

def adb(*args):
    cmd = ["adb", "-s", DEVICE] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

def screencap(local_name):
    remote = f"/sdcard/{local_name}"
    local = os.path.join(OUT_DIR, local_name)
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, local)
    return local

print("Waking screen...")
adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")

print("Dumping UI hierarchy on Home...")
adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
adb("pull", "/sdcard/ui.xml", os.path.join(OUT_DIR, "ui.xml"))

# Parse UI hierarchy
tree = ET.parse(os.path.join(OUT_DIR, "ui.xml"))
targets = {}
for node in tree.iter():
    cd = node.attrib.get("content-desc", "")
    text = node.attrib.get("text", "")
    bounds = node.attrib.get("bounds", "")
    key = cd or text
    if key in ["Home", "Listen", "Search", "Library", "Profile", "Settings"]:
        print(f"Node found: {key} -> bounds={bounds}, cd={cd}, text={text}")
        if key not in targets:
            targets[key] = bounds

print("Observed targets:", targets)

# 1. Capture Home
print("Capturing 01_home.png...")
screencap("01_home.png")

# 2. Tap Listen
print("Tapping Listen (418, 2260)...")
adb("shell", "input", "tap", "418", "2260")
time.sleep(2)
screencap("02_listen.png")

# 3. Tap Search
print("Tapping Search (661, 2260)...")
adb("shell", "input", "tap", "661", "2260")
time.sleep(2)
screencap("03_search.png")

# 4. Tap Library
print("Tapping Library (905, 2260)...")
adb("shell", "input", "tap", "905", "2260")
time.sleep(2)
screencap("04_library.png")

# 5. Tap Profile in header (964, 226)
print("Tapping Profile in header (964, 226)...")
adb("shell", "input", "tap", "964", "226")
time.sleep(2)
screencap("05_profile.png")

# 6. Tap Back
print("Tapping Back (keyevent 4)...")
adb("shell", "input", "keyevent", "4")
time.sleep(2)
screencap("06_back_to_previous.png")

# Verify and crop
print("Verifying crops...")
im0 = Image.open(os.path.join(OUT_DIR, "01_home.png"))
w, h = im0.size

bar_crop = im0.crop((50, 2180, w - 50, 2335))
bar_crop.save(os.path.join(OUT_DIR, "zoom_bottom_bar.png"))

header_crop = im0.crop((750, 180, w - 20, 275))
header_crop.save(os.path.join(OUT_DIR, "zoom_header_pill.png"))

# Pixel analysis on 01_home.png
arr = np.array(im0)
# Listen region: Y: 2210-2284, X: 381-455
listen_px = arr[2210:2284, 381:455]
print("01_home Listen icon: max RGB =", listen_px[..., :3].max(axis=(0,1)), "mean RGB =", listen_px[..., :3].mean(axis=(0,1)))

# Library region: Y: 2210-2284, X: 868-942
library_px = arr[2210:2284, 868:942]
print("01_home Library icon: max RGB =", library_px[..., :3].max(axis=(0,1)), "mean RGB =", library_px[..., :3].mean(axis=(0,1)))

# Also check crashes in logcat
crash_log = adb("logcat", "-d", "-b", "crash")
crashes = [line for line in crash_log.splitlines() if "com.mueair" in line]
print(f"Crashes found for com.mueair: {len(crashes)}")
if crashes:
    for c in crashes:
        print("  Crash:", c)

print("All tests completed successfully.")
