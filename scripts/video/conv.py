#!/usr/bin/env python3

import argparse
from pathlib import Path


def sanitize_png_filenames(directory: Path) -> int:
    renamed_count = 0
    for path in sorted(directory.glob("*.png")):
        sanitized_name = path.name.replace(":", "")
        if sanitized_name == path.name:
            continue

        target = path.with_name(sanitized_name)
        if target.exists() and target != path:
            raise FileExistsError(
                f"Cannot rename '{path.name}' to '{sanitized_name}': target already exists"
            )

        path.rename(target)
        renamed_count += 1

    return renamed_count


def main() -> int:
    parser = argparse.ArgumentParser(description="Sanitize PNG filenames for ffmpeg processing")
    parser.add_argument(
        "directory",
        nargs="?",
        default=".",
        help="Directory containing PNG frames (default: current directory)",
    )
    args = parser.parse_args()

    target_dir = Path(args.directory).resolve()
    if not target_dir.exists() or not target_dir.is_dir():
        raise NotADirectoryError(f"Directory does not exist: {target_dir}")

    renamed_count = sanitize_png_filenames(target_dir)
    print(f"Renamed {renamed_count} PNG file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())