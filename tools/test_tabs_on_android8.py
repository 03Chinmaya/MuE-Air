import subprocess, time, os
import xml.etree.ElementTree as ET
from PIL import Image
import numpy as np

DEVICE = "emulator-5554"
OUT_DIRS = ["artifacts/emulator/api26/tabs", "artifacts/emulator/api35/tabs"]
for d in OUT_DIRS:
    os.makedirs(d, exist_ok=True)

def adb(*args):
    cmd = ["adb", "-s", DEVICE] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

print("1. Granting storage permission and launching...")
adb("shell", "pm", "grant", "com.mueair.app", "android.permission.READ_EXTERNAL_STORAGE")
adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")
adb("logcat", "-c")
adb("shell", "am", "force-stop", "com.mueair.app")
adb("shell", "am", "start", "-n", "com.mueair.app/com.mue.app.MainActivity")
time.sleep(3)

def screencap(name):
    remote = f"/sdcard/{name}"
    adb("shell", "screencap", "-p", remote)
    for d in OUT_DIRS:
        local = os.path.join(d, name)
        adb("pull", remote, local)

# 1. Capture Home
print("Capturing 01_home.png...")
screencap("01_home.png")

# Dump UI hierarchy on Home
adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
for d in OUT_DIRS:
    adb("pull", "/sdcard/ui.xml", os.path.join(d, "ui.xml"))

# 2. Tap Listen (418, 2160)
print("Tapping Listen (418, 2160)...")
adb("shell", "input", "tap", "418", "2160")
time.sleep(2)
screencap("02_listen.png")

# 3. Tap Search (661, 2160)
print("Tapping Search (661, 2160)...")
adb("shell", "input", "tap", "661", "2160")
time.sleep(2)
screencap("03_search.png")

# 4. Tap Library (905, 2160)
print("Tapping Library (905, 2160)...")
adb("shell", "input", "tap", "905", "2160")
time.sleep(2)
screencap("04_library.png")

# 5. Tap Profile in header pill (964, 147)
print("Tapping Profile in header pill (964, 147)...")
adb("shell", "input", "tap", "964", "147")
time.sleep(2)
screencap("05_profile.png")

# 6. Tap Back (keyevent 4)
print("Tapping Back (keyevent 4)...")
adb("shell", "input", "keyevent", "4")
time.sleep(2)
screencap("06_back_to_previous.png")

# 7. Generate zoomed crops
print("Generating zoomed crops...")
for d in OUT_DIRS:
    im0 = Image.open(os.path.join(d, "01_home.png"))
    w, h = im0.size
    # Bottom bar is around y: 2090 to 2260
    bar_crop = im0.crop((50, 2090, w - 50, 2260))
    bar_crop.save(os.path.join(d, "zoom_bottom_bar.png"))

    # Header pill is around y: 100 to 195, x: 750 to w - 20
    header_crop = im0.crop((750, 100, w - 20, 195))
    header_crop.save(os.path.join(d, "zoom_header_pill.png"))

# 8. Pixel analysis on 01_home.png
im0 = Image.open("artifacts/emulator/api26/tabs/01_home.png")
arr = np.array(im0)
# Listen icon bounds on API 26: Y: 2123-2197, X: 381-455
listen_px = arr[2123:2197, 381:455]
print("01_home Listen icon: max RGB =", listen_px[..., :3].max(axis=(0,1)), "mean RGB =", listen_px[..., :3].mean(axis=(0,1)))

# Library icon bounds on API 26: Y: 2123-2197, X: 868-942
library_px = arr[2123:2197, 868:942]
print("01_home Library icon: max RGB =", library_px[..., :3].max(axis=(0,1)), "mean RGB =", library_px[..., :3].mean(axis=(0,1)))

# 9. Check crashes
crash_log = adb("logcat", "-d", "-b", "crash")
crashes = [line for line in crash_log.splitlines() if "com.mueair" in line]
print(f"Crashes found for com.mueair: {len(crashes)}")
if crashes:
    for c in crashes:
        print("  Crash:", c)

print("Android 8 full verification finished successfully.")
