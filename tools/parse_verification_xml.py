import xml.etree.ElementTree as ET
import os

files = ['ui_home.xml', 'ui_listen.xml', 'ui_search.xml', 'ui_library.xml', 'ui_sort_sheet.xml']
for f in files:
    path = os.path.join('artifacts/emulator/verification', f)
    if not os.path.exists(path):
        continue
    tree = ET.parse(path)
    print(f"\n=================== {f} ===================")
    seen = set()
    for node in tree.iter('node'):
        text = node.attrib.get('text', '').strip()
        desc = node.attrib.get('content-desc', '').strip()
        bounds = node.attrib.get('bounds', '')
        if text or desc:
            entry = f'text: "{text}" | desc: "{desc}" | bounds: {bounds}'
            if entry not in seen:
                seen.add(entry)
                print(" ", entry)
