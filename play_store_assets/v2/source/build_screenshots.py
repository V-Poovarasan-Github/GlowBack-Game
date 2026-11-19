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
done = 0
with sync_playwright() as p:
    exe = os.environ.get("CHROME", "/usr/bin/google-chrome")
    b = p.chromium.launch(executable_path=exe if os.path.exists(exe) else None, args=["--no-sandbox"])
    for item in json.loads((here / "captions.json").read_text(encoding="utf-8")):
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
