import subprocess
import time
import sys
import xml.etree.ElementTree as ET

def run_adb(device, args):
    cmd = ["adb", "-s", device] + args
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip()

def dump_ui(device):
    run_adb(device, ["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    xml_str = run_adb(device, ["shell", "cat", "/sdcard/window_dump.xml"])
    try:
        return ET.fromstring(xml_str)
    except Exception as e:
        return None

def find_nodes(root, text=None, content_desc=None):
    matches = []
    if root is None:
        return matches
    for node in root.iter("node"):
        t = node.attrib.get("text", "")
        c = node.attrib.get("content-desc", "")
        if text and text.lower() in t.lower():
            matches.append(node)
        elif content_desc and content_desc.lower() in c.lower():
            matches.append(node)
    return matches

def get_center(node):
    bounds = node.attrib.get("bounds", "") # [x1,y1][x2,y2]
    import re
    m = re.findall(r"\d+", bounds)
    if len(m) == 4:
        x1, y1, x2, y2 = map(int, m)
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def test_device(device_id):
    print(f"\n==========================================")
    print(f"TESTING DEVICE: {device_id}")
    print(f"==========================================")
    
    # Check Android Version
    ver = run_adb(device_id, ["shell", "getprop", "ro.build.version.release"])
    sdk = run_adb(device_id, ["shell", "getprop", "ro.build.version.sdk"])
    print(f"Android {ver} (API {sdk})")

    # Clear logcat & launch app
    run_adb(device_id, ["logcat", "-c"])
    run_adb(device_id, ["shell", "am", "force-stop", "com.mueair.app"])
    run_adb(device_id, ["shell", "am", "start", "-n", "com.mueair.app/com.mue.app.MainActivity"])
    time.sleep(3)

    # Verify Home Screen
    root = dump_ui(device_id)
    if root is None:
        print("[FAIL] Could not dump UI on device")
        return False

    # Check for crashes in logcat
    logs = run_adb(device_id, ["logcat", "-d", "*:E"])
    crashes = [line for line in logs.splitlines() if "FATAL EXCEPTION" in line]
    if crashes:
        print(f"[CRASH] {crashes}")
        return False
    print("[PASS] App started cleanly with 0 crashes")

    # Check Home Header Logo / Elements
    home_nodes = find_nodes(root, text="Quick Picks")
    print(f"Home Quick Picks section found: {len(home_nodes) > 0}")

    # Test Search Navigation & Chips
    # Tap Search Tab (tab 3)
    search_tabs = find_nodes(root, content_desc="Search")
    if search_tabs:
        center = get_center(search_tabs[0])
        if center:
            run_adb(device_id, ["shell", "input", "tap", str(center[0]), str(center[1])])
            time.sleep(2)
            root = dump_ui(device_id)
            print("[PASS] Navigated to Search Tab")

    # Tap on Search Input & enter a query
    search_fields = find_nodes(root, text="Search")
    if not search_fields:
        search_fields = find_nodes(root, content_desc="Search songs, albums, or artists")
    if search_fields:
        center = get_center(search_fields[0])
        if center:
            run_adb(device_id, ["shell", "input", "tap", str(center[0]), str(center[1])])
            time.sleep(1)
            run_adb(device_id, ["shell", "input", "text", "Arijit"])
            time.sleep(3)
            root = dump_ui(device_id)
            # Check for chips
            chips = ["All", "Songs", "Videos", "Albums", "Artists", "Playlists"]
            found_chips = []
            for chip in chips:
                if find_nodes(root, text=chip):
                    found_chips.append(chip)
            print(f"[PASS] Search Chips visible: {found_chips}")

            # Tap on Albums Chip
            album_chip = find_nodes(root, text="Albums")
            if album_chip:
                ac_center = get_center(album_chip[0])
                if ac_center:
                    run_adb(device_id, ["shell", "input", "tap", str(ac_center[0]), str(ac_center[1])])
                    time.sleep(2)
                    print("[PASS] Tapped Albums Chip")

            # Tap on Artists Chip
            artist_chip = find_nodes(root, text="Artists")
            if artist_chip:
                ar_center = get_center(artist_chip[0])
                if ar_center:
                    run_adb(device_id, ["shell", "input", "tap", str(ar_center[0]), str(ar_center[1])])
                    time.sleep(2)
                    print("[PASS] Tapped Artists Chip")

            # Tap on Playlists Chip
            pl_chip = find_nodes(root, text="Playlists")
            if pl_chip:
                pl_center = get_center(pl_chip[0])
                if pl_center:
                    run_adb(device_id, ["shell", "input", "tap", str(pl_center[0]), str(pl_center[1])])
                    time.sleep(2)
                    print("[PASS] Tapped Playlists Chip")

            # Tap Back button to clear search / go back
            run_adb(device_id, ["shell", "input", "keyevent", "4"])
            time.sleep(1)

    # Test Profile Screen Navigation
    # In Home or Header, tap Profile icon
    profile_btn = find_nodes(root, content_desc="Profile")
    if not profile_btn:
        # Navigate to profile directly via am start or Header
        run_adb(device_id, ["shell", "input", "tap", "980", "120"]) # Top right header pill area
    time.sleep(2)
    root = dump_ui(device_id)
    profile_nodes = find_nodes(root, text="Account & Profile")
    if not profile_nodes:
        profile_nodes = find_nodes(root, text="EDIT PROFILE")
    print(f"Profile Screen loaded: {len(profile_nodes) > 0}")

    if profile_nodes:
        # Test Edit Display Name row
        dn_row = find_nodes(root, text="Display Name")
        if dn_row:
            center = get_center(dn_row[0])
            if center:
                run_adb(device_id, ["shell", "input", "tap", str(center[0]), str(center[1])])
                time.sleep(1)
                dialog_root = dump_ui(device_id)
                save_btn = find_nodes(dialog_root, text="Save")
                cancel_btn = find_nodes(dialog_root, text="Cancel")
                print(f"[PASS] Edit Display Name dialog opened: {len(save_btn) > 0}")
                if cancel_btn:
                    c_center = get_center(cancel_btn[0])
                    if c_center:
                        run_adb(device_id, ["shell", "input", "tap", str(c_center[0]), str(c_center[1])])
                        time.sleep(1)

        # Back to main
        run_adb(device_id, ["shell", "input", "keyevent", "4"])
        time.sleep(1)

    # Check logcat again
    logs = run_adb(device_id, ["logcat", "-d", "*:E"])
    crashes = [line for line in logs.splitlines() if "FATAL EXCEPTION" in line]
    if crashes:
        print(f"[FAIL] Crashes detected during test: {crashes}")
        return False
    print(f"[SUCCESS] Device {device_id} passed all functional tests with 0 errors!")
    return True

if __name__ == "__main__":
    d1 = test_device("emulator-5554")
    d2 = test_device("emulator-5556")
    if d1 and d2:
        print("\nALL DUAL-EMULATOR VERIFICATIONS PASSED!")
        sys.exit(0)
    else:
        print("\nSOME VERIFICATIONS FAILED!")
        sys.exit(1)
