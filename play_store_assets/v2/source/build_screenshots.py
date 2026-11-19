#!/usr/bin/env python3
"""Frames REAL phone screenshots (../raw/*.png) with a caption -> ../screenshots/*.png (1080x1920).
Usage: python3 build_screenshots.py [raw_dir] [out_dir]
Needs: pip install playwright pillow  (+ a Chromium/Chrome). Nothing is invented: a missing raw file is skipped."""
import json, os, sys, pathlib
from playwright.sync_api import sync_playwright
here = pathlib.Path(__file__).resolve().parent
raw = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else here.parent / "raw"
out = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else here.parent / "screenshots"
out.mkdir(parents=True, exist_ok=True)
tpl = (here / "frame.html.tpl").read_text(encoding="utf-8")
TOP = 440                       # where the phone starts; it bleeds off the bottom edge
PW = 800                        # phone frame width
PH = 1920 - TOP + 20            # taller than the canvas so the bottom is cropped
def build_bands(item, tpl_b):
    W, S = 1080, 60                      # canvas width, half-slant in px
    top0, band_h, n = 470, 470, len(item["panels"])
    bands, lines, pills = [], [], []
    for i, pnl in enumerate(item["panels"]):
        src = raw / pnl["raw"]
        if not src.exists(): return None
        y = top0 + i * band_h                       # boundary centre line i
        top = y - S if i else y - S                 # band box top
        hgt = band_h + 2 * S
        tl = f"0px,{2*S if i else S}px"             # top edge at x=0 (first band: shallower)
        tr = f"{W}px,{0 if i else 0}px"
        last = (i == n - 1)
        bl = f"0px,{hgt if last else band_h + 2*S}px"
        br = f"{W}px,{hgt if last else band_h}px"
        poly = f"polygon({tl},{tr},{br},{bl})"
        bands.append(f'<div class="band" style="top:{top}px;height:{hgt}px;clip-path:{poly}"><img src="{src.resolve().as_uri()}" style="top:{S - pnl["y"]}px"></div>')
        if i:
            lines.append(f'<line x1="0" y1="{y+S}" x2="{W}" y2="{y-S}" stroke="#F4F7FB" stroke-width="7" stroke-linecap="round" style="filter:drop-shadow(0 0 14px rgba(0,229,255,.9))"/>')
        if pnl.get("label"):
            ptop = (y + S + 4) if i else (y + 4)      # top-left corner of its band, in the slant gap
            pills.append(f'<div class="pill" style="top:{ptop}px;background:{pnl.get("color","#00E5FF")}">{pnl["label"]}</div>')
    return (tpl_b.replace("{{CAPTION}}", item["caption"]).replace("{{SUB}}", item["sub"])
            .replace("{{BANDS}}", "".join(bands)).replace("{{LINES}}", "".join(lines)).replace("{{PILLS}}", "".join(pills)))

tpl_b = (here / "bands.html.tpl").read_text(encoding="utf-8")
done = 0
with sync_playwright() as p:
    exe = os.environ.get("CHROME", "/usr/bin/google-chrome")
    b = p.chromium.launch(executable_path=exe if os.path.exists(exe) else None, args=["--no-sandbox"])
    for item in json.loads((here / "captions.json").read_text(encoding="utf-8")):
        if "panels" in item:
            html = build_bands(item, tpl_b)
            if html is None:
                print("skip (missing a real screenshot):", item["file"]); continue
            f = here / "_frame.html"; f.write_text(html, encoding="utf-8")
            pg = b.new_page(viewport={"width": 1080, "height": 1920})
            pg.goto(f.as_uri()); pg.wait_for_timeout(400)
            pg.screenshot(path=str(out / (item["file"] + ".png"))); pg.close(); done += 1
            print("wrote", item["file"] + ".png"); continue
        src = raw / item["raw"]
        if not src.exists():
            print("skip (no real screenshot yet):", item["raw"]); continue
        anchor = {"top": "center top", "center": "center center", "bottom": "center bottom"}[item.get("anchor", "top")]
        html = (tpl.replace("{{CAPTION}}", item["caption"]).replace("{{SUB}}", item["sub"])
                   .replace("{{IMG}}", src.resolve().as_uri()).replace("{{TOP}}", str(TOP))
                   .replace("{{PH}}", str(PH)).replace("{{PW}}", str(PW)).replace("{{ANCHOR}}", anchor))
        f = here / "_frame.html"; f.write_text(html, encoding="utf-8")
        pg = b.new_page(viewport={"width": 1080, "height": 1920})
        pg.goto(f.as_uri()); pg.wait_for_timeout(400)
        pg.screenshot(path=str(out / (item["file"] + ".png")))
        pg.close(); done += 1; print("wrote", item["file"] + ".png")
    b.close()
(here / "_frame.html").unlink(missing_ok=True)
print(done, "screenshot(s) built")
