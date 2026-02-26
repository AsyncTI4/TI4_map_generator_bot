#!/usr/bin/env python3
"""Process game map images for video rendering.

For each frame:
  1. Convert to PNG (handling webp/jpg/etc).
  2. Detect the y-coordinate where the first player-area starts.
     The player-area begins with a gradient rectangle border that spans
     nearly the full image width (x≈10 to x≈imageWidth-15).  The region
     just above it (objectives / laws section) contains no content in the
     far-right portion of the image, creating a detectable gap.
  3. Crop the image to just above that player-area border.
  4. Normalise all frames to the same dimensions (the largest crop height
     across all frames) by padding shorter images with black.

This replaces the old fixed-height mogrify crop that was cutting off
variable amounts of the objectives / laws section.
"""

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}

# Fraction of image width that defines the "far-right" region.
# The player-area gradient rectangle always reaches x ≈ imageWidth - 5,
# while objectives / laws elements never extend this far right.
_RIGHT_THRESHOLD = 0.97

# Minimum length (rows) for a "dark gap" to be considered the
# objectives/laws section rather than a tile-boundary artefact.
# Hex-tile boundaries create gaps of ~20-50 rows; the objectives
# section (score track alone is 160 rows) is always longer than this.
_MIN_GAP_ROWS = 100

# Minimum per-channel brightness (0-255) for a pixel to be considered
# "non-dark".  Pure-black background pixels are 0; a threshold of 30
# comfortably excludes near-black JPEG compression artefacts while still
# detecting the faint gradient borders used for player-area rectangles.
_BRIGHTNESS_THRESHOLD = 30


def find_player_area_top(arr: np.ndarray) -> int:
    """Return the y-coordinate where the first player area starts.

    Scans each row for non-dark pixels in the rightmost ``_RIGHT_THRESHOLD``
    fraction of the image.  The map-tile region and the player-area region
    both have such pixels; the objectives/laws section between them does not.
    The end of the longest contiguous run of rows *without* right-edge pixels
    (of length ≥ ``_MIN_GAP_ROWS``) marks the start of the first player area.

    Returns the full image height when no player area is detected (safe
    fallback that keeps the entire image).
    """
    height, width = arr.shape[:2]
    right_x = max(1, int(width * _RIGHT_THRESHOLD))

    # True where any pixel in the rightmost slice has brightness > 30.
    non_dark = np.max(arr, axis=2) > _BRIGHTNESS_THRESHOLD
    has_right = np.any(non_dark[:, right_x:], axis=1)  # shape: (height,)

    best_run_length = 0
    best_run_end = height  # fallback

    run_start: int | None = None
    for y in range(height):
        if not has_right[y]:
            if run_start is None:
                run_start = y
        else:
            if run_start is not None:
                run_length = y - run_start
                if run_length >= _MIN_GAP_ROWS and run_length > best_run_length:
                    best_run_length = run_length
                    best_run_end = y
                run_start = None

    # Handle a run that reaches the very bottom of the image.
    if run_start is not None:
        run_length = height - run_start
        if run_length >= _MIN_GAP_ROWS and run_length > best_run_length:
            best_run_end = height  # no player area below

    return best_run_end


def process_frames(frames_dir: Path) -> None:
    """Convert, detect, crop, and normalise all image frames in *frames_dir*."""
    image_files = sorted(
        p for p in frames_dir.iterdir()
        if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS
    )
    if not image_files:
        print("Error: no image files found in directory", file=sys.stderr)
        raise SystemExit(1)

    print(f"Processing {len(image_files)} frame(s)...", file=sys.stderr)

    # --- Pass 1: detect crop heights ---
    crop_info: list[tuple[Path, int, int, int]] = []  # (path, crop_y, h, w)

    for img_path in image_files:
        img = Image.open(img_path).convert("RGB")
        arr = np.array(img)
        h, w = arr.shape[:2]
        crop_y = find_player_area_top(arr)
        crop_info.append((img_path, crop_y, h, w))
        print(
            f"  {img_path.name}: player area at y={crop_y} (image {w}x{h})",
            file=sys.stderr,
        )
        del arr

    # Target dimensions: largest crop height and width across all frames.
    # Round up to the nearest even number (required by H.264).
    max_height = max(crop_y for _, crop_y, _, _ in crop_info)
    max_width = max(w for _, _, _, w in crop_info)
    max_height = (max_height + 1) // 2 * 2
    max_width = (max_width + 1) // 2 * 2

    print(
        f"Target frame size: {max_width}x{max_height} "
        f"(largest crop height across all frames)",
        file=sys.stderr,
    )

    # --- Pass 2: crop, pad, and save as PNG ---
    for img_path, crop_y, _h, _w in crop_info:
        img = Image.open(img_path).convert("RGB")
        arr = np.array(img)

        # Crop from the top down to just above the player area.
        cropped = arr[:crop_y, :]

        # Pad to target dimensions with black (bottom / right padding only).
        out = np.zeros((max_height, max_width, 3), dtype=np.uint8)
        out[: cropped.shape[0], : cropped.shape[1]] = cropped

        out_path = img_path.with_suffix(".png")
        Image.fromarray(out).save(out_path, "PNG")

        # Remove the original if it was in a different format.
        if img_path.suffix.lower() != ".png":
            img_path.unlink()

        del arr

    print(
        f"Done. {len(crop_info)} frame(s) saved at {max_width}x{max_height}.",
        file=sys.stderr,
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Crop game-map frames to just above the player area and "
            "normalise all frames to the same dimensions for video rendering."
        )
    )
    parser.add_argument(
        "directory",
        help="Directory containing image frames to process.",
    )
    args = parser.parse_args()

    frames_dir = Path(args.directory).resolve()
    if not frames_dir.is_dir():
        print(f"Error: not a directory: {frames_dir}", file=sys.stderr)
        return 1

    process_frames(frames_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
