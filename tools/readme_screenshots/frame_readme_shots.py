#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


@dataclass(frozen=True)
class ScreenRect:
    x: int
    y: int
    width: int
    height: int


def apply_rounded_corners(image: Image.Image, corner_radius: int) -> Image.Image:
    if corner_radius <= 0:
        return image
    max_radius = min(image.width, image.height) // 2
    radius = min(corner_radius, max_radius)
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(
        [(0, 0), (image.width - 1, image.height - 1)],
        radius=radius,
        fill=255,
    )
    rounded = image.copy()
    rounded.putalpha(mask)
    return rounded


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compose README screenshots into phone frame")
    parser.add_argument(
        "--config",
        default="tools/readme_screenshots/frame_config.json",
        help="Path to frame composition config JSON",
    )
    return parser.parse_args()


def read_config(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def resolve_path(repo_root: Path, value: str) -> Path:
    candidate = Path(value)
    if candidate.is_absolute():
        return candidate
    return repo_root / candidate


def build_screen_rect(data: dict) -> ScreenRect:
    return ScreenRect(
        x=int(data["x"]),
        y=int(data["y"]),
        width=int(data["width"]),
        height=int(data["height"]),
    )


def ensure_bounds(frame_size: tuple[int, int], rect: ScreenRect) -> None:
    frame_width, frame_height = frame_size
    if rect.x < 0 or rect.y < 0:
        raise ValueError("Screen rectangle cannot have negative origin")
    if rect.width <= 0 or rect.height <= 0:
        raise ValueError("Screen rectangle dimensions must be positive")
    if rect.x + rect.width > frame_width or rect.y + rect.height > frame_height:
        raise ValueError("Screen rectangle must fit inside frame dimensions")


def compose_one(
    frame_path: Path,
    rect: ScreenRect,
    corner_radius: int,
    raw_path: Path,
    framed_path: Path,
) -> None:
    if not raw_path.exists():
        raise FileNotFoundError(f"Missing raw screenshot: {raw_path}")

    frame_image = Image.open(frame_path).convert("RGBA")
    ensure_bounds(frame_image.size, rect)
    raw_image = Image.open(raw_path).convert("RGBA")
    fitted = ImageOps.fit(
        raw_image,
        (rect.width, rect.height),
        method=Image.Resampling.LANCZOS,
        centering=(0.5, 0.5),
    )
    fitted = apply_rounded_corners(fitted, corner_radius)

    screenshot_layer = Image.new("RGBA", frame_image.size, (0, 0, 0, 0))
    screenshot_layer.paste(fitted, (rect.x, rect.y))
    composed = Image.alpha_composite(screenshot_layer, frame_image)
    framed_path.parent.mkdir(parents=True, exist_ok=True)
    composed.save(framed_path, format="PNG")


def main() -> None:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    config_path = resolve_path(repo_root, args.config)
    config = read_config(config_path)

    frame_path = resolve_path(repo_root, config["frame"])
    if not frame_path.exists():
        raise FileNotFoundError(f"Missing frame image: {frame_path}")

    rect = build_screen_rect(config["screen"])
    corner_radius = int(config.get("corner_radius", 0))
    outputs: list[tuple[Path, Path]] = []
    for item in config["shots"]:
        raw = resolve_path(repo_root, item["raw"])
        framed = resolve_path(repo_root, item["framed"])
        outputs.append((raw, framed))

    for raw_path, framed_path in outputs:
        compose_one(frame_path, rect, corner_radius, raw_path, framed_path)
        print(f"Wrote {framed_path.relative_to(repo_root)}")


if __name__ == "__main__":
    main()
