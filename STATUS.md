# Final status (source release 1.0.0)

Phases 1-6 complete as source. Phase 7 (compile, run on a device, deliver an APK) could NOT be completed in the authoring
environment: no Android SDK, and dl.google.com / maven.google.com / services.gradle.org were unreachable (checked).

## Verified
- engine module compiled with Kotlin 2.0.21; 91 tests pass (local JUnit-compatible runner, JUnit 4 API).
- Audio files generated and format/level-checked.

## Not verified (never compiled or run)
- app module: Compose UI, animations, DataStore storage, audio playback, haptics, themes on screen.
- Gradle files (AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.09.00).
- Real JUnit run under Gradle; AI speed on a phone (Hard ~25 ms/move on a desktop JVM).
- Sound quality: the audio was generated and measured but never listened to.

## Known limitations
- Dragon tile not implemented; mode rules are this project's interpretation (MODES.md).
- One AI difficulty per game (shared by all AI players).
- After restoring a save, AI random tie-breaking restarts (moves stay legal).
- Menu buttons have no click sound. Only English. Landscape-first layout (portrait layout exists but is untested).
- AI in Loop Battle / Longest Path only has a score bonus in its evaluation, no dedicated strategy.
- Acceptance criteria that need a device (launch, full match in UI, settings persisting across restart, no crashes, APK installs) are UNCHECKED.
