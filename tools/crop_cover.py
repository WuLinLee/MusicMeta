#!/usr/bin/env python3
"""Trim the uploaded app cover's near-white outer border before packaging."""
from pathlib import Path
from PIL import Image

COVER = Path("app/src/main/res/drawable/musicmeta_cover.webp")


def is_background(pixel):
    r, g, b, a = pixel
    if a <= 12:
        return True
    # Treat connected near-white paper margins as whitespace, but preserve colored art.
    return min(r, g, b) >= 244 and max(r, g, b) - min(r, g, b) <= 18


def main():
    if not COVER.is_file():
        raise SystemExit(f"App cover was not found: {COVER}")

    with Image.open(COVER) as source:
        image = source.convert("RGBA")

    pixels = image.load()
    width, height = image.size
    xs, ys = [], []
    for y in range(height):
        for x in range(width):
            if not is_background(pixels[x, y]):
                xs.append(x)
                ys.append(y)

    if not xs or not ys:
        raise SystemExit("The app cover appears to be blank; refusing to crop it.")

    left, right = min(xs), max(xs) + 1
    top, bottom = min(ys), max(ys) + 1

    # Keep a small safety margin so the artwork never touches the edge.
    margin = max(2, int(max(right - left, bottom - top) * 0.018))
    left = max(0, left - margin)
    top = max(0, top - margin)
    right = min(width, right + margin)
    bottom = min(height, bottom + margin)

    cropped = image.crop((left, top, right, bottom))
    cropped.save(COVER, format="WEBP", lossless=True, method=6)
    print(f"Cropped {width}x{height} to {cropped.width}x{cropped.height}: {COVER}")


if __name__ == "__main__":
    main()
