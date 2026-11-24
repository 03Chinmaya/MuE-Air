import subprocess, time, os, sys
import xml.etree.ElementTree as ET

DEVICES = ["emulator-5554", "emulator-5556"]
BASE_OUT = "artifacts/emulator/dual_verification"
os.makedirs(BASE_OUT, exist_ok=True)

def adb(device, *args):
    cmd = ["adb", "-s", device] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

def dump_nodes(device):
    adb(device, "shell", "uiautomator", "dump", "/sdcard/ui_dump.xml")
    local_path = f"tools/ui_{device}.xml"
    adb(device, "pull", "/sdcard/ui_dump.xml", local_path)
    if not os.path.exists(local_path):
        return []
    try:
        tree = ET.parse(local_path)
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

def find_node(device, target, exact=False):
    nodes = dump_nodes(device)
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

def screencap(device, name):
    out_dir = os.path.join(BASE_OUT, device)
    os.makedirs(out_dir, exist_ok=True)
    remote = f"/sdcard/{name}"
    adb(device, "shell", "screencap", "-p", remote)
    local = os.path.join(out_dir, name)
    adb(device, "pull", remote, local)

def test_device(device):
    print(f"\n==========================================")
    print(f" TESTING ON DEVICE: {device}")
    sdk = adb(device, "shell", "getprop", "ro.build.version.sdk")
    rel = adb(device, "shell", "getprop", "ro.build.version.release")
    print(f" OS: Android {rel} (API {sdk})")
    print(f"==========================================")

    adb(device, "logcat", "-c")

    # Launch app
    print("-> Launching com.mueair.app...")
    adb(device, "shell", "am", "force-stop", "com.mueair.app")
    time.sleep(1)
    adb(device, "shell", "am", "start", "-n", "com.mueair.app/com.mue.app.MainActivity")
    time.sleep(3)
    screencap(device, "00_home.png")

    # Navigate to Settings
    # Find settings icon or tap top right gear
    gear = find_node(device, "Settings")
    if not gear:
        adb(device, "shell", "input", "tap", "833", "147")
    else:
        adb(device, "shell", "input", "tap", str(gear["x"]), str(gear["y"]))
    time.sleep(2)
    screencap(device, "01_settings_root.png")

    results = {}

    # 1. Test Playback Settings and newly active toggles
    print("\n-> Testing Playback Settings...")
    p_node = find_node(device, "Playback")
    if p_node:
        adb(device, "shell", "input", "tap", str(p_node["x"]), str(p_node["y"]))
        time.sleep(1.5)
        screencap(device, "02_playback.png")

        # Scroll down to audio enhancements
        adb(device, "shell", "input", "swipe", "500", "1500", "500", "600", "200")
        time.sleep(1)
        screencap(device, "02_playback_enhancements.png")

        # Verify Lossless Audio, Automix, Spatial Audio toggles exist and tap them
        lossless = find_node(device, "Lossless Audio")
        automix = find_node(device, "Automix")
        spatial = find_node(device, "Spatial Audio")

        results["Playback_Lossless_Audio"] = lossless is not None
        results["Playback_Automix"] = automix is not None
        results["Playback_Spatial_Audio"] = spatial is not None

        if lossless:
            print(f"   Tapping Lossless Audio toggle at ({lossless['x']}, {lossless['y']})...")
            adb(device, "shell", "input", "tap", str(lossless["x"]), str(lossless["y"]))
            time.sleep(0.5)

        if automix:
            print(f"   Tapping Automix toggle at ({automix['x']}, {automix['y']})...")
            adb(device, "shell", "input", "tap", str(automix["x"]), str(automix["y"]))
            time.sleep(0.5)

        if spatial:
            print(f"   Tapping Spatial Audio toggle at ({spatial['x']}, {spatial['y']})...")
            adb(device, "shell", "input", "tap", str(spatial["x"]), str(spatial["y"]))
            time.sleep(0.5)

        screencap(device, "02_playback_toggled.png")

        # Back
        back = find_node(device, "Back")
        if back:
            adb(device, "shell", "input", "tap", str(back["x"]), str(back["y"]))
        else:
            adb(device, "shell", "input", "tap", "84", "158")
        time.sleep(1)
    else:
        results["Playback"] = False

    # 2. Test Appearance & Language settings (Verify Hindi removed)
    print("\n-> Testing Appearance & Language...")
    app_node = find_node(device, "Appearance & Accessibility")
    if not app_node:
        adb(device, "shell", "input", "swipe", "500", "1500", "500", "800", "200")
        time.sleep(0.5)
        app_node = find_node(device, "Appearance & Accessibility")

    if app_node:
        adb(device, "shell", "input", "tap", str(app_node["x"]), str(app_node["y"]))
        time.sleep(1.5)
        screencap(device, "03_appearance.png")

        # Check languages
        sys_def = find_node(device, "System Default")
        eng = find_node(device, "English")
        hindi = find_node(device, "Hindi")

        results["Language_SystemDefault"] = sys_def is not None
        results["Language_English"] = eng is not None
        results["Language_Hindi_Removed"] = (hindi is None)

        print(f"   System Default: {sys_def is not None}")
        print(f"   English: {eng is not None}")
        print(f"   Hindi Removed: {hindi is None} (hindi found = {hindi is not None})")

        # Test selecting English
        if eng:
            adb(device, "shell", "input", "tap", str(eng["x"]), str(eng["y"]))
            time.sleep(0.5)

        # Back
        back = find_node(device, "Back")
        if back:
            adb(device, "shell", "input", "tap", str(back["x"]), str(back["y"]))
        else:
            adb(device, "shell", "input", "tap", "84", "158")
        time.sleep(1)
    else:
        results["Appearance"] = False

    # 3. Test About & Updates (Check for Updates trigger)
    print("\n-> Testing About & Updates...")
    about_node = find_node(device, "About & Updates")
    if not about_node:
        adb(device, "shell", "input", "swipe", "500", "1500", "500", "600", "200")
        time.sleep(0.5)
        about_node = find_node(device, "About & Updates")

    if about_node:
        adb(device, "shell", "input", "tap", str(about_node["x"]), str(about_node["y"]))
        time.sleep(1.5)
        screencap(device, "04_about.png")

        client_v = find_node(device, "Client Version")
        server_v = find_node(device, "Server Version")
        check_update = find_node(device, "Check for Updates")

        results["About_ClientVersion"] = client_v is not None
        results["About_ServerVersion"] = server_v is not None
        results["About_CheckForUpdates"] = check_update is not None

        if check_update:
            print("   Clicking 'Check for Updates'...")
            adb(device, "shell", "input", "tap", str(check_update["x"]), str(check_update["y"]))
            time.sleep(2)
            screencap(device, "04_about_checked.png")

        # Back
        back = find_node(device, "Back")
        if back:
            adb(device, "shell", "input", "tap", str(back["x"]), str(back["y"]))
        else:
            adb(device, "shell", "input", "tap", "84", "158")
        time.sleep(1)
    else:
        results["About"] = False

    # 4. Check Logcat for Crashes
    print("\n-> Checking Logcat for any runtime errors or crashes...")
    logs = adb(device, "logcat", "-d")
    crashes = [l for l in logs.splitlines() if "FATAL EXCEPTION" in l or "AndroidRuntime: E" in l]
    results["Zero_Crashes"] = (len(crashes) == 0)

    print(f"\nDevice {device} Summary:")
    for k, v in results.items():
        print(f"  {'[PASS]' if v else '[FAIL]'} {k}: {v}")

    if crashes:
        print(f"  [ERROR] {len(crashes)} crashes detected!")
        for c in crashes[:5]:
            print(f"    {c}")

    return all(results.values())

def main():
    overall_ok = True
    for d in DEVICES:
        ok = test_device(d)
        if not ok:
            overall_ok = False

    print("\n==========================================")
    if overall_ok:
        print(">>> ALL TESTS PASSED ACROSS BOTH EMULATORS (API 26 + API 37) <<<")
    else:
        print(">>> SOME CHECKS FAILED! <<<")
        sys.exit(1)

if __name__ == "__main__":
    main()
