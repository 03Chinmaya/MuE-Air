from PIL import Image
import numpy as np, os
from collections import deque

SRC_ICONS, OUT_APP, OUT_CORE = "assets/icons", "app/src/main/res/drawable-nodpi", "core/design/src/main/res/drawable-nodpi"
os.makedirs(OUT_APP, exist_ok=True)
os.makedirs(OUT_CORE, exist_ok=True)

def fill_interior(alpha_mask):
    """Fills enclosed hollow spaces inside the outline so icon is solid/filled like Home."""
    h, w = alpha_mask.shape
    grid = np.zeros((h + 2, w + 2), dtype=bool)
    grid[1:h+1, 1:w+1] = (alpha_mask > 30)

    visited = np.zeros_like(grid, dtype=bool)
    q = deque([(0, 0)])
    visited[0, 0] = True
    while q:
        y, x = q.popleft()
        for dy, dx in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h + 2 and 0 <= nx < w + 2:
                if not visited[ny, nx] and not grid[ny, nx]:
                    visited[ny, nx] = True
                    q.append((ny, nx))

    # Any interior cell not reached from the outside is filled
    filled = ~visited[1:h+1, 1:w+1]
    return filled

def to_filled_tab_icon(path):
    im = Image.open(path)
    a = np.array(im).astype(np.float32)
    if im.mode == "RGBA" and a[..., 3].min() < 128:
        raw_alpha = a[..., 3].astype(np.uint8)
    else:
        raw_alpha = (255 - a[..., :3].min(axis=2)).clip(0, 255).astype(np.uint8)

    ys, xs = np.where(raw_alpha > 8)
    alpha_crop = raw_alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

    # Fill internal spaces so it becomes a solid filled icon
    filled_mask = fill_interior(alpha_crop)
    alpha_filled = (filled_mask.astype(np.uint8) * 255)

    h, w = alpha_filled.shape
    side = max(h, w)
    canvas = np.zeros((side, side), dtype=np.uint8)
    y0, x0 = (side - h) // 2, (side - w) // 2
    canvas[y0:y0 + h, x0:x0 + w] = alpha_filled

    out = np.zeros((side, side, 4), dtype=np.uint8)
    out[..., 0:3] = 255
    out[..., 3] = canvas
    return Image.fromarray(out, "RGBA").resize((96, 96), Image.LANCZOS)

def build_header_logo():
    src_logo = "assets/branding/MuE-logo.png" if os.path.exists("assets/branding/MuE-logo.png") else "assets/branding/MuE-Logo.png"
    im = Image.open(src_logo)
    crop = im.crop(im.getbbox())
    crop.save(f"{OUT_CORE}/ic_mue_logo.png", "PNG")
    crop.save(f"{OUT_APP}/ic_mue_logo.png", "PNG")
    print(f"Header logo cropped and saved from {src_logo}: {crop.size}")

if __name__ == "__main__":
    # Tab icons (filled like Home icon)
    lt_icon = to_filled_tab_icon(f"{SRC_ICONS}/L-T.png")
    lt_icon.save(f"{OUT_APP}/ic_tab_listen.webp", "WEBP", quality=95, method=6)
    lt_icon.save(f"{OUT_CORE}/ic_tab_listen.webp", "WEBP", quality=95, method=6)

    lib_icon = to_filled_tab_icon(f"{SRC_ICONS}/Library.png")
    lib_icon.save(f"{OUT_APP}/ic_tab_library.webp", "WEBP", quality=95, method=6)
    lib_icon.save(f"{OUT_CORE}/ic_tab_library.webp", "WEBP", quality=95, method=6)

    # Header logo
    build_header_logo()

    print("Tab icons and header logo built successfully.")
