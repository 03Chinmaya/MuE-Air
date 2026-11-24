import subprocess, time, os, sys
import xml.etree.ElementTree as ET

DEVICE = "emulator-5556"
OUT_DIR = "artifacts/emulator/dual_verification/emulator-5556"
os.makedirs(OUT_DIR, exist_ok=True)

def adb(*args):
    cmd = ["adb", "-s", DEVICE] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

def dump_nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui_5556.xml")
    local = "tools/ui_5556_test.xml"
    adb("pull", "/sdcard/ui_5556.xml", local)
    if not os.path.exists(local):
        return []
    try:
        tree = ET.parse(local)
    except Exception:
        return []
    nodes = []
    for node in tree.getroot().iter("node"):
        text = node.attrib.get("text", "")
        cd = node.attrib.get("content-desc", "")
        bounds = node.attrib.get("bounds", "")
        parts = bounds.replace("][", ",").replace("[", "").replace("]", "").split(",")
        if len(parts) == 4 and (text or cd):
            x = (int(parts[0]) + int(parts[2])) // 2
            y = (int(parts[1]) + int(parts[3])) // 2
            nodes.append({
                "x": x,
                "y": y,
                "text": text,
                "cd": cd,
                "bounds": bounds
            })
    return nodes

def find_node(target, exact=False):
    nodes = dump_nodes()
    for n in nodes:
        t = n["text"]
        c = n["cd"]
        if exact:
            if target.lower() == t.lower() or target.lower() == c.lower():
                return n
        else:
            if target.lower() in t.lower() or target.lower() in c.lower():
                return n
    return None

def screencap(name):
    remote = f"/sdcard/{name}"
    adb("shell", "screencap", "-p", remote)
    local = os.path.join(OUT_DIR, name)
    adb("pull", remote, local)

print("=== VERIFYING EMULATOR-5556 (ANDROID 17) ===")
adb("logcat", "-c")

# Launch
print("-> Starting MuE-Air...")
adb("shell", "am", "start", "-n", "com.mueair.app/com.mue.app.MainActivity")
time.sleep(3)
screencap("00_home.png")

# Tap Settings button at top right
print("-> Opening Settings...")
adb("shell", "input", "tap", "833", "147")
time.sleep(2)
screencap("01_settings_root.png")

# 1. Playback
print("-> Checking Playback...")
pb = find_node("Playback")
if pb:
    adb("shell", "input", "tap", str(pb["x"]), str(pb["y"]))
    time.sleep(1.5)
    adb("shell", "input", "swipe", "500", "1600", "500", "400", "300")
    time.sleep(1)
    screencap("02_playback_enhancements.png")

    lossless = find_node("Lossless Audio")
    automix = find_node("Automix")
    spatial = find_node("Spatial Audio")

    print(f"   Lossless Audio toggle: {lossless is not None}")
    print(f"   Automix toggle: {automix is not None}")
    print(f"   Spatial Audio toggle: {spatial is not None}")

    if lossless:
        adb("shell", "input", "tap", str(lossless["x"]), str(lossless["y"]))
        time.sleep(0.5)
    if automix:
        adb("shell", "input", "tap", str(automix["x"]), str(automix["y"]))
        time.sleep(0.5)
    if spatial:
        adb("shell", "input", "tap", str(spatial["x"]), str(spatial["y"]))
        time.sleep(0.5)

    screencap("02_playback_toggled.png")

# Back to root
adb("shell", "input", "keyevent", "4")
time.sleep(1)

# 2. Appearance
print("-> Checking Appearance & Language...")
app_n = find_node("Appearance & Accessibility")
if not app_n:
    adb("shell", "input", "swipe", "500", "1500", "500", "700", "200")
    time.sleep(0.5)
    app_n = find_node("Appearance & Accessibility")

if app_n:
    adb("shell", "input", "tap", str(app_n["x"]), str(app_n["y"]))
    time.sleep(1.5)
    screencap("03_appearance.png")

    sys_def = find_node("System Default")
    eng = find_node("English")
    hindi = find_node("Hindi")

    print(f"   System Default present: {sys_def is not None}")
    print(f"   English present: {eng is not None}")
    print(f"   Hindi completely removed: {hindi is None}")

# Back to root
adb("shell", "input", "keyevent", "4")
time.sleep(1)

# 3. About & Updates
print("-> Checking About & Updates...")
about_n = find_node("About & Updates")
if not about_n:
    adb("shell", "input", "swipe", "500", "1500", "500", "700", "200")
    time.sleep(0.5)
    about_n = find_node("About & Updates")

if about_n:
    adb("shell", "input", "tap", str(about_n["x"]), str(about_n["y"]))
    time.sleep(1.5)
    screencap("04_about.png")

    client_v = find_node("Client Version")
    server_v = find_node("Server Version")
    chk = find_node("Check for Updates")

    print(f"   Client Version present: {client_v is not None}")
    print(f"   Server Version present: {server_v is not None}")
    print(f"   Check for Updates button present: {chk is not None}")

    if chk:
        print("   Triggering Check for Updates...")
        adb("shell", "input", "tap", str(chk["x"]), str(chk["y"]))
        time.sleep(2)
        screencap("04_about_checked.png")

# Back
adb("shell", "input", "keyevent", "4")
time.sleep(1)

# Logcat check
logs = adb("logcat", "-d")
crashes = [l for l in logs.splitlines() if "FATAL EXCEPTION" in l or "AndroidRuntime: E" in l]
if crashes:
    print(f"\nCRASHES DETECTED on emulator-5556: {len(crashes)}")
    for c in crashes[:5]:
        print("  ", c)
    sys.exit(1)
else:
    print("\n[✓] ZERO CRASHES ON EMULATOR-5556 (ANDROID 17)")

print("\nEMULATOR-5556 VERIFICATION COMPLETE!")
