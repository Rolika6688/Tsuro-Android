# Tsuro (Android) — source release 1.0.0

A Tsuro-inspired path game for Android with original code and artwork: 6x6 board, 35 generated tiles, 2-8 players,
Easy/Normal/Hard AI, four modes (Classic, Solo, Loop Battle, Longest Path), animations, original audio, themes,
saved settings/statistics/games. Offline, no permissions.

## IMPORTANT — what has and has not been verified
- VERIFIED (compiled with Kotlin 2.0.21 and tested, 91 tests passing): the whole `engine/` module — rules, AI, animation planner,
  save/restore, settings, statistics, game modes.
- NOT VERIFIED: the `app/` module (Jetpack Compose UI, DataStore wrapper, audio playback, haptics) and the Gradle build.
  They were written without an Android SDK or Maven access, so they have never been compiled or run.
- **No APK has been built.** An APK only exists after you build it (below). Expect a few compile errors on first build.

## Get an APK
**A. GitHub Actions (no local setup).** Put this folder in a GitHub repository, open Actions -> "Build APK" -> Run workflow,
download the artifact `tsuro-debug-apk`, install it on the phone (allow "install unknown apps"). The workflow also runs the engine tests.

**B. Android Studio (Hedgehog or newer).** Open this folder, let Gradle sync (JDK 17), then Run `app` on a device/emulator (API 26+),
or Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

**C. Command line.** Needs JDK 17, Android SDK (platform 34, build-tools 34.0.0, `ANDROID_HOME` set), Gradle 8.9:
`gradle :engine:test :app:assembleDebug`.

## Project layout
- `engine/` pure Kotlin (no Android): `tiles` (ports, rotation, generated tile set), `board`, `rules` (path tracing, collisions, loops),
  `engine` (turns, moves, victory, modes), `ai` (Easy/Normal/Hard), `animation` (timeline planner), `storage` (JSON codecs, statistics, settings), `ui` (turn controller).
- `app/` Compose UI + Android glue: screens, board/tile drawing, audio (`audio/GameAudio`), DataStore (`storage/AppStorage`), ViewModel.
- `tools/make_audio.py` regenerates the audio in `app/src/main/res/raw` (all sounds are synthesised; no third-party material).
- `MODES.md` mode rules, `engine/AI_NOTES.md` AI design, `engine/TEST_REPORT.md`, `STATUS.md`.

## Rules deviations to know
Tiles have 8 ports (2 per side) like the real game, not 4. No Dragon tile. A stone trapped in a closed loop is eliminated.
Players unable to move because tiles ran out end the game with the survivors sharing the win.

## Running the engine tests without Android
`gradle :engine:test`, or compile `engine/src/main` + `engine/src/test` with kotlinc and a JUnit 4 jar.

## Known limitations
See STATUS.md.
