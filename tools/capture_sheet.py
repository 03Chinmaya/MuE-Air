import subprocess, time

def adb(*args):
    return subprocess.run(['adb', '-s', 'emulator-5554'] + list(args), capture_output=True, text=True).stdout.strip()

# Tap Library tab
adb('shell', 'input', 'tap', '905', '2160')
time.sleep(2)

# Tap Sort & Filter button (994, 277)
adb('shell', 'input', 'tap', '994', '277')
time.sleep(2)

adb('shell', 'screencap', '-p', '/sdcard/06_sort_sheet.png')
adb('pull', '/sdcard/06_sort_sheet.png', 'artifacts/emulator/verification/06_sort_sheet.png')
adb('shell', 'uiautomator', 'dump', '/sdcard/ui_sort_sheet.xml')
adb('pull', '/sdcard/ui_sort_sheet.xml', 'artifacts/emulator/verification/ui_sort_sheet.xml')

# Dismiss sheet
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)
print("Captured sheet!")
