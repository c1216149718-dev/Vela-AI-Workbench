from __future__ import annotations

import argparse
import json
import math
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageStat


PAGE_TITLES = {
    "today": ("TODAY", "今天", "Today"),
    "tasks": ("TASKS", "任务", "Tasks"),
    "insights": ("INSIGHTS", "洞察", "Insights"),
    "settings": ("SETTINGS", "设置", "Settings"),
    "tools": ("VELA TOOLS", "工具", "Tools"),
}

SECTION_TITLES = {
    "next": ("下一步", "Next"),
    "daily_reflection": ("今日复盘", "Daily Reflection"),
    "data_sources": ("数据源", "Data Sources"),
    "widget": ("小组件", "Widget"),
    "source_center": ("数据源中心", "Source Center"),
    "connections_credentials": ("连接与凭据", "Connections & Credentials"),
    "focus": ("专注", "Focus"),
    "focus_history": ("专注历史", "Focus History"),
    "daily_wall": ("每日留言墙", "Daily Wall"),
    "usage_detail": ("用量详情", "Usage Detail"),
    "key_management": ("密钥管理", "Key Management"),
}

LIGHT_BG = (242, 235, 226)
DARK_BG = (18, 29, 41)
LIGHT_INK = (51, 44, 37)
DARK_INK = (242, 237, 229)
LIGHT_ACCENT = (155, 103, 45)
DARK_ACCENT = (127, 179, 229)


def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), size)


def star(draw: ImageDraw.ImageDraw, x: float, y: float, radius: float, fill: tuple[int, ...]) -> None:
    points: list[tuple[float, float]] = []
    for index in range(8):
        angle = math.pi * index / 4 - math.pi / 2
        scale = radius if index % 2 == 0 else radius * 0.17
        points.append((x + math.cos(angle) * scale, y + math.sin(angle) * scale))
    draw.polygon(points, fill=fill)


def fit_script_font(
    draw: ImageDraw.ImageDraw,
    text: str,
    font_path: Path,
    max_size: int,
    max_width: int,
) -> ImageFont.FreeTypeFont:
    size = max_size
    while size >= 42:
        candidate = font(font_path, size)
        box = draw.textbbox((0, 0), text, font=candidate)
        if box[2] - box[0] <= max_width:
            return candidate
        size -= 2
    return font(font_path, 42)


def page_title_asset(
    eyebrow: str,
    chinese: str,
    script: str,
    chinese_font: Path,
    script_font: Path,
    serif_font: Path,
    dark: bool,
) -> Image.Image:
    image = Image.new("RGBA", (1200, 240), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    ink = DARK_INK + (255,) if dark else LIGHT_INK + (255,)
    accent = DARK_ACCENT + (255,) if dark else LIGHT_ACCENT + (255,)
    quiet = DARK_ACCENT + (185,) if dark else LIGHT_ACCENT + (185,)
    eyebrow_font = font(serif_font, 40)
    chinese_face = font(chinese_font, 116)
    script_face = fit_script_font(draw, script, script_font, 94, 450)
    draw.text((12, 3), eyebrow, font=eyebrow_font, fill=quiet)
    draw.text((10, 75), chinese, font=chinese_face, fill=ink)
    cn_box = draw.textbbox((10, 75), chinese, font=chinese_face)
    script_x = cn_box[2] + 30
    draw.text((script_x, 95), script, font=script_face, fill=accent)
    star(draw, max(script_x - 14, 8), 164, 6, accent)
    return image


def section_title_asset(
    chinese: str,
    script: str,
    chinese_font: Path,
    script_font: Path,
    dark: bool,
) -> Image.Image:
    image = Image.new("RGBA", (1200, 160), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    ink = DARK_INK + (255,) if dark else LIGHT_INK + (255,)
    accent = DARK_ACCENT + (255,) if dark else LIGHT_ACCENT + (255,)
    chinese_face = font(chinese_font, 88)
    script_face = fit_script_font(draw, script, script_font, 74, 560)
    draw.text((10, 25), chinese, font=chinese_face, fill=ink)
    cn_box = draw.textbbox((10, 25), chinese, font=chinese_face)
    script_x = cn_box[2] + 28
    draw.text((script_x, 35), script, font=script_face, fill=accent)
    star(draw, max(script_x - 13, 8), 99, 5, accent)
    return image


def save_lossless_webp(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", lossless=True, method=6)


def split_entry(source: Path, target_dir: Path, reference_dir: Path, theme: str) -> None:
    image = Image.open(source).convert("RGB")
    if image.size != (1080, 2400):
        raise ValueError(f"{source} must be 1080x2400, got {image.size}")
    # The authored subject and the Loading/footer remain untouched. Only the quiet
    # material between the lower wave and the Loading block may change height.
    segments = {
        "top": image.crop((0, 0, 1080, 1550)),
        "band": image.crop((0, 1550, 1080, 1840)),
        "footer": image.crop((0, 1840, 1080, 2400)),
    }
    for name, segment in segments.items():
        save_lossless_webp(segment, target_dir / f"vela_entry_{name}.webp")
        segment.save(reference_dir / f"vela-entry-{theme}-{name}.png")
    shutil.copy2(source, reference_dir / f"Vela_Splash_{theme.title()}_20x9.png")


def color_distance(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return math.sqrt(sum((int(a[i]) - int(b[i])) ** 2 for i in range(3)))


def extract_ornament(
    source: Image.Image,
    crop: tuple[int, int, int, int],
    target_size: tuple[int, int],
    dark: bool,
    placement: str = "center",
) -> Image.Image:
    piece = source.crop(crop).convert("RGB")
    pixels = list(piece.getdata())
    median = ImageStat.Stat(piece).median
    base_luminance = sum(median) / 3
    rgba = Image.new("RGBA", piece.size, (0, 0, 0, 0))
    out = []
    tint = (176, 139, 90) if dark else (155, 103, 45)
    for pixel in pixels:
        luminance = sum(pixel) / 3
        if dark:
            # Dark references use warm lines and stars over a near-uniform navy
            # field. Luminance isolation avoids baking that field into the asset.
            alpha = int((luminance - base_luminance - 9) * 4.8)
        else:
            # Light references use brass engravings darker than the porcelain.
            alpha = int((base_luminance - luminance - 7) * 4.6)
        alpha = max(0, min(190, alpha))
        out.append(tint + (alpha,))
    rgba.putdata(out)
    rgba = rgba.filter(ImageFilter.GaussianBlur(0.25))
    canvas = Image.new("RGBA", target_size, (0, 0, 0, 0))
    scale = min(target_size[0] / rgba.width, target_size[1] / rgba.height)
    scaled = rgba.resize((max(1, int(rgba.width * scale)), max(1, int(rgba.height * scale))), Image.Resampling.LANCZOS)
    if placement == "right":
        x = target_size[0] - scaled.width
    elif placement == "left":
        x = 0
    else:
        x = (target_size[0] - scaled.width) // 2
    y = (target_size[1] - scaled.height) // 2
    canvas.alpha_composite(scaled, (x, y))
    return canvas


def compose_ornament(
    source: Image.Image,
    crop: tuple[int, int, int, int],
    target_dir: Path,
    name: str,
    dark: bool,
    target_size: tuple[int, int],
    placement: str,
) -> None:
    save_lossless_webp(
        extract_ornament(source, crop, target_size, dark=dark, placement=placement),
        target_dir / name,
    )


def generated_divider(scene: str, dark: bool) -> Image.Image:
    image = Image.new("RGBA", (900, 150), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    accent = (176, 139, 90, 105) if dark else (155, 103, 45, 85)
    quiet = accent[:3] + (42,)
    y = 78
    draw.line((70, y, 360, y), fill=quiet, width=2)
    draw.line((540, y, 830, y), fill=quiet, width=2)
    star(draw, 450, y, 12, accent)
    if scene in {"tasks", "insights"}:
        draw.arc((344, 28, 556, 126), 200, 340, fill=accent, width=2)
        draw.ellipse((598, 53, 606, 61), fill=accent)
    elif scene == "home":
        draw.arc((352, 43, 548, 123), 190, 350, fill=accent, width=2)
    else:
        for offset in (-24, 24):
            star(draw, 450 + offset, y + offset // 2, 4, quiet)
    return image


def generated_footer(scene: str, dark: bool) -> Image.Image:
    # Footer ornaments are rendered natively at 2x. Coordinates, stroke widths,
    # star sizes and alpha values scale together so Android can downsample the
    # same approved composition instead of enlarging a thin 1080px raster.
    scale = 2
    image = Image.new("RGBA", (1080 * scale, 320 * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    accent = (176, 139, 90, 92) if dark else (155, 103, 45, 72)
    quiet = accent[:3] + (34,)
    if scene in {"home", "tasks", "settings"}:
        ridges = [
            [(0, 278), (145, 208), (258, 244), (408, 142), (530, 230), (686, 170), (818, 235), (1080, 178)],
            [(0, 300), (190, 250), (350, 272), (515, 212), (675, 270), (830, 222), (1080, 260)],
        ]
        for index, ridge in enumerate(ridges):
            draw.line([(x * scale, y * scale) for x, y in ridge], fill=accent if index == 0 else quiet, width=2 * scale, joint="curve")
        draw.arc(tuple(value * scale for value in (370, 80, 710, 360)), 190, 350, fill=quiet, width=2 * scale)
    else:
        points = [(25, 230), (165, 218), (270, 248), (390, 202), (520, 238), (650, 190), (790, 226), (1045, 194)]
        draw.line([(x * scale, y * scale) for x, y in points], fill=accent, width=2 * scale, joint="curve")
        for x, y in points[1:-1:2]:
            star(draw, x * scale, y * scale, 7 * scale, accent)
        draw.arc(tuple(value * scale for value in (360, 94, 720, 298)), 204, 132, fill=quiet, width=2 * scale)
    return image


def drawer_background(source: Image.Image, dark: bool) -> Image.Image:
    canvas = Image.new("RGBA", (768, 2048), DARK_BG + (255,) if dark else LIGHT_BG + (255,))
    draw = ImageDraw.Draw(canvas)
    line = (176, 139, 90, 112) if dark else (155, 103, 45, 96)
    draw.rounded_rectangle((18, 18, 750, 2030), radius=52, outline=line, width=2)
    draw.rounded_rectangle((28, 28, 740, 2020), radius=44, outline=line[:3] + (54,), width=1)
    if dark:
        top_crop = (930, 20, 1040, 290)
        footer_crop = (776, 700, 1048, 1010)
    else:
        top_crop = (170, 20, 278, 295)
        footer_crop = (8, 690, 282, 1010)
    top = extract_ornament(source, top_crop, (360, 650), dark=dark, placement="right")
    bottom = extract_ornament(source, footer_crop, (768, 720), dark=dark, placement="center")
    canvas.alpha_composite(top, (408, 20))
    canvas.alpha_composite(bottom, (0, 1328))
    return canvas


def generate_ornaments(reference_pages: Path, reference_home: Path, light_dir: Path, dark_dir: Path) -> None:
    pages = Image.open(reference_pages).convert("RGB")
    home = Image.open(reference_home).convert("RGB")
    # Coordinates deliberately avoid baked UI text and capture only the approved
    # celestial/botanical engravings from the user's reference boards.
    light_specs = {
        "home": (home, (500, 20, 745, 175), (570, 420, 745, 535), (500, 735, 745, 915)),
        "tasks": (pages, (970, 18, 1170, 105), (825, 535, 1170, 585), (825, 535, 1170, 585)),
        "insights": (pages, (550, 18, 720, 105), (430, 565, 720, 605), (430, 565, 720, 605)),
        "settings": (pages, (220, 18, 368, 105), (35, 235, 370, 270), (35, 235, 370, 270)),
    }
    dark_specs = {
        "home": (home, (1260, 20, 1515, 175), (1330, 420, 1515, 535), (1260, 735, 1515, 915)),
        "tasks": (pages, (970, 680, 1170, 755), (825, 1195, 1170, 1235), (825, 1195, 1170, 1235)),
        "insights": (pages, (550, 680, 720, 755), (430, 1215, 720, 1245), (430, 1215, 720, 1245)),
        "settings": (pages, (220, 680, 368, 755), (35, 900, 370, 935), (35, 900, 370, 935)),
    }
    for dark, specs, target in ((False, light_specs, light_dir), (True, dark_specs, dark_dir)):
        for scene, (source, header, divider, footer) in specs.items():
            compose_ornament(source, header, target, f"vela_ornament_{scene}_header.webp", dark, (900, 300), "right")
            save_lossless_webp(generated_divider(scene, dark), target / f"vela_ornament_{scene}_divider.webp")
            save_lossless_webp(generated_footer(scene, dark), target / f"vela_ornament_{scene}_footer.webp")
    for dark, target, crop in (
        (False, light_dir, (865, 235, 1120, 425)),
        (True, dark_dir, (865, 900, 1120, 1090)),
    ):
        save_lossless_webp(
            extract_ornament(pages, crop, (640, 420), dark=dark, placement="center"),
            target / "vela_empty_tasks.webp",
        )
    save_lossless_webp(drawer_background(home, False), light_dir / "vela_drawer_background.webp")
    save_lossless_webp(drawer_background(home, True), dark_dir / "vela_drawer_background.webp")


def title_overview(entries: list[Image.Image], dark: bool) -> Image.Image:
    background = DARK_BG if dark else LIGHT_BG
    width = 1280
    height = sum(image.height for image in entries) + 32 * (len(entries) + 1)
    sheet = Image.new("RGB", (width, height), background)
    y = 32
    for image in entries:
        sheet.paste(image, (40, y), image)
        y += image.height + 32
    return sheet


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--light-entry", required=True, type=Path)
    parser.add_argument("--dark-entry", required=True, type=Path)
    parser.add_argument("--page-reference", required=True, type=Path)
    parser.add_argument("--home-reference", required=True, type=Path)
    parser.add_argument("--app-res", required=True, type=Path)
    parser.add_argument("--design-out", required=True, type=Path)
    parser.add_argument("--chinese-font", type=Path, default=Path(r"C:\Windows\Fonts\NotoSerifSC-VF.ttf"))
    parser.add_argument("--script-font", type=Path, default=Path(r"C:\Windows\Fonts\Gabriola.ttf"))
    parser.add_argument("--serif-font", type=Path, default=Path(r"C:\Windows\Fonts\georgia.ttf"))
    args = parser.parse_args()

    light_dir = args.app_res / "drawable-nodpi"
    dark_dir = args.app_res / "drawable-night-nodpi"
    reference_dir = args.design_out / "approved-reference"
    reference_dir.mkdir(parents=True, exist_ok=True)
    light_dir.mkdir(parents=True, exist_ok=True)
    dark_dir.mkdir(parents=True, exist_ok=True)

    split_entry(args.light_entry, light_dir, reference_dir, "light")
    split_entry(args.dark_entry, dark_dir, reference_dir, "dark")
    shutil.copy2(args.page_reference, reference_dir / "Vela_Page_Theme_Reference.png")
    shutil.copy2(args.home_reference, reference_dir / "Vela_Home_Drawer_Reference.png")
    generate_ornaments(args.page_reference, args.home_reference, light_dir, dark_dir)

    sheets: dict[str, list[Image.Image]] = {"light": [], "dark": []}
    manifest: dict[str, dict[str, object]] = {}
    for dark, target, theme in ((False, light_dir, "light"), (True, dark_dir, "dark")):
        for name, (eyebrow, chinese, script) in PAGE_TITLES.items():
            image = page_title_asset(eyebrow, chinese, script, args.chinese_font, args.script_font, args.serif_font, dark)
            save_lossless_webp(image, target / f"vela_title_{name}.webp")
            sheets[theme].append(image)
            manifest[name] = {"role": "PAGE", "canvas": [1200, 240], "spokenLabel": chinese}
        for name, (chinese, script) in SECTION_TITLES.items():
            image = section_title_asset(chinese, script, args.chinese_font, args.script_font, dark)
            save_lossless_webp(image, target / f"vela_title_{name}.webp")
            sheets[theme].append(image)
            manifest[name] = {"role": "SECTION", "canvas": [1200, 160], "spokenLabel": chinese}
    title_overview(sheets["light"], False).save(args.design_out / "vela-title-lockups-light.png")
    title_overview(sheets["dark"], True).save(args.design_out / "vela-title-lockups-dark.png")
    (args.design_out / "asset-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
