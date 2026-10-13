# Changelog

## 1.2.1 (2026-10-13)

- Fixed: the game closed right after opening on Android 14 and 15. The background look for updates
  needed one more Android permission (network state); without it Android stopped the app at the start.

## 1.2.0 (2026-10-09)

The first public release.

- Updates itself from these releases, quietly while the game is closed: nobody playing is asked or interrupted, and
  the saved game stays (Android 12+, after "Automatische Updates einschalten" once in Einstellungen).
- An "Updates" part in Einstellungen: the version, the one-time switch, and "Nach Updates suchen".
