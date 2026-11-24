import xml.etree.ElementTree as ET
import os
from PIL import Image

for target_dir in ["artifacts/emulator/api26/tabs", "artifacts/emulator/api35/tabs"]:
    xml_path = os.path.join(target_dir, "ui.xml")
    print(f"=== Inspecting {xml_path} ===")
    if os.path.exists(xml_path):
        tree = ET.parse(xml_path)
        for node in tree.iter():
            cd = node.attrib.get("content-desc", "")
            text = node.attrib.get("text", "")
            bounds = node.attrib.get("bounds", "")
            if cd in ["Home", "Listen", "Search", "Library", "Profile", "Settings"] or text in ["Home", "Listen", "Search", "Library", "Profile", "Settings"]:
                print(f"  {cd or text}: bounds={bounds}, cd='{cd}', text='{text}'")
    
    print(f"=== Files in {target_dir} ===")
    for f in sorted(os.listdir(target_dir)):
        p = os.path.join(target_dir, f)
        if f.endswith(".png"):
            im = Image.open(p)
            print(f"  {f}: {im.size}, mode={im.mode}, {os.path.getsize(p)} bytes")
        else:
            print(f"  {f}: {os.path.getsize(p)} bytes")
