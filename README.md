# Strike III

Personal Android app for the Strike III Cards Instagram account (@strikelllcards):
shoot a card, drop it on the branded post template, log it, and share it to Instagram.

## What it does

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
