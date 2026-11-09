# Validation record

Validated 2026-11-10 with the preserved Gradle configuration.

- `:app:assembleDebug` — passed; API 26 minimum / SDK 37 build.
- `:app:testDebugUnitTest` — 4 tests passed. New tests validate all 114 chapter counts, all 6,236 sequential references, all 30 Juz, every bundled text row, verified asset fingerprints, and Arabic search normalization.
- `:app:lintDebug` — passed with zero errors. Warnings remain for existing dependency updates, unused XML resources, exported media-service declaration and stylistic KTX suggestions. Media connections are restricted to this package and trusted system controllers.
- `:app:connectedDebugAndroidTest` — 3 tests passed on the connected Nothing A069P / Android 17 device. Tests construct the real offline repository with runtime integrity validation, load Geist / NDot / Amiri, verify key reference metadata, and connect a Media3 controller to the playback service to set and clear a queue. The queue test deliberately does not prepare or play audio.
- `tools/verify_quran.py --source-dir /tmp/nos-verified` — passed for all four published editions and all Surah / ayah / Juz references. Source responses were retrieved from Al Quran Cloud. The temporary source directory is not part of the repository; use `--download` to reproduce independently.
- Each configured EveryAyah reciter endpoint returned HTTP 200 with `audio/mpeg` for 1:1.
- App installed and launched successfully on the connected Nothing device. Dark library layout visually inspected. Runtime accessibility inspection identified and corrected missing standalone switch, radio and slider labels.
- `git diff --check` — passed.

Not claimed as tested: the full Android 8–17 device matrix, TalkBack end-to-end, all font-scale/orientation combinations, audible playback and lock-screen behavior, download completion/cancellation under network loss, reminder delivery over device idle/reboot, or every document-provider import/export workflow. These remain release QA tasks, alongside the licensing and Tafsir boundaries described in README.md.
