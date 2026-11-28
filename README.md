# Strike III

Personal Android app for the Strike III Cards Instagram account (@strikelllcards):
shoot a card, drop it on the branded post template, log it, and share it to Instagram.

## What it does

**Collection**
- Search with several words in any order ("judge 2024 chrome auto"). Special words: auto, rc/rookie, relic/patch, numbered, 1/1, posted, and /99 for a print run. Player and team names allow a small typo.
- Filters: Autos, Rookies, Numbered, 1/1, Posted / Not posted, plus Team, Set and Year lists built from your cards (with counts).
- Sort: newest, oldest, player A–Z, year, or print run (lowest first).
- View: 2 or 3 columns or a list, card front photos or posts, and grouping by player with counts. A summary line shows cards, players, autos and numbered for what's on screen. Search and filters stay put when you open a card and come back.
- Card page: swipe left/right through the current results, Front / Back / Post views, tap to flip, pinch to zoom, double-tap to reset, and "All N player cards" to jump back to that player's cards.

**Batch add, stats and export**
- Add card › Batch: shoot front, back, next card for a whole stack. Each card is read (front and back) and saved in the background, marked **Needs details**. Finish them later from the Needs details filter; the card page has a Finish details button.
- The reader reads the front as well as the back. A player already in your collection found on either side wins, then a name both sides agree on. A later reading can replace what the reader guessed earlier, but never what you typed.
- Duplicate check: saving a card with the same player, year, set, parallel and serial as one you already have asks first.
- Current value on each card (price paid is no longer shown). Sort by value; the summary line and Stats show totals.
- Stats page (chart icon): totals, value and most valuable cards, top players, teams, years, sets, and cards added by month. Tap a row to see those cards.
- Value page ($ icon): total worth, how many cards are priced (tap to see the unpriced ones), most valuable cards, value by player, set, team and year, and how much sits in autos, numbered and rookies.
- Settings › Export Excel with photos: an .xlsx saved to Downloads/Strike III with the front and back photos placed in their cells (Excel for Microsoft 365), then Share or Open. Import spreadsheet reads the .xlsx or a CSV back in.
- Settings › Export CSV saves the whole collection as a spreadsheet anywhere.
- Settings › Import CSV brings spreadsheet edits back: rows match cards by the ID column, rows without an ID become new cards (no photos), and you confirm the counts first. Serials are exported as ="1/1" so Sheets and Excel don't turn them into dates.

**Card reader**
- Reads the "from 2025 Topps Cosmic Chrome Baseball" line on autograph and insert backs for the exact year and set (forgiving of small misreads).
- Year: ignores birth, draft and debut years and honored designs ("1990 Topps"); uses the copyright line, then the last stats season + 1, then the year mentioned most.
- Card codes (BCP-139, PD-77, RA-WWA, US325…) name the product and go into Notes.
- Player name: the line above the team or position line, matched against players already in the collection.
- Serials must look like a print run (49/199), never a date; the front's foil stamp is preferred.
- Grading labels (PSA, CCG, BGS, SGC): year, set, card number and grade (into Notes).
- Parallel from print run + border color (Bowman Chrome /150 + blue = Blue Refractor).
- Guessed fields are marked: amber "?" on the details screen and card page, yellow in the Excel export, listed in a "Check" column (clear it and import to confirm).

**Catalog (Add card)**
- Shoot the front with the card guide, add the back (it reads the player, year, set, serial and more), fill in the rest and save to the collection. No post is made.
- The back reader checks the text against every player name already in your collection (allowing small misreads and names split over two lines), so it gets better the more you catalog. Fix a wrong name once and it's learned.
- Make the post later from the card page (**Make post**); it opens the editor with the saved front photo and the card's badges.
- Cards without a post show their front photo in the collection grid. The **Not posted** filter lists them.

**Post Maker**
- Camera with a card-shaped guide (or pick a photo from the gallery)
- Auto cutout with Google's on-device ML Kit, or "shot on black" mode that fades the edges and hides the backdrop
- Template: dark spotlight background, faint III, floor and reflection, optional tag pill (New pickup, PC favorite or custom), fanned-hand + STRIKE III CARDS at the bottom
- Drag and pinch to place the card, plus sliders for size, edge fade and brightness
- Saves a 1080×1350 JPG and opens Instagram's share options with the caption already copied

**On each saved card**
- **Edit post:** reopen the post with its saved settings and change the tag, position or look without reshooting
- **Share as Story or Reel cover:** the same post at 1080×1920 (9:16)
- **Carousel:** add a photo of the back and a close-up; slide 2 shows them side by side, and both slides share to Instagram together
- **Listing photo:** plain white 1600×1600 photo for selling or trading, saved to `Pictures/Strike III Listings`

**Read details from the card back**
- On the details screen, take or pick a photo of the back. On-device text recognition fills in the player, year, set, serial, team, auto and rookie when it finds them. Only empty fields are filled, and the photo becomes the carousel's back slide.

**Camera**
- Tap anywhere on the preview to focus and set exposure there.

**Collection log**
- Player, year, set, parallel, serial, team, auto/rookie/patch, date acquired, price paid, posted, notes
- Search, plus filters for autos, rookies and not posted
- Parallel suggestions under the Parallel field (tap to fill): names you've already used for the same set and print run, best match same year, plus the photo's border color as a hint (e.g. "Purple?") when the log has nothing yet. Nothing is filled automatically
- Serial number entered as two number boxes with the slash built in (049 / 199), so the number pad works
- Caption styles on each card: **Clean** (player, set line, numbered), **Strike III** (umpire call opener, "joins the Yankees PC") and **Short** (one line). Built only from the logged details, editable before sharing, and the last style you pick is used by every Share button

**Backup**
- Card log → Google Drive (details and post settings; the private photo copies used for re-editing stay on the phone). Settings › Set up Drive backup creates `strike3-backup.json` (restore file) and `strike3-collection.csv` (opens in Google Sheets). Both are rewritten after every change.
- Photos → Google Photos. Posts go to `Pictures/Strike III`, originals to `Pictures/Strike III Originals`. Turn on backup for both folders in Google Photos.
- **Card #** — every card's printed number/code (TOTD-22, BCP-139, US325, #287) has its own field. The reader fills it from the back (or a grading label), search finds it with or without the dash ("totd22"), and it's a column in CSV/Excel export and import. Cards added before this field got their number copied from Notes once.
- **Numbered cards** — only the print run is kept ("/199"; one-of-ones are "1/1"). The serial box asks just for the number it's numbered to, and cards saved earlier were trimmed once.
- **Reader fixes from a 231-card check** — the reader now keeps the text it saw on each card (hidden; in backups and the photo export) so later fixes can be re-run without new photos. A card code that names a set (PD-41 → Topps Pro Debut, BD-118 → Bowman Draft) beats a plain "Topps"/"Bowman" logo. New codes: BD, BDC, HOC, FF, EG, PG, T90R, T90, 87T, PDAR, M1B. Years from throwback designs (87T, 90AS, T90R) aren't taken as the card's year. Bowman /399 is Neon Green; Topps Chrome /250 isn't guessed as Purple. A parallel that doesn't fit its print run (Gold on a /99 Bowman Chrome) is marked to check, on new reads and once on saved cards.
- **Reader fixes from 36 more cards** — a height/weight line ("HT: 6'7\" WT: 275") is no longer read as Topps Holiday HE67; "Yankees #1-June, 2013" (draft round) isn't card #1; stat headings ("HR RBI SB BB") aren't a name; "JUDGE, A." finds Aaron Judge when he's in the collection; no year later than this one; paper Bowman borders follow the 2024-25 checklists (Sky Blue /499, Neon Green /399, Fuchsia /299, Purple /250, Pink /175, Blue /150 …).
- **Do I have this?** — the magnifier button on the Collection screen opens the camera; snap a card's front or back and it lists what you already have: *You have this card*, *Same card – check the parallel*, *Same card, different version*, *Looks like one you have* (by photo), or *Same player and set*. Fix the player or card # if the reader got them wrong and the list updates; *Add to collection* carries on with the same photo. Nothing is saved otherwise.
- **Matching** — details are compared loosely (accents, "Jr.", dashes, leading zeros, "Topps" vs "Topps Pro Debut", blanks don't count against). Every card also gets a photo fingerprint (a 12×12 color thumbnail, made in the background from the photos already on the phone; not backed up). On 230 real fronts with tilt, framing, lighting and blur changes, the right card was the closest match 98% of the time; backs of one set look alike, so a back match needs a higher score. The save-time "already logged?" check uses the same rules.
- **Export photos for Claude** (Settings) — one zip of every card's front/back photos plus `cards.json` with the saved details, so the card reader can be tested and tuned against real photos.

## Getting the APK

Every push to `main` builds the app on GitHub Actions:

1. Open the repo's **Actions** tab › latest **Build APK** run.
2. Download the **strike3-apk** artifact (a zip), unzip it, and install `app-debug.apk` on the phone.

All builds are signed with the same key (`keystore/strike3-debug.jks`), so new versions install over the old one and keep your data.

You can also open the project in Android Studio and run it directly.

## Fonts

The template uses Bebas Neue (STRIKE III CARDS) and Oswald Medium (tags and the K), bundled in
`app/src/main/res/font/`. Both are free fonts under the SIL Open Font License (see `licenses/`).

## Notes

- First cutout after installing may fail while Google Play services downloads the model. Wait a minute and try again.
- The clear plastic holder can confuse the cutout. If edges look rough, use "Shot on black" instead.
- Package: `dev.mazzagotte.strike3` · min Android 10 · Kotlin + Jetpack Compose, CameraX, ML Kit, Room.
