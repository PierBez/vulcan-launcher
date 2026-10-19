#!/usr/bin/env python3
"""Build per-setting contact sheets from a finished sweep, for visual review.

Usage: python3 montage.py [sweep-dir] [--tile-h 420] [--cols 5]
Writes sheets/<setting>.png next to the run's results.jsonl.
"""
import argparse
import json
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))


def label_font(size):
    for path in ("/System/Library/Fonts/Helvetica.ttc", "/System/Library/Fonts/SFNS.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            continue
    return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("sweep_dir", nargs="?", default=os.path.join(ROOT, "out/sweep1"))
    ap.add_argument("--tile-h", type=int, default=420)
    ap.add_argument("--cols", type=int, default=5)
    args = ap.parse_args()
    results_path = os.path.join(args.sweep_dir, "results.jsonl")
    groups = {}
    for line in open(results_path):
        e = json.loads(line)
        groups.setdefault(e["setting"], []).append(e)
    out_dir = os.path.join(args.sweep_dir, "sheets")
    os.makedirs(out_dir, exist_ok=True)
    font = label_font(18)
    for setting, entries in sorted(groups.items()):
        entries.sort(key=lambda e: e["sid"])
        tiles = []
        for e in entries:
            shot = next((s for s in e.get("shots", []) if os.path.exists(s)), None)
            if not shot:
                continue
            im = Image.open(shot).convert("RGB")
            w = int(im.width * args.tile_h / im.height)
            tiles.append((e, im.resize((w, args.tile_h))))
        if not tiles:
            continue
        tw = tiles[0][1].width + 8
        rows = (len(tiles) + args.cols - 1) // args.cols
        sheet = Image.new("RGB", (tw * min(len(tiles), args.cols), (args.tile_h + 30) * rows), (24, 24, 28))
        d = ImageDraw.Draw(sheet)
        for i, (e, im) in enumerate(tiles):
            x = (i % args.cols) * tw + 4
            y = (i // args.cols) * (args.tile_h + 30) + 26
            sheet.paste(im, (x, y))
            label = e["value"] + (" [CRASH]" if e.get("crash") else "") + (" [miss]" if e.get("miss") else "")
            d.text((x, y - 22), label, fill=(255, 120, 120) if e.get("crash") else (230, 230, 230), font=font)
        sheet.save(os.path.join(out_dir, f"{setting}.png"))
        print(f"{setting:16} {len(tiles):>3} tiles  -> sheets/{setting}.png")


if __name__ == "__main__":
    main()
