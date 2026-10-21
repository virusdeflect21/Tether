"""Generate Tether brand assets from the master logo.

Requires Pillow. Run from the repository root:

    python tools/generate_brand_assets.py

The master file is logo.png (1254x1254). It is never modified.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
LOGO = ROOT / "logo.png"

MOBILE_RES = ROOT / "tether-mobile" / "src" / "main" / "res"
TV_RES = ROOT / "tether-tv" / "src" / "main" / "res"
DESIGN_RES = ROOT / "core" / "design" / "src" / "main" / "res"

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def feather_alpha(rgb: np.ndarray) -> np.ndarray:
    """Recover the white feather with anti-aliased alpha from the flat logo."""
    lowest = rgb.min(axis=2).astype(np.float32)
    alpha = np.clip((lowest - 110.0) / 120.0, 0.0, 1.0)
    # Kill faint noise and keep the solid interior.
    return np.where(alpha > 0.08, alpha, 0.0)


def remove_feather(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """Fill the feather area by horizontal interpolation of the background."""
    out = rgb.astype(np.float32).copy()
    mask = alpha > 0.02
    # Dilate the mask a little so anti-aliased edges are removed too.
    for _ in range(3):
        grown = mask.copy()
        grown[1:, :] |= mask[:-1, :]
        grown[:-1, :] |= mask[1:, :]
        grown[:, 1:] |= mask[:, :-1]
        grown[:, :-1] |= mask[:, 1:]
        mask = grown
    height, width = mask.shape
    for y in range(height):
        row_mask = mask[y]
        if not row_mask.any():
            continue
        xs = np.arange(width)
        known = ~row_mask
        if known.sum() < 2:
            continue
        for channel in range(3):
            out[y, :, channel] = np.interp(xs, xs[known], out[y, known, channel])
    # Soften remaining vertical seams from interpolation.
    filled = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB").filter(
        ImageFilter.GaussianBlur(4)
    )
    base = Image.fromarray(rgb.astype(np.uint8), "RGB")
    base.paste(filled, (0, 0), Image.fromarray((mask * 255).astype(np.uint8), "L"))
    return np.array(base)


def rounded_icon(base: Image.Image, feather: Image.Image, size: int, circle: bool) -> Image.Image:
    """Rounded/circular app icon with the feather centred."""
    scale = 4
    canvas = base.resize((size * scale, size * scale), Image.LANCZOS)
    fw = int(canvas.width * 0.62)
    fh = int(fw * feather.height / feather.width)
    feather_scaled = feather.resize((fw, fh), Image.LANCZOS)
    canvas.paste(
        feather_scaled,
        ((canvas.width - fw) // 2, (canvas.height - fh) // 2 - int(canvas.height * 0.015)),
        feather_scaled,
    )
    mask = Image.new("L", canvas.size, 0)
    draw = ImageDraw.Draw(mask)
    if circle:
        draw.ellipse((0, 0, canvas.width - 1, canvas.height - 1), fill=255)
    else:
        radius = int(canvas.width * 0.225)
        draw.rounded_rectangle((0, 0, canvas.width - 1, canvas.height - 1), radius=radius, fill=255)
    canvas.putalpha(mask)
    return canvas.resize((size, size), Image.LANCZOS)


def adaptive_layer(base: Image.Image, feather: Image.Image, size: int, feather_ratio: float, opaque: bool) -> Image.Image:
    """Layer artwork on the 108dp adaptive canvas; safe content stays centred."""
    canvas = base.resize((size, size), Image.LANCZOS)
    fw = int(size * feather_ratio)
    fh = int(fw * feather.height / feather.width)
    feather_scaled = feather.resize((fw, fh), Image.LANCZOS)
    canvas.paste(
        feather_scaled,
        ((size - fw) // 2, (size - fh) // 2),
        feather_scaled,
    )
    if not opaque:
        canvas = canvas.convert("RGBA")
        # Encode the feather only; the gradient background is a separate layer.
        alpha = np.array(feather_scaled.getchannel("A").resize((size, size), Image.LANCZOS))
        layer = Image.new("RGBA", (size, size), (255, 255, 255, 0))
        placed = Image.new("L", (size, size), 0)
        placed.paste(feather_scaled.getchannel("A"), ((size - fw) // 2, (size - fh) // 2))
        white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        layer = Image.composite(white, layer, placed)
        return layer
    return canvas.convert("RGBA")


def make_monochrome(feather: Image.Image, size: int, feather_ratio: float) -> Image.Image:
    fw = int(size * feather_ratio)
    fh = int(fw * feather.height / feather.width)
    placed = Image.new("L", (size, size), 0)
    scaled = feather.resize((fw, fh), Image.LANCZOS)
    placed.paste(scaled.getchannel("A"), ((size - fw) // 2, (size - fh) // 2))
    layer = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    return Image.composite(white, layer, placed)


def load_font(size: int) -> ImageFont.FreeTypeFont:
    candidates = [
        Path(r"C:\Windows\Fonts\seguisb.ttf"),
        Path(r"C:\Windows\Fonts\segoeuib.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def make_banner(base: Image.Image, feather: Image.Image) -> Image.Image:
    width, height = 640, 360
    background = base.resize((width, height), Image.LANCZOS).convert("RGB")
    # Darken the artwork into a ten-foot banner background.
    dark = Image.new("RGB", (width, height), (7, 8, 16))
    banner = Image.blend(background, dark, 0.78)
    # Soft violet glow behind the mark.
    glow = Image.new("L", (width, height), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-120, -80, 460, 460), fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(70))
    violet = Image.new("RGB", (width, height), (118, 44, 232))
    banner = Image.composite(violet, banner, glow.point(lambda p: int(p * 0.85)))

    mark_size = 190
    mark = rounded_icon(base, feather, mark_size, circle=False)
    banner.paste(mark, (46, (height - mark_size) // 2), mark)

    draw = ImageDraw.Draw(banner)
    title_font = load_font(70)
    tag_font = load_font(21)
    title = "TETHER"
    x = 278
    y = 118
    tracking = 7
    for character in title:
        draw.text((x, y), character, font=title_font, fill=(246, 244, 255))
        x += draw.textlength(character, font=title_font) + tracking
    draw.rounded_rectangle((282, 216, 486, 219), radius=2, fill=(150, 105, 255))
    draw.text((282, 238), "MUSIC, YOUR WAY", font=tag_font, fill=(186, 173, 235))
    return banner


def main() -> None:
    source = Image.open(LOGO).convert("RGB")
    rgb = np.array(source)
    alpha = feather_alpha(rgb)
    feather_mask = Image.fromarray((alpha * 255).astype(np.uint8), "L")
    white = Image.new("RGBA", source.size, (255, 255, 255, 255))
    feather = Image.new("RGBA", source.size, (255, 255, 255, 0))
    feather = Image.composite(white, feather, feather_mask)
    background = Image.fromarray(remove_feather(rgb, alpha), "RGB")

    # In-app brand mark (drawable, not launcher-masked).
    design_logo = rounded_icon(background, feather, 512, circle=False)
    design_dir = DESIGN_RES / "drawable-nodpi"
    design_dir.mkdir(parents=True, exist_ok=True)
    design_logo.save(design_dir / "tether_logo.png", optimize=True)

    for res in (MOBILE_RES, TV_RES):
        for density, size in DENSITIES.items():
            folder = res / f"mipmap-{density}"
            folder.mkdir(parents=True, exist_ok=True)
            rounded_icon(background, feather, size, circle=False).save(folder / "ic_launcher.png", optimize=True)
            rounded_icon(background, feather, size, circle=True).save(folder / "ic_launcher_round.png", optimize=True)

        adaptive_dir = res / "mipmap-xxxhdpi"
        adaptive_dir.mkdir(parents=True, exist_ok=True)
        # Background layer must be full bleed; foreground is safe-zone artwork.
        background.resize((432, 432), Image.LANCZOS).save(adaptive_dir / "ic_launcher_background.png", optimize=True)
        adaptive_layer(background, feather, 432, 0.40, opaque=False).save(
            adaptive_dir / "ic_launcher_foreground.png", optimize=True
        )
        make_monochrome(feather, 432, 0.40).save(adaptive_dir / "ic_launcher_monochrome.png", optimize=True)

        # Remove the old vector placeholder so `@mipmap/ic_launcher_foreground` resolves to the PNG.
        old_vector = res / "drawable" / "ic_launcher_foreground.xml"
        if old_vector.exists():
            old_vector.unlink()

    banner = make_banner(background, feather)
    banner_dir = TV_RES / "drawable-xhdpi"
    banner_dir.mkdir(parents=True, exist_ok=True)
    banner.save(banner_dir / "tv_banner.png", optimize=True)
    old_banner = TV_RES / "drawable" / "tv_banner.xml"
    if old_banner.exists():
        old_banner.unlink()

    print("Generated launcher icons, adaptive layers, in-app logo and TV banner.")


if __name__ == "__main__":
    main()
