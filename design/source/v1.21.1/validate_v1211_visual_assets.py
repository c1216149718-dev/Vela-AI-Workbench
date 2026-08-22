from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
SOURCE = Path(__file__).resolve().parent
REFERENCE = SOURCE / "approved-reference"
RES_LIGHT = ROOT / "app/src/main/res/drawable-nodpi"
RES_DARK = ROOT / "app/src/main/res/drawable-night-nodpi"
OUTPUT = ROOT / "design/validation/v1.21.1/asset-validation.json"


def local_mean(values: np.ndarray, radius: int = 5) -> np.ndarray:
    window = radius * 2 + 1
    padded = np.pad(values, radius, mode="reflect")
    integral = np.pad(padded, ((1, 0), (1, 0)), mode="constant").cumsum(0).cumsum(1)
    total = (
        integral[window:, window:]
        - integral[:-window, window:]
        - integral[window:, :-window]
        + integral[:-window, :-window]
    )
    return total / float(window * window)


def ssim(reference: Image.Image, candidate: Image.Image) -> float:
    left = np.asarray(reference.convert("RGB"), dtype=np.float64)
    right = np.asarray(candidate.convert("RGB"), dtype=np.float64)
    if left.shape != right.shape:
        raise ValueError(f"shape mismatch: {left.shape} != {right.shape}")
    values: list[float] = []
    c1 = (0.01 * 255.0) ** 2
    c2 = (0.03 * 255.0) ** 2
    for channel in range(3):
        x = left[:, :, channel]
        y = right[:, :, channel]
        mu_x = local_mean(x)
        mu_y = local_mean(y)
        sigma_x = local_mean(x * x) - mu_x * mu_x
        sigma_y = local_mean(y * y) - mu_y * mu_y
        sigma_xy = local_mean(x * y) - mu_x * mu_y
        numerator = (2 * mu_x * mu_y + c1) * (2 * sigma_xy + c2)
        denominator = (mu_x * mu_x + mu_y * mu_y + c1) * (sigma_x + sigma_y + c2)
        values.append(float(np.mean(numerator / np.maximum(denominator, 1e-12))))
    return float(np.mean(values))


def rebuild_entry(resource_dir: Path) -> Image.Image:
    parts = [
        Image.open(resource_dir / "vela_entry_top.webp").convert("RGB"),
        Image.open(resource_dir / "vela_entry_band.webp").convert("RGB"),
        Image.open(resource_dir / "vela_entry_footer.webp").convert("RGB"),
    ]
    result = Image.new("RGB", (1080, sum(part.height for part in parts)))
    offset = 0
    for part in parts:
        result.paste(part, (0, offset))
        offset += part.height
    return result


def dimensions(resource_dir: Path, names: list[str]) -> dict[str, list[int]]:
    return {
        name: list(Image.open(resource_dir / name).size)
        for name in names
    }


def main() -> None:
    light = rebuild_entry(RES_LIGHT)
    dark = rebuild_entry(RES_DARK)
    light_reference = Image.open(REFERENCE / "Vela_Splash_Light_20x9.png").convert("RGB")
    dark_reference = Image.open(REFERENCE / "Vela_Splash_Dark_20x9.png").convert("RGB")

    page_titles = [
        "vela_title_today.webp",
        "vela_title_tasks.webp",
        "vela_title_insights.webp",
        "vela_title_settings.webp",
        "vela_title_tools.webp",
    ]
    section_titles = [
        "vela_title_next.webp",
        "vela_title_daily_reflection.webp",
        "vela_title_data_sources.webp",
        "vela_title_widget.webp",
        "vela_title_usage_detail.webp",
        "vela_title_source_center.webp",
        "vela_title_focus.webp",
        "vela_title_focus_history.webp",
        "vela_title_daily_wall.webp",
        "vela_title_key_management.webp",
    ]
    dark_title = np.asarray(Image.open(RES_DARK / "vela_title_today.webp").convert("RGBA"))
    blue = np.array([0x7F, 0xB3, 0xE5], dtype=np.int16)
    rgb = dark_title[:, :, :3].astype(np.int16)
    opaque = dark_title[:, :, 3] > 0
    blue_pixels = int(np.sum(opaque & (np.max(np.abs(rgb - blue), axis=2) <= 8)))

    report = {
        "entry": {
            "light_ssim": round(ssim(light_reference, light), 6),
            "dark_ssim": round(ssim(dark_reference, dark), 6),
            "light_size": list(light.size),
            "dark_size": list(dark.size),
            "split_heights": [1550, 290, 560],
        },
        "titles": {
            "page_light": dimensions(RES_LIGHT, page_titles),
            "page_dark": dimensions(RES_DARK, page_titles),
            "section_light": dimensions(RES_LIGHT, section_titles),
            "section_dark": dimensions(RES_DARK, section_titles),
            "dark_editorial_blue_pixels": blue_pixels,
        },
        "checks": {
            "entry_ssim_at_least_0_95": ssim(light_reference, light) >= 0.95
            and ssim(dark_reference, dark) >= 0.95,
            "page_titles_are_1200x240": all(
                size == [1200, 240]
                for size in dimensions(RES_LIGHT, page_titles).values()
            ) and all(
                size == [1200, 240]
                for size in dimensions(RES_DARK, page_titles).values()
            ),
            "section_titles_are_1200x160": all(
                size == [1200, 160]
                for size in dimensions(RES_LIGHT, section_titles).values()
            ) and all(
                size == [1200, 160]
                for size in dimensions(RES_DARK, section_titles).values()
            ),
            "dark_editorial_blue_present": blue_pixels > 100,
        },
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(OUTPUT)
    print(json.dumps(report["checks"], ensure_ascii=False))


if __name__ == "__main__":
    main()
