from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


TITLES = {
    "today": ("今天", "Today"),
    "next": ("下一步", "Next"),
    "tasks": ("任务", "Tasks"),
    "insights": ("洞察", "Insights"),
    "settings": ("设置", "Settings"),
    "tools": ("工具", "Tools"),
    "source_center": ("数据源中心", "Source Center"),
    "focus": ("专注", "Focus"),
    "focus_history": ("专注历史", "Focus History"),
    "daily_wall": ("每日留言墙", "Daily Wall"),
    "daily_reflection": ("今日复盘", "Daily Reflection"),
    "usage_detail": ("用量详情", "Usage Detail"),
    "key_management": ("密钥管理", "Key Management"),
}


def star(draw: ImageDraw.ImageDraw, x: float, y: float, radius: float, color: tuple[int, ...]) -> None:
    points = []
    for i in range(8):
        angle = math.pi / 4 * i - math.pi / 2
        r = radius if i % 2 == 0 else radius * 0.18
        points.append((x + math.cos(angle) * r, y + math.sin(angle) * r))
    draw.polygon(points, fill=color)


def title_asset(
    chinese: str,
    english: str,
    chinese_font: Path,
    script_font: Path,
    dark: bool,
) -> Image.Image:
    canvas = Image.new("RGBA", (1320, 250), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    ink = (244, 239, 230, 255) if dark else (55, 46, 38, 255)
    accent = (186, 148, 91, 255) if dark else (184, 132, 61, 255)
    cn_size = 128 if len(chinese) <= 4 else 104
    en_size = 106 if len(english) <= 12 else 82
    cn = ImageFont.truetype(str(chinese_font), cn_size)
    en = ImageFont.truetype(str(script_font), en_size)
    cn_box = draw.textbbox((0, 0), chinese, font=cn)
    cn_w = cn_box[2] - cn_box[0]
    baseline_y = 62
    draw.text((14, baseline_y), chinese, font=cn, fill=ink, stroke_width=0)
    en_x = cn_w + 58
    en_y = baseline_y + 22
    draw.text((en_x, en_y), english, font=en, fill=accent)
    star(draw, en_x - 22, baseline_y + 78, 7, accent)
    bbox = canvas.getbbox()
    if bbox is None:
        return canvas
    crop = canvas.crop((max(0, bbox[0] - 14), max(0, bbox[1] - 12), min(canvas.width, bbox[2] + 18), min(canvas.height, bbox[3] + 14)))
    return crop


def save_webp(image: Image.Image, path: Path, quality: int = 94) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", quality=quality, method=6, lossless=False)


def prepare_entry(source: Path, dark: bool) -> Image.Image:
    image = Image.open(source).convert("RGB")
    if dark:
        # Remove the pale presentation mat around the approved dark artwork.
        image = image.crop((16, 22, image.width - 8, image.height - 18))
        backdrop = Image.new("RGB", image.size, (14, 25, 38))
        backdrop.paste(image)
        image = backdrop
    return image


def drawer_background(dark: bool, serif_font: Path) -> Image.Image:
    w, h = 768, 2048
    if dark:
        base = (14, 29, 43)
        line = (171, 132, 74, 145)
        faint = (160, 126, 78, 55)
        footer = (221, 198, 156, 230)
    else:
        base = (247, 239, 225)
        line = (191, 139, 69, 145)
        faint = (184, 131, 61, 58)
        footer = (151, 105, 55, 230)
    image = Image.new("RGBA", (w, h), base + (255,))
    draw = ImageDraw.Draw(image)

    # Thin editorial double frame.
    draw.rounded_rectangle((18, 18, w - 18, h - 18), radius=54, outline=line, width=2)
    draw.rounded_rectangle((29, 29, w - 29, h - 29), radius=46, outline=faint, width=2)

    # Upper botanical / celestial engraving. Keep the silhouette sparse so it
    # reads as editorial ornament instead of a second navigation hierarchy.
    stem = []
    for step in range(19):
        t = step / 18
        stem.append((w - 82 - 115 * t - 20 * math.sin(t * math.pi), 84 + 520 * t))
    draw.line(stem, fill=line, width=2, joint="curve")
    for index in range(3, 17, 3):
        x, y = stem[index]
        direction = -1 if index % 2 else 1
        length = 68 + index * 2
        end = (x + direction * length, y - 46)
        mid = ((x + end[0]) / 2, y - 34)
        draw.line([(x, y), mid, end], fill=faint, width=2, joint="curve")
        for leaf_index in (0.42, 0.72, 1.0):
            lx = x + (end[0] - x) * leaf_index
            ly = y + (end[1] - y) * leaf_index
            rx = 13 if leaf_index < 1 else 17
            ry = 7 if leaf_index < 1 else 9
            draw.ellipse((lx - rx, ly - ry, lx + rx, ly + ry), outline=line, width=2)
    for x, y, r in ((74, 220, 8), (648, 670, 10), (112, 910, 6), (678, 1040, 7), (92, 1310, 5)):
        star(draw, x, y, r, line)

    # Bottom mountain / horizon etching with a restrained sun or crescent.
    horizon = int(h * 0.72)
    orb = (w * 0.48, horizon + 35)
    if dark:
        draw.ellipse((orb[0] - 74, orb[1] - 74, orb[0] + 74, orb[1] + 74), outline=line, width=3)
        draw.ellipse((orb[0] - 26, orb[1] - 88, orb[0] + 88, orb[1] + 54), fill=base + (255,))
    else:
        draw.ellipse((orb[0] - 72, orb[1] - 72, orb[0] + 72, orb[1] + 72), outline=line, width=3)
        for ray in range(24):
            angle = math.pi * ray / 12
            draw.line(
                (
                    orb[0] + math.cos(angle) * 86,
                    orb[1] + math.sin(angle) * 86,
                    orb[0] + math.cos(angle) * 122,
                    orb[1] + math.sin(angle) * 122,
                ),
                fill=faint,
                width=2,
            )
    ridge = [(0, horizon + 240), (105, horizon + 138), (210, horizon + 210), (350, horizon + 62), (475, horizon + 188), (602, horizon + 108), (w, horizon + 232)]
    for band in range(6):
        shifted = [(x, y + band * 34) for x, y in ridge]
        draw.line(shifted, fill=(line[0], line[1], line[2], max(42, line[3] - band * 14)), width=2, joint="curve")
    for offset in (0, 28, 56):
        draw.arc((64 - offset, horizon + 310 - offset, w - 64 + offset, h - 205 + offset), 190, 350, fill=faint, width=2)

    phrase = "In Stillness, We See Further" if dark else "Shine with Clarity · Create with Purpose"
    font = ImageFont.truetype(str(serif_font), 28)
    box = draw.textbbox((0, 0), phrase, font=font)
    tw = box[2] - box[0]
    draw.text(((w - tw) / 2, h - 132), phrase, font=font, fill=footer)
    star(draw, w / 2, h - 78, 8, line)

    # Grain lives in a separate layer so line work remains crisp.
    grain = Image.effect_noise((w, h), 7).convert("L").filter(ImageFilter.GaussianBlur(0.25))
    grain_rgba = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    grain_rgba.putalpha(grain.point(lambda value: int(value * (0.035 if dark else 0.024))))
    image.alpha_composite(grain_rgba)
    return image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--light-entry", required=True, type=Path)
    parser.add_argument("--dark-entry", required=True, type=Path)
    parser.add_argument("--app-res", required=True, type=Path)
    parser.add_argument("--design-out", required=True, type=Path)
    parser.add_argument("--chinese-font", type=Path, default=Path(r"C:\Windows\Fonts\NotoSerifSC-VF.ttf"))
    parser.add_argument("--script-font", type=Path, default=Path(r"C:\Windows\Fonts\FRSCRIPT.TTF"))
    parser.add_argument("--serif-font", type=Path, default=Path(r"C:\Windows\Fonts\georgia.ttf"))
    args = parser.parse_args()

    light_dir = args.app_res / "drawable-nodpi"
    dark_dir = args.app_res / "drawable-night-nodpi"
    source_dir = args.design_out / "approved-reference"
    source_dir.mkdir(parents=True, exist_ok=True)

    light_entry = prepare_entry(args.light_entry, dark=False)
    dark_entry = prepare_entry(args.dark_entry, dark=True)
    save_webp(light_entry, light_dir / "vela_entry_master.webp", quality=96)
    save_webp(dark_entry, dark_dir / "vela_entry_master.webp", quality=96)
    light_entry.save(source_dir / "Vela_Loading_Light_Android.png")
    dark_entry.save(source_dir / "Vela_Loading_Dark_Android_cropped.png")

    save_webp(drawer_background(False, args.serif_font), light_dir / "vela_drawer_background.webp", quality=94)
    save_webp(drawer_background(True, args.serif_font), dark_dir / "vela_drawer_background.webp", quality=94)

    title_sheet_light = Image.new("RGBA", (1420, len(TITLES) * 230 + 80), (247, 239, 225, 255))
    title_sheet_dark = Image.new("RGBA", (1420, len(TITLES) * 230 + 80), (14, 29, 43, 255))
    for index, (name, (chinese, english)) in enumerate(TITLES.items()):
        light = title_asset(chinese, english, args.chinese_font, args.script_font, False)
        dark = title_asset(chinese, english, args.chinese_font, args.script_font, True)
        save_webp(light, light_dir / f"vela_title_{name}.webp", quality=96)
        save_webp(dark, dark_dir / f"vela_title_{name}.webp", quality=96)
        title_sheet_light.alpha_composite(light, (38, index * 230 + 36))
        title_sheet_dark.alpha_composite(dark, (38, index * 230 + 36))
    title_sheet_light.save(args.design_out / "vela-title-lockups-light.png")
    title_sheet_dark.save(args.design_out / "vela-title-lockups-dark.png")


if __name__ == "__main__":
    main()
