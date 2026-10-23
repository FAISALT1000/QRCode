# ClipQR — Smart Clipboard + QR (Android, 100% offline)

Everything stays on your phone. No internet, no account, no ads.

## Features

**Library (smart clipboard)**
- Saves what you copy automatically when you open the app (Android only lets apps read the clipboard while they're on screen).
- Sorts each item into a category: Links, Emails, Phones, Codes (OTP), Wi-Fi, Contacts, Text.
- Filter chips, search, pin favorites (IBAN, email signature, standard replies), copy with one tap.
- Use **Share → ClipQR** from any app to save text directly.
- Keeps up to 500 unpinned items; pinned items are never removed automatically.

**Scan**
- Scan QR codes with the camera, or from a photo/screenshot.
- Smart actions by type: open link, call, email, copy Wi-Fi password, add contact.
- **Link safety check**: warns about http links, IP addresses, short links, look-alike letters, '@' tricks and .apk downloads before opening.

**Create**
- QR from text/link, Wi-Fi (guests scan to join), contact card, or anything in your library.
- Share the QR as an image, or save it to your library.
- Any library item can be shown as a QR, so you can pass text to another phone with no internet.

## Build it — option A: Android Studio (on your computer)
1. Install Android Studio (free): https://developer.android.com/studio
2. Unzip this folder, then **File → Open** and choose the `ClipQR` folder.
3. Wait for Gradle sync to finish (first time downloads tools, needs internet).
4. Plug in your phone with USB debugging on, then press **Run ▶**.
   Or use **Build → Build App Bundle(s)/APK(s) → Build APK(s)** and copy the APK to your phone.

## Build it — option B: GitHub (no install needed)
1. Create a new repository on github.com and upload all files from this folder.
2. Open the **Actions** tab; the "Build APK" workflow runs automatically.
3. When it finishes (about 5 minutes), open the run and download **ClipQR-debug-apk**.
4. Copy the APK to your phone and install it (allow "Install unknown apps" when asked).

## Tech
- Kotlin, Jetpack Compose, Material 3 (follows your phone's colors on Android 12+)
- ZXing (zxing-android-embedded 4.3.0) for scanning and generating QR codes
- Storage: private JSON file in the app's folder
- minSdk 24 (Android 7.0), targetSdk 35

## Files
- `app/src/main/java/com/clipqr/app/MainActivity.kt` — entry point, clipboard capture, share handling, theme
- `App.kt` — all screens (Library, Scan, Create, item details)
- `Data.kt` — library storage and the ViewModel
- `Logic.kt` — categorizing, link safety, QR encode/decode, Wi-Fi/contact formats, phone actions
