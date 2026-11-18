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

**Collection log**
- Player, year, set, parallel, serial, team, auto/rookie/patch, date acquired, price paid, posted, notes
- Search, plus filters for autos, rookies and not posted
- Ready-made captions with hashtags

**Backup**
- Card log → Google Drive. Settings › Set up Drive backup creates `strike3-backup.json` (restore file) and `strike3-collection.csv` (opens in Google Sheets). Both are rewritten after every change.
- Photos → Google Photos. Posts go to `Pictures/Strike III`, originals to `Pictures/Strike III Originals`. Turn on backup for both folders in Google Photos.

## Getting the APK

Every push to `main` builds the app on GitHub Actions:

1. Open the repo's **Actions** tab › latest **Build APK** run.
2. Download the **strike3-apk** artifact (a zip), unzip it, and install `app-debug.apk` on the phone.

All builds are signed with the same key (`keystore/strike3-debug.jks`), so new versions install over the old one and keep your data.

You can also open the project in Android Studio and run it directly.

## Optional: brand fonts

The template uses the phone's condensed font unless these files are added to `app/src/main/res/font/`:
- `bebas_neue.ttf` (Bebas Neue, for STRIKE III CARDS)
- `oswald_medium.ttf` (Oswald Medium, for tags and the K)

Both are free on Google Fonts. File names must be exactly as above.

## Notes

- First cutout after installing may fail while Google Play services downloads the model. Wait a minute and try again.
- The clear plastic holder can confuse the cutout. If edges look rough, use "Shot on black" instead.
- Package: `dev.mazzagotte.strike3` · min Android 10 · Kotlin + Jetpack Compose, CameraX, ML Kit, Room.
