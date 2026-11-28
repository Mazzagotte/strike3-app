# App icon

Fanned hand of three cards: two brushed-chrome cards behind a gold refractor with an embossed
backwards K (the scorebook mark for a called third strike).

- `make.py` writes `fg.svg` (transparent, 108dp adaptive-icon canvas; art inside the 66dp safe circle).
  `python3 make.py 0.8 0.6` (the numbers nudge the art to the optical center).
- `shot.py fg.svg` renders it to `fg.png` (1024 px) with headless Chrome, which handles the SVG
  filters and blend modes. Resize that to `mipmap-*/ic_launcher_foreground.png`
  (108, 162, 216, 324, 432 px).
- `mono.py` writes `drawable/ic_launcher_foreground.xml`, the one-color themed icon, from the same geometry.
