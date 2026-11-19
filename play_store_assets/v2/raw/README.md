# Real screenshots go here

Put **real phone screenshots** of the final v1.1 build in this folder, with exactly these names.
The build script frames them with captions. It never draws or invents UI: a missing file is skipped.

| File | Capture this screen | Caption it gets |
|---|---|---|
| `01_gameplay.png` | A 3x3 Classic round in progress (some pads lit, score showing) | Watch the glow. Send it back. |
| `02_setup.png` | Home screen with the grid sizes and the Easy / Hard slider visible | Choose your difficulty |
| `03_share.png` | "Share Challenge to <rival>" dialog (use a believable rival name) | Challenge a friend with a text code |
| `04_hall_of_fame.png` | Hall of Fame, Rivalry tab, 2 to 3 rivals with mixed wins and losses | Defend your score |
| `05_revenge.png` | A rival card that shows the **Get Revenge** button | Lost a round? Take your revenge |
| `06_offline.png` | Any main screen, taken with **airplane mode on** (the status bar shows it) | Fully offline. Zero ads. |

Tips: full battery, no notifications in the status bar, dark theme, PNG, do not crop or edit.
Then run: `python3 ../source/build_screenshots.py` and check `../screenshots/`.
