import os
import shutil
from pathlib import Path
from PIL import Image, ImageDraw

PACK_DIR = Path(r"C:\Users\chinm\.gemini\antigravity\brain\3431d7d8-c273-4f5a-9a09-6048a1e86ae7\scratch\asset_pack\MuE_Brand_Asset_Pack")
REPO_DIR = Path(r"C:\Users\chinm\Desktop\MuE")
ASSETS_DIR = REPO_DIR / "assets"
BRANDING_DIR = ASSETS_DIR / "branding"

def integrate_assets():
    print("1. Cleaning old branding directory and preparing structure...")
    if BRANDING_DIR.exists():
        shutil.rmtree(BRANDING_DIR)
    
    BRANDING_DIR.mkdir(parents=True, exist_ok=True)
    (BRANDING_DIR / "svg").mkdir(exist_ok=True)
    (BRANDING_DIR / "png").mkdir(exist_ok=True)
    (BRANDING_DIR / "webp").mkdir(exist_ok=True)
    (BRANDING_DIR / "launcher").mkdir(exist_ok=True)
    (BRANDING_DIR / "favicon").mkdir(exist_ok=True)
    (BRANDING_DIR / "splash").mkdir(exist_ok=True)
    (BRANDING_DIR / "ico").mkdir(exist_ok=True)
    (BRANDING_DIR / "icns").mkdir(exist_ok=True)

    print("2. Copying official assets into assets/branding/...")
    # Copy SVG
    for f in (PACK_DIR / "SVG").glob("*.svg"):
        shutil.copy2(f, BRANDING_DIR / "svg" / f.name)
    
    # Copy PNG
    for f in (PACK_DIR / "PNG").glob("*.png"):
        shutil.copy2(f, BRANDING_DIR / "png" / f.name)
        # Also copy directly into BRANDING_DIR for root level access
        shutil.copy2(f, BRANDING_DIR / f.name)
    
    # Copy WEBP
    for f in (PACK_DIR / "WEBP").glob("*.webp"):
        shutil.copy2(f, BRANDING_DIR / "webp" / f.name)

    # Copy Launcher
    for f in (PACK_DIR / "Launcher").glob("*.png"):
        shutil.copy2(f, BRANDING_DIR / "launcher" / f.name)

    # Copy Favicon
    for f in (PACK_DIR / "Favicon").glob("*"):
        shutil.copy2(f, BRANDING_DIR / "favicon" / f.name)

    # Copy Splash
    for f in (PACK_DIR / "Splash").glob("*"):
        shutil.copy2(f, BRANDING_DIR / "splash" / f.name)

    # Copy ICO & ICNS
    for f in (PACK_DIR / "ICO").glob("*.ico"):
        shutil.copy2(f, BRANDING_DIR / "ico" / f.name)
    for f in (PACK_DIR / "ICNS").glob("*.icns"):
        shutil.copy2(f, BRANDING_DIR / "icns" / f.name)

    # Copy manifest and readme
    if (PACK_DIR / "manifest.json").exists():
        shutil.copy2(PACK_DIR / "manifest.json", BRANDING_DIR / "manifest.json")
    if (PACK_DIR / "README.md").exists():
        shutil.copy2(PACK_DIR / "README.md", BRANDING_DIR / "README.md")

    print("3. Updating root assets/ files with official single source of truth...")
    # icon.png -> official dark 1024 launcher
    shutil.copy2(PACK_DIR / "Launcher" / "launcher-dark-1024.png", ASSETS_DIR / "icon.png")
    
    # splash-icon.png -> logo-primary-dark.png
    shutil.copy2(PACK_DIR / "PNG" / "logo-primary-dark.png", ASSETS_DIR / "splash-icon.png")
    
    # favicon.png -> favicon-512.png
    shutil.copy2(PACK_DIR / "Favicon" / "favicon-512.png", ASSETS_DIR / "favicon.png")
    
    # mue-logo.png -> logo-primary-dark.png
    shutil.copy2(PACK_DIR / "PNG" / "logo-primary-dark.png", ASSETS_DIR / "mue-logo.png")
    
    # mue-symbol.png & mue-symbol-light.png
    shutil.copy2(PACK_DIR / "PNG" / "symbol-dark.png", ASSETS_DIR / "mue-symbol.png")
    shutil.copy2(PACK_DIR / "PNG" / "symbol-light.png", ASSETS_DIR / "mue-symbol-light.png")

    # Generate adaptive-icon.png: 1024x1024 transparent canvas with symbol centered in the 66% safe zone (~675px)
    symbol_img = Image.open(PACK_DIR / "PNG" / "symbol-dark.png").convert("RGBA")
    # In symbol-dark.png, the symbol is already centered. Scale it to 640x640 to sit within 1024 safe zone
    symbol_resized = symbol_img.resize((640, 640), Image.Resampling.LANCZOS)
    adaptive_canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    adaptive_canvas.paste(symbol_resized, (192, 192), symbol_resized)
    adaptive_canvas.save(ASSETS_DIR / "adaptive-icon.png")

    print("4. Removing obsolete / outdated assets...")
    obsolete_files = [
        ASSETS_DIR / "mue-logo-source.png",
        ASSETS_DIR / "mue-logo-inner.png",
        ASSETS_DIR / "mue-logo-outer.png",
    ]
    for obs in obsolete_files:
        if obs.exists():
            obs.unlink()
            print(f"   Deleted obsolete: {obs.name}")

    print("5. Generating Android Mipmap density icons (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)...")
    res_dir = REPO_DIR / "app" / "src" / "main" / "res"
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    master_launcher = Image.open(PACK_DIR / "Launcher" / "launcher-dark-1024.png").convert("RGBA")

    for folder_name, size in densities.items():
        density_dir = res_dir / folder_name
        density_dir.mkdir(parents=True, exist_ok=True)
        
        # Standard square/squircle icon
        resized_icon = master_launcher.resize((size, size), Image.Resampling.LANCZOS)
        resized_icon.save(density_dir / "ic_launcher.png", "PNG")

        # Round icon with circular crop
        round_mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(round_mask)
        draw.ellipse((0, 0, size, size), fill=255)
        
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(resized_icon, (0, 0), round_mask)
        round_icon.save(density_dir / "ic_launcher_round.png", "PNG")
        print(f"   Generated {folder_name} ({size}x{size})")

    print("Brand asset integration completed successfully!")

if __name__ == "__main__":
    integrate_assets()

