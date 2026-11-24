import subprocess, time, os, sys
import xml.etree.ElementTree as ET

DEVICE = "emulator-5554"
OUT_DIR = "artifacts/emulator/settings_verification"
os.makedirs(OUT_DIR, exist_ok=True)

def adb(*args):
    cmd = ["adb", "-s", DEVICE] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

def dump_ui():
    adb("shell", "uiautomator", "dump", "/sdcard/ui_temp.xml")
    adb("pull", "/sdcard/ui_temp.xml", "tools/ui_temp.xml")
    if not os.path.exists("tools/ui_temp.xml"):
        return []
    tree = ET.parse("tools/ui_temp.xml")
    nodes = []
    for node in tree.getroot().iter("node"):
        text = node.attrib.get("text", "")
        cd = node.attrib.get("content-desc", "")
        bounds = node.attrib.get("bounds", "")
        parts = bounds.replace("][", ",").replace("[", "").replace("]", "").split(",")
        if len(parts) == 4 and (text or cd):
            x = (int(parts[0]) + int(parts[2])) // 2
            y = (int(parts[1]) + int(parts[3])) // 2
            nodes.append((x, y, text, cd))
    return nodes

def find_node(text_pattern):
    for x, y, text, cd in dump_ui():
        if text_pattern.lower() in text.lower() or text_pattern.lower() in cd.lower():
            return (x, y, text or cd)
    return None

def screencap(name):
    remote = f"/sdcard/{name}"
    adb("shell", "screencap", "-p", remote)
    local = os.path.join(OUT_DIR, name)
    adb("pull", remote, local)

print("=== STARTING FULL SETTINGS VERIFICATION ===")
adb("logcat", "-c")
adb("shell", "am", "force-stop", "com.mueair.app")
time.sleep(1)
adb("shell", "am", "start", "-n", "com.mueair.app/com.mue.app.MainActivity")
time.sleep(3)

# Open Settings (833, 147)
print("1. Opening Settings...")
adb("shell", "input", "tap", "833", "147")
time.sleep(2)
screencap("settings_root.png")

subscreens = [
    ("Playback", "STREAMING QUALITY", "settings_01_playback.png"),
    ("Storage & Downloads", "AUDIO CACHE LIMIT", "settings_02_storage.png"),
    ("Privacy", "LISTENING ACTIVITY", "settings_03_privacy.png"),
    ("Linked Devices", "CROSS-DEVICE PLAYBACK", "settings_04_devices.png"),
    ("Notifications", "PUSH NOTIFICATIONS", "settings_05_notifications.png"),
    ("Appearance & Accessibility", "APPEARANCE MODE", "settings_06_appearance.png"),
    ("About & Updates", "APPLICATION VERSION", "settings_07_about.png"),
    ("Danger Zone", "LOCAL STORAGE & PREFERENCES", "settings_08_danger.png"),
]

passed_screens = []

for title, expected_content, shot_name in subscreens:
    print(f"\n--- Checking '{title}' ---")
    # Reset scroll to top
    adb("shell", "input", "swipe", "500", "500", "500", "1600", "150")
    time.sleep(0.5)

    loc = find_node(title)
    if not loc:
        # scroll down
        adb("shell", "input", "swipe", "500", "1500", "500", "800", "300")
        time.sleep(0.8)
        loc = find_node(title)

    if not loc:
        print(f"FAILED to find row for '{title}'")
        continue

    print(f"  Tapping '{title}' at ({loc[0]}, {loc[1]})...")
    adb("shell", "input", "tap", str(loc[0]), str(loc[1]))
    time.sleep(1.5)

    screencap(shot_name)

    # Verify content
    v = find_node(expected_content)
    if v:
        print(f"  PASS: Found '{expected_content}' in {title}")
        passed_screens.append(title)
    else:
        print(f"  WARNING: Did not find '{expected_content}' in {title}")

    # If appearance screen, test light/dark switching
    if title == "Appearance & Accessibility":
        print("  Testing Theme Mode switching in Appearance screen...")
        light_btn = find_node("Light Mode")
        if light_btn:
            adb("shell", "input", "tap", str(light_btn[0]), str(light_btn[1]))
            time.sleep(1)
            screencap("settings_06_appearance_light.png")
            print("  Switched to Light Mode.")

        dark_btn = find_node("Dark Mode")
        if dark_btn:
            adb("shell", "input", "tap", str(dark_btn[0]), str(dark_btn[1]))
            time.sleep(1)
            screencap("settings_06_appearance_dark.png")
            print("  Switched back to Dark Mode.")

    # Tap Back button at (70, 147)
    adb("shell", "input", "tap", "70", "147")
    time.sleep(1)

# Return to Home from Settings root
adb("shell", "input", "tap", "70", "147")
time.sleep(1)

# Test Profile Screen
print("\n--- Checking 'Account & Profile' ---")
adb("shell", "input", "tap", "964", "147")
time.sleep(2)
screencap("profile_screen.png")

prof = find_node("Account & Profile")
if prof:
    print("  PASS: Profile screen opened successfully.")
    passed_screens.append("Profile")

    # Test opening edit dialog
    edit_row = find_node("Display Name")
    if edit_row:
        adb("shell", "input", "tap", str(edit_row[0]), str(edit_row[1]))
        time.sleep(1)
        screencap("profile_edit_dialog.png")
        cancel_btn = find_node("Cancel")
        if cancel_btn:
            print("  PASS: Display Name edit dialog opened and cancel button active.")
            adb("shell", "input", "tap", str(cancel_btn[0]), str(cancel_btn[1]))
            time.sleep(1)

adb("shell", "input", "tap", "70", "147")
time.sleep(1)

print("\n=== SUMMARY OF PASSED SCREENS ===")
for s in passed_screens:
    print(f"  [✓] {s}")

# Check crashes in logcat
logs = adb("logcat", "-d")
crashes = [l for l in logs.splitlines() if "FATAL EXCEPTION" in l or "AndroidRuntime: E" in l]
if crashes:
    print(f"\nCRASHES FOUND ({len(crashes)}):")
    for c in crashes[:5]:
        print("  ", c)
    sys.exit(1)
else:
    print(f"\n[✓] ZERO CRASHES detected across entire run!")
