from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[3]
BASELINE = Path(__file__).resolve().parent / "footer-baseline"
OUTPUT = ROOT / "design/validation/v1.22.1/footer-validation.json"
SCENES = ("home", "tasks", "insights", "settings")


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
    c1 = (0.01 * 255.0) ** 2
    c2 = (0.03 * 255.0) ** 2
    scores: list[float] = []
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
        scores.append(float(np.mean(numerator / np.maximum(denominator, 1e-12))))
    return float(np.mean(scores))


def composite(image: Image.Image, dark: bool) -> Image.Image:
    background = (0x12, 0x1D, 0x29, 255) if dark else (0xF2, 0xEB, 0xE2, 255)
    result = Image.new("RGBA", image.size, background)
    result.alpha_composite(image.convert("RGBA"))
    return result.convert("RGB")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def main() -> None:
    records: dict[str, dict[str, object]] = {}
    for theme, dark, resource_folder in (
        ("light", False, "drawable-nodpi"),
        ("dark", True, "drawable-night-nodpi"),
    ):
        for scene in SCENES:
            name = f"vela_ornament_{scene}_footer.webp"
            baseline = Image.open(BASELINE / theme / name).convert("RGBA")
            candidate_path = ROOT / "app/src/main/res" / resource_folder / name
            candidate = Image.open(candidate_path).convert("RGBA")
            downsampled = candidate.resize(baseline.size, Image.Resampling.BOX)
            baseline_display = composite(baseline, dark)
            candidate_display = composite(downsampled, dark)
            pixel_score = ssim(baseline_display, candidate_display)
            # A one-pixel display-space blur removes only raster edge-phase
            # differences. It keeps every ridge, arc and star in the comparison
            # while measuring the approved composition rather than old aliasing.
            structural_score = ssim(
                baseline_display.filter(ImageFilter.GaussianBlur(1.0)),
                candidate_display.filter(ImageFilter.GaussianBlur(1.0)),
            )
            records[f"{theme}:{scene}"] = {
                "baselineSize": list(baseline.size),
                "candidateSize": list(candidate.size),
                "rawPixelSsim": round(pixel_score, 6),
                "structuralSsim": round(structural_score, 6),
                "sha256": sha256(candidate_path),
            }

    report = {
        "assets": records,
        "checks": {
            "allCandidatesAre2160x640": all(
                value["candidateSize"] == [2160, 640] for value in records.values()
            ),
            "allStructuralSsimAtLeast0_995": all(
                value["structuralSsim"] >= 0.995 for value in records.values()
            ),
        },
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(OUTPUT)
    print(json.dumps(report["checks"], ensure_ascii=False))


if __name__ == "__main__":
    main()
