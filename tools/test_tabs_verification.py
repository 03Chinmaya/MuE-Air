import subprocess, time, os
import xml.etree.ElementTree as ET
from PIL import Image
import numpy as np

DEVICE = "emulator-5554"
OUT_DIR = "artifacts/emulator/verification"
os.makedirs(OUT_DIR, exist_ok=True)

def adb(*args):
    cmd = ["adb", "-s", DEVICE] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

print("Installing app-debug.apk on emulator-5554...")
install_out = adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
print("Install output:", install_out)

print("Granting storage permission and launching...")
adb("shell", "pm", "grant", "com.mueair.app", "android.permission.READ_EXTERNAL_STORAGE")
adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")
adb("logcat", "-c")
adb("shell", "am", "force-stop", "com.mueair.app")
adb("shell", "am", "start", "-n", "com.mueair.app/com.mue.app.MainActivity")
time.sleep(4)

def screencap(name):
    remote = f"/sdcard/{name}"
    adb("shell", "screencap", "-p", remote)
    local = os.path.join(OUT_DIR, name)
    adb("pull", remote, local)
    return local

# 1. Capture Home Tab
print("1. Capturing Home tab...")
home_png = screencap("01_home.png")
adb("shell", "uiautomator", "dump", "/sdcard/ui_home.xml")
adb("pull", "/sdcard/ui_home.xml", os.path.join(OUT_DIR, "ui_home.xml"))

# 2. Tap Listen Tab (418, 2160)
print("2. Tapping Listen Together tab...")
adb("shell", "input", "tap", "418", "2160")
time.sleep(3)
screencap("02_listen.png")
adb("shell", "uiautomator", "dump", "/sdcard/ui_listen.xml")
adb("pull", "/sdcard/ui_listen.xml", os.path.join(OUT_DIR, "ui_listen.xml"))

# 3. Tap Search Tab (661, 2160)
print("3. Tapping Search tab (Quick Picks)...")
adb("shell", "input", "tap", "661", "2160")
time.sleep(2)
screencap("03_search.png")
adb("shell", "uiautomator", "dump", "/sdcard/ui_search.xml")
adb("pull", "/sdcard/ui_search.xml", os.path.join(OUT_DIR, "ui_search.xml"))

# 4. Tap Library Tab (905, 2160)
print("4. Tapping Library tab...")
adb("shell", "input", "tap", "905", "2160")
time.sleep(3)
screencap("04_library_all.png")
adb("shell", "uiautomator", "dump", "/sdcard/ui_library.xml")
adb("pull", "/sdcard/ui_library.xml", os.path.join(OUT_DIR, "ui_library.xml"))

# 5. Tap Filter Chip "Liked" in Library
print("5. Tapping Liked filter chip...")
# Liked chip is second chip in row, near (165, 230)
# Let's find its bounds from ui_library.xml if possible, or tap
adb("shell", "input", "tap", "165", "230")
time.sleep(2)
screencap("05_library_liked_filter.png")

# 6. Tap Sort & Filter button (top right of filter bar, near 1010, 230)
print("6. Tapping Sort & Filter button...")
adb("shell", "input", "tap", "1010", "230")
time.sleep(2)
screencap("06_library_sort_sheet.png")

# 7. Tap outside to dismiss bottom sheet
adb("shell", "input", "keyevent", "4")
time.sleep(1)

# Check for crashes
crash_log = adb("logcat", "-d", "-b", "crash")
crashes = [line for line in crash_log.splitlines() if "com.mueair" in line]
print(f"Crashes found for com.mueair: {len(crashes)}")
if crashes:
    for c in crashes:
        print("  CRASH:", c)
else:
    print("Zero crashes detected. Application is stable!")
