# nosq

Native Kotlin / Jetpack Compose Quran reader, package `dev.faizeenhoque.nosquran`, minimum Android 8.0 (API 26). The existing SDK 37, AGP, Kotlin, Compose BOM and font resources are preserved.

## Build

Use the checked-in Gradle wrapper with the configured Java 25 toolchain and Android SDK 37:

```sh
./gradlew :app:assembleDebug :app:testBenchmarkUnitTest :app:lintDebug
./gradlew :app:connectedBenchmarkAndroidTest # connected device required
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Implemented

- Warm monochrome and dark/OLED themes, original Geist / NDot / Amiri Quran typography, custom navigation and reusable NOS components.
- Three-step onboarding, dashboard, estimated Hijri date, dated daily verse, quick passages, recent activity and reading statistics.
- All 114 Surahs / 6,236 ayahs offline, Juz browsing, revelation filters, direct references, Arabic / English / Bengali search, recent queries and translation highlighting.
- Scrolling reader and one-ayah pages, exact verse resume, separate introductory Bismillah, contextual actions, configurable Arabic / translation size, spacing, numerals, separators and transliteration.
- Reader screen-awake preference, notes, collections, sorting, renaming, bookmarks and JSON import/export through the Android document picker.
- Media3 playback service, notification / system media controls, provider-sourced reciter catalogs, mini and expanded players, seeking, speed, automatic advancement, repeat ayah / queue / selected range, error retry and background-playback preference.
- DownloadManager recitation downloads over unmetered networks, download status, cancellation and deletion. Incomplete files are never played as completed downloads.
- Approximate daily reminders with optional notification permission, time selection and rescheduling after reboot.
- History, statistics, translation and reciter selectors, verse details, source attribution, privacy and license information. Settings have persistent effects.

## Accuracy and provenance

On 2026-11-10, every bundled Arabic, English, Bengali and transliteration row was compared against the corresponding published Al Quran Cloud edition. Surah, ayah and Juz references were also compared. Arabic matches exactly after removing the API transport BOM and separating the unnumbered introductory Bismillah from applicable first ayahs. The stored verse text is not normalized or rewritten. Search normalization operates only on temporary comparison strings.

`tools/verify_quran.py --download` repeats the comparison. `--source-dir PATH` can verify previously downloaded responses without a network. SHA-256 fingerprints are checked at runtime and by unit tests. Structural tests cover every chapter and all 6,236 references; Android instrumentation loads the actual repository and all three font resources.

Sources and terms are in `app/src/main/assets/quran/NOTICE.txt` and available inside the app. Arabic is from the Uthmani / Hafs edition; translations are Saheeh International (English) and Muhiuddin Khan (Bengali), plus the English transliteration edition. Tanzil attribution and its text notice are retained.

## Release boundaries

This is a functioning implementation, with the following release work explicitly outstanding:

- No offline Tafsir edition is bundled. Verse details explain this and open published Ibn Kathir commentary on Quran.com. A future adapter must supply verified text, source attribution and a license before inline commentary is enabled.
- The bundled translations have non-commercial distribution terms. Obtain publisher permission before commercial distribution. Confirm NDot redistribution rights; it was an existing project asset. Confirm EveryAyah audio distribution terms for the intended release.
- The interface is English; Bengali is a Quran translation selection. Full interface localization is not implemented.
- Page mode is one ayah per page, not a reproduction of a printed Madani Mushaf. Statistics record ayahs brought into view, not time or proof of completion.
- DownloadManager handles connectivity and retries; there is no automatic bulk download of the entire Quran. Recitation requires network access until downloaded. Streaming uses EveryAyah or MP3Quran directly, without an application backend.
- Reminder delivery is approximate and subject to Android notification and battery policies. Changing the timezone after scheduling requires reopening reminder settings to reschedule.
- Release signing, store metadata, publisher/data/font permissions and a full API 26–37 device/accessibility test matrix remain release tasks. No production-readiness claim replaces those checks.

## Structure

`DesignSystem.kt` holds visual tokens and shared controls. `AppState.kt` owns persistent preferences, saved verses and local reading state in an Android ViewModel. `QuranRepository.kt` loads and checks immutable text on an IO dispatcher via `QuranLoader`. Compose Navigation owns screen destinations. Browse, reader, settings, player, downloads and supporting screens are separate modules. `Playback.kt` owns Media3 service/controller integration and DownloadManager boundaries; `Reminders.kt` owns optional local notifications.

No account, ads or analytics SDK. App-private data may participate in Android backup according to system settings; exports contain personal notes.

## Phase 2: motion and performance

`Motion.kt` centralizes precise easing, durations, press feedback, state crossfades, activity dots, expandable sections and reader control transitions. Navigation has separate forward/back transitions; Navigation Compose handles predictive back. Motion follows both the app preference and Android animator-off accessibility setting. Only visible verse expansions animate; full-list placement animations are avoided. Player clocks update in isolated scopes, with draw-phase progress interpolation.

The immutable repository indexes chapter, Juz and reference lookups, and lazily builds a normalized Arabic search index on `Dispatchers.Default` when Search opens. Search is cancellable with a 100 ms input debounce. Preference loading/JSON parsing runs off main; ordered background writes coalesce slider and reading changes. Progress uses snapshot maps and per-chapter counts. Playback service connection is lazy, media preparation/file checks use IO, and playback clocks stop polling while inactive or backgrounded. Download scans run only while their screen is started, and idle polling is reduced.

Release optimization is enabled. The separate `.benchmark` package uses the same optimized release configuration, a debug signing certificate and shell profileability, leaving the production package untouched. Benchmark playback is muted. Device-generated Baseline/Startup Profiles live in `app/src/main/baselineProfiles`; no handwritten profile rules are substituted.

```sh
python3 tools/run_performance.py --serial YOUR_ADB_SERIAL
# Regenerate profiles from real interactions before measuring:
python3 tools/run_performance.py --serial YOUR_ADB_SERIAL --generate-profile
```

Keep the device unlocked and untouched, with battery above 30%. The harness deliberately fails on test errors; it does not silently suppress performance validation. Raw logs, JSON and Perfetto paths are saved under `build/performance/run`. See `docs/MOTION.md` for the current motion system. Benchmark tooling is preserved but the profiling phase was stopped at the user’s request. Android instrumentation now targets the isolated benchmark variant, not the user's installed package. Release APK remains unsigned pending your production signing configuration.

## Latest navigation and catalog update

See `docs/OVERHAUL.md`. The three-reciter selector has been replaced with the provider catalog snapshots in `assets/audio` and a cached refresh boundary. Full-Surah and verse-by-verse recordings have distinct capabilities. The existing audio service was preserved after the user confirmed audio was fixed. The library now has a swipeable Surah/Juz pager and collapsing header; bottom tabs coalesce rapid requests; reader return navigation preserves its restored position. Motion profiles are selectable in Appearance settings.

## App identity

- App: **nosq**
- Developer: **Faizeen Hoque**
- Developer website: https://faizeenhoque.dev
- App website: https://nosq.faizeenhoque.dev
- Android application ID: `dev.faizeenhoque.nosquran` (preserved for installed-app and data compatibility)

The About and Settings version labels use the build's version name.
