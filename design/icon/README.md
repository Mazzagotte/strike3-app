# App icon

The III bars (silver, silver, gold — the same mark as the post template) in brushed chrome and
gold foil, inside a single gold-framed card on near-black.

- `barsicon.py` writes `full_D.svg` / `fg_D.svg` (option D; `fg` is transparent, the 108dp
  adaptive-icon canvas with the art scaled 0.94 to sit in the 66dp safe circle). It takes the
  gradients and filters from `make.py` (the earlier fanned-cards icon, kept for reference).
- `shot.py fg_D.svg` renders it to a 1024 px PNG with headless Chrome (needed for the SVG
  filters and blend modes). Resize to `mipmap-*/ic_launcher_foreground.png` (108, 162, 216, 324, 432 px).
- `mono.py` writes `drawable/ic_launcher_foreground.xml`, the one-color themed icon (card outline + bars).
