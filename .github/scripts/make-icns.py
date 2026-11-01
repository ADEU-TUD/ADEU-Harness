#!/usr/bin/env python3
"""Writes the macOS app icon (desktop/icons/app.icns) from desktop/icons/app.png.

Usage: python3 .github/scripts/make-icns.py [desktop/icons/app.png] [desktop/icons/app.icns]

The ICNS holds only PNG entries (supported since macOS 10.7), each a downscaled copy of the source picture; sizes
above the source are left out instead of being upscaled (512 px source: up to ic09/ic14, no ic10 = 1024 px).
Needs Pillow (pip install pillow). Run it again whenever app.png changes (README › Logo ändern).
"""
import io
import struct
import sys

from PIL import Image

# ICNS type -> edge length in pixels (the @2x types hold the double size of their point size).
ENTRIES = [
    ("icp4", 16),   # 16x16
    ("ic11", 32),   # 16x16@2x
    ("icp5", 32),   # 32x32
    ("ic12", 64),   # 32x32@2x
    ("ic07", 128),  # 128x128
    ("ic13", 256),  # 128x128@2x
    ("ic08", 256),  # 256x256
    ("ic14", 512),  # 256x256@2x
    ("ic09", 512),  # 512x512
    ("ic10", 1024), # 512x512@2x
]


def png_bytes(img: Image.Image, size: int) -> bytes:
    scaled = img if img.size == (size, size) else img.resize((size, size), Image.LANCZOS)
    out = io.BytesIO()
    scaled.save(out, format="PNG", optimize=True)
    return out.getvalue()


def main() -> None:
    src = sys.argv[1] if len(sys.argv) > 1 else "desktop/icons/app.png"
    dst = sys.argv[2] if len(sys.argv) > 2 else "desktop/icons/app.icns"
    img = Image.open(src).convert("RGBA")
    if img.width != img.height:
        raise SystemExit(f"{src}: Bild ist nicht quadratisch ({img.width}x{img.height})")
    cache = {}
    chunks = []
    for kind, size in ENTRIES:
        if size > img.width:
            continue
        data = cache.get(size) or cache.setdefault(size, png_bytes(img, size))
        chunks.append(kind.encode("ascii") + struct.pack(">I", 8 + len(data)) + data)
    body = b"".join(chunks)
    with open(dst, "wb") as f:
        f.write(b"icns" + struct.pack(">I", 8 + len(body)) + body)
    print(f"{dst}: {len(chunks)} Einträge, {8 + len(body)} Bytes")


if __name__ == "__main__":
    main()
