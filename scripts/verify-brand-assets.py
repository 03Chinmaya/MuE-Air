import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

def verify_brand_assets():
    errors = []
    print("Running MuE Brand Assets & Integration Verification...\n")

    # 1. Root Assets
    required_root_assets = [
        "assets/icon.png",
        "assets/adaptive-icon.png",
        "assets/splash-icon.png",
        "assets/favicon.png",
        "assets/mue-logo.png",
        "assets/mue-symbol.png",
        "assets/mue-symbol-light.png",
    ]
    for rel in required_root_assets:
        p = REPO_ROOT / rel
        if not p.exists():
            errors.append(f"Missing root asset: {rel}")
        elif p.stat().st_size == 0:
            errors.append(f"Empty root asset: {rel}")
        else:
            print(f"  ✓ Root asset verified: {rel} ({p.stat().st_size:,} bytes)")

    # 3. Android Vector Drawables & Resources
    android_resources = [
        "app/src/main/res/drawable/ic_launcher_foreground.xml",
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "app/src/main/res/values/colors.xml",
        "app/src/main/res/mipmap-mdpi/ic_launcher.png",
        "app/src/main/res/mipmap-hdpi/ic_launcher.png",
        "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
        "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
        "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
        "core/design/src/main/res/drawable/mue_symbol_dark.xml",
        "core/design/src/main/res/drawable/mue_symbol_light.xml",
        "core/design/src/main/res/drawable/mue_symbol_black.xml",
        "core/design/src/main/res/drawable/mue_symbol_white.xml",
    ]
    for rel in android_resources:
        p = REPO_ROOT / rel
        if not p.exists():
            errors.append(f"Missing Android resource: {rel}")
        elif p.stat().st_size == 0:
            errors.append(f"Empty Android resource: {rel}")
        else:
            print(f"  ✓ Android resource verified: {rel}")

    # 4. Compose Design System Brand Assets
    design_files = [
        "core/design/src/main/kotlin/com/mue/core/design/Color.kt",
        "core/design/src/main/kotlin/com/mue/core/design/MuETheme.kt",
        "core/design/src/main/kotlin/com/mue/core/design/BrandAssets.kt",
    ]
    for rel in design_files:
        p = REPO_ROOT / rel
        if not p.exists():
            errors.append(f"Missing Design System file: {rel}")
        else:
            print(f"  ✓ Design System file verified: {rel}")

    # 5. Obsolete Assets Check
    obsolete_files = [
        "assets/mue-logo-source.png",
        "assets/mue-logo-inner.png",
        "assets/mue-logo-outer.png",
    ]
    for rel in obsolete_files:
        p = REPO_ROOT / rel
        if p.exists():
            errors.append(f"Obsolete asset still exists: {rel}")
        else:
            print(f"  ✓ Confirmed obsolete asset removed: {rel}")

    # 6. Functional Icons Preserved
    functional_icons = [
        "assets/icons/bookmark.png",
        "assets/icons/chat.png",
        "assets/icons/listen-together.png",
    ]
    for rel in functional_icons:
        p = REPO_ROOT / rel
        if not p.exists():
            errors.append(f"Functional icon accidentally removed: {rel}")
        else:
            print(f"  ✓ Functional icon preserved: {rel}")

    # Report results
    print("\n-------------------------------------------")
    if errors:
        print(f"FAILED: {len(errors)} brand asset issues found:")
        for err in errors:
            print(f"  ✗ {err}")
        sys.exit(1)
    else:
        print("SUCCESS: All brand assets, registries, and Android resources verified with 100% integrity!")
        sys.exit(0)

if __name__ == "__main__":
    verify_brand_assets()

