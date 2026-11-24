from PIL import Image
import os
import numpy as np

def build_icons():
    src_mue = 'assets/branding/Mue.png'
    im_mue = Image.open(src_mue).convert('RGBA')

    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    for folder, size in densities.items():
        out_dir = os.path.join('app/src/main/res', folder)
        os.makedirs(out_dir, exist_ok=True)
        resized = im_mue.resize((size, size), Image.LANCZOS)
        resized.save(os.path.join(out_dir, 'ic_launcher.png'), 'PNG')
        resized.save(os.path.join(out_dir, 'ic_launcher_round.png'), 'PNG')

    # Adaptive icon foreground
    fg_canvas = Image.new('RGBA', (432, 432), (0, 0, 0, 0))
    mue_fg = im_mue.resize((280, 280), Image.LANCZOS)
    fg_canvas.paste(mue_fg, ((432 - 280) // 2, (432 - 280) // 2), mue_fg)
    fg_canvas.save('app/src/main/res/drawable-nodpi/ic_launcher_foreground.webp', 'WEBP', quality=95)

    # L-T.png icon for ListenTogetherScreen
    im_lt = Image.open('assets/icons/L-T.png')
    arr = np.array(im_lt)
    alpha = arr[..., 3]
    ys, xs = np.where(alpha > 8)
    alpha_crop = alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = alpha_crop.shape
    side = max(h, w)
    canvas = np.zeros((side, side), dtype=np.uint8)
    y0, x0 = (side - h) // 2, (side - w) // 2
    canvas[y0:y0 + h, x0:x0 + w] = alpha_crop
    out = np.zeros((side, side, 4), dtype=np.uint8)
    out[..., 0:3] = 255
    out[..., 3] = canvas
    out_im = Image.fromarray(out, 'RGBA').resize((256, 256), Image.LANCZOS)
    
    os.makedirs('app/src/main/res/drawable-nodpi', exist_ok=True)
    out_im.save('app/src/main/res/drawable-nodpi/l_t.png', 'PNG')
    os.makedirs('core/design/src/main/res/drawable-nodpi', exist_ok=True)
    out_im.save('core/design/src/main/res/drawable-nodpi/l_t.png', 'PNG')

    print('All app icons and drawables built successfully.')

if __name__ == '__main__':
    build_icons()
