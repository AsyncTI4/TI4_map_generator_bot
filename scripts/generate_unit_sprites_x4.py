#!/usr/bin/env python3
"""
Writes a 4x nearest-neighbour copy of every coloured unit sprite for the website.

Input:  src/main/resources/units/sheets/<color>.webp (from generate-unit-sprites.mjs)
Output: src/main/resources/units/x4/<color>_<unit>.webp (lossless)

The upload workflow publishes these as https://images.asyncti4.com/units/x4/<color>_<unit>.webp.
The website shows units far above their native size in a few places (the music
video); a pre-scaled pixel copy keeps them crisp instead of blurring the 50px sprite.

Frames match UNIT_SPRITE_SHEETS.colored in ti4_web_new's renderedSpriteDimensions.ts,
so every colour's sprite for a unit has the same footprint. Empty frames (a colour
without that unit) are skipped. Re-run after regenerating the sheets.
"""

import argparse
from pathlib import Path

from PIL import Image

SCALE = 4
# [x, y, width, height] in the colored sheet
FRAMES = {
    "fs": (0, 0, 80, 82),
    "dn": (82, 0, 77, 77),
    "cv": (161, 0, 71, 70),
    "ca": (234, 0, 67, 67),
    "ws": (303, 0, 56, 65),
    "monument": (361, 0, 64, 64),
    "dd": (427, 0, 51, 50),
    "dd_eyes": (480, 0, 51, 50),
    "mf": (533, 0, 45, 45),
    "gf": (580, 0, 39, 44),
    "sd": (621, 0, 42, 43),
    "pd": (665, 0, 33, 38),
    "tkn_ff": (700, 0, 70, 35),
    "tkn_gf": (772, 0, 70, 35),
    "tkn_inf": (844, 0, 70, 35),
    "ff": (916, 0, 32, 32),
}
SHEET_SIZE = (948, 82)

RESOURCES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sheets", type=Path, default=RESOURCES / "units" / "sheets")
    parser.add_argument("--out", type=Path, default=RESOURCES / "units" / "x4")
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    expected = set()
    written = 0
    for sheet_path in sorted(args.sheets.glob("*.webp")):
        color = sheet_path.stem
        if color == "shared":
            continue
        with Image.open(sheet_path) as sheet:
            sheet = sheet.convert("RGBA")
            if sheet.size != SHEET_SIZE:
                raise SystemExit(f"{sheet_path.name}: expected {SHEET_SIZE}, got {sheet.size}; update FRAMES")
            for unit, (x, y, w, h) in FRAMES.items():
                frame = sheet.crop((x, y, x + w, y + h))
                if frame.getbbox() is None:
                    continue
                name = f"{color}_{unit}.webp"
                expected.add(name)
                frame.resize((w * SCALE, h * SCALE), Image.NEAREST).save(
                    args.out / name, "WEBP", lossless=True, quality=100, method=4
                )
                written += 1

    stale = [p for p in args.out.glob("*.webp") if p.name not in expected]
    for path in stale:
        path.unlink()
    print(f"wrote {written} sprites to {args.out} (removed {len(stale)} stale)")


if __name__ == "__main__":
    main()
