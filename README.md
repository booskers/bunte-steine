# Bunte Steine

**Download:** [Bunte-Steine.apk](https://github.com/booskers/bunte-steine/releases/latest/download/Bunte-Steine.apk) (latest release). Free and open
source under the MIT licence. Made with love by booskers / Polychrome.

A Clickomania for Android phones ("Colorful Stones" in English), made for older players and people with low vision.
English or German, following the phone's language (polite "Sie" in German); upright (portrait) only.

**The game:** tap a group of 2 or more touching stones of the same colour and the whole group vanishes. Stones above fall
down, and empty columns close up to the left. A group of n stones scores n × (n − 1) points; clearing the board earns
500 more. When no group is left, the game ends with 1 to 3 stars (3 = every stone gone).

What makes it easier than the classic:

- **Big stones:** Leicht 5 × 8 (3 colours), Mittel 6 × 10 (4), Schwer 7 × 12 (5), Sehr schwer 8 × 13 (6 colours).
- **Groups are one shape:** stones sit close together, and touching stones of the same colour join into one piece.
- **Press to preview:** while a finger rests on a stone, its group lights up with "9 Steine · +72 Punkte". Lifting the
  finger removes the group; sliding away cancels.
- **Symbols are optional:** they are off on the first start, so the stones are plain colours. "Zeichen auf den Steinen"
  (on the last welcome page and in Einstellungen) puts a circle, square, triangle, star or heart on each colour for people
  who find the colours hard to tell apart. Hints, speech and labels say "Steine" or the shape to match.
- **Tipp** (yellow) shows the biggest group with a pointing hand. **Zurück** undoes moves, without limit.
- **The coach** steps in when the gestures aren't working out: repeated taps on a lone stone, trying to drag stones,
  tapping the empty board, double taps, pinch-zooming, holding a finger down without letting go, or a long time
  without a move. Each guide has a small animated picture, shows at most three times, and closes with a tap anywhere; messages close when tapped.
- **Einstellungen:** Hilfe-Hinweise (the coach, on/off), Zeichen, Ansagen vorlesen (the phone's text-to-speech, in its language), Töne, Vibration. Records per level
  under "Ihre Rekorde". The game is saved after every move.

## Layout

- `web/index.html` — the whole game (one file, no internet needed).
- `android/` — one WebView activity, locked upright, full screen; `python build.py` makes `android/dist/Bunte-Steine.apk`
  (no Gradle; needs JDK 17 and Android SDK platform 35 / build-tools 35.0.0). The signing key is made on the first build
  in `%USERPROFILE%\.buntesteine\` — keep it, or updates won't install.
- `tools/serve.mjs` — `node tools/serve.mjs` serves the game at http://localhost:5191/ for testing in a browser.

## Updates

The game updates itself from this repository's releases, unnoticed: it looks for a new version when it is left and
every hour in the background, and installs it only while the game is closed. Next time it opens it simply is the new
version, with the saved game kept. Android allows that from Android 12 on, after **Automatische Updates einschalten**
in Einstellungen has been tapped once (Android asks one time). On Android 11 and older each update needs a tap there.

**Releasing:** raise `versionCode` and `versionName` in `android/AndroidManifest.xml`, run `python android/build.py`
(signs with the key in `%USERPROFILE%\.buntesteine\`; keep it, or updates won't install), add a section to
`CHANGELOG.md`, commit, then attach **both** files from `android/dist/`:

    gh release create vX.Y.Z android/dist/Bunte-Steine.apk android/dist/bunte-steine.json --title "Bunte Steine X.Y.Z" --notes "…"

Phones read `releases/latest/download/bunte-steine.json`; a release without it is never installed.
