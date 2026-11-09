# Navigation, scrolling and reciter-catalog update

Audio repair and audible playback diagnosis were explicitly skipped after the user confirmed playback was fixed. The MediaSessionService, ExoPlayer construction, focus handling and output settings were preserved. Existing EveryAyah URLs and downloaded file paths retain compatibility.

## Implemented

- Distinct primary-tab, hierarchical, modal and backward transitions. Opaque destination surfaces prevent overlapping page text. Motion directions follow RTL layout.
- Tab requests are coalesced to the latest choice and wait for the current destination to resume before starting another transition. Existing save/restore navigation state is retained.
- A continuous bottom indicator and animated icon/label emphasis. The Surah/Juz library uses HorizontalPager with an indicator driven directly by swipe position and separate saved list states.
- A native collapsing library header with no forced header snap, indexed metadata lookups and stable list keys.
- The reader no longer reapplies its initial jump when returning to a saved entry. Long-distance verse jumps approach from nearby content, then animate the final scroll and briefly highlight the target. Arabic glyphs and list entries do not animate during ordinary scrolling.
- Persistent Calm, Balanced (default) and Expressive profiles, alongside the existing reduced-motion override and Android animator-off handling.
- Provider-sourced catalogs: all 79 EveryAyah entries and all 299 MP3Quran recording editions from the retrieved snapshots. The latter covers 244 provider reciter identities with Arabic/English names. IDs preserve distinct recordings, riwayat and quality variants. No name-based cross-provider merging is assumed.
- Searchable provider/type/variant/quality selection, bundled offline metadata, daily cached refresh and manual retry. Failed refreshes retain previously available metadata.
- MP3Quran recordings are explicitly full-Surah tracks. They start at the Surah beginning, use Surah previous/next controls, and do not expose fabricated verse timing or repeat-range controls. EveryAyah retains the existing verse queue and verse controls. Downloads use the selected recording's real track granularity.

## Data and boundaries

Catalog source URLs and retrieval details are in `app/src/main/assets/audio/NOTICE.txt`. Arabic Quran text and all verified Quran data assets were unchanged. EveryAyah's catalog does not state coverage for individual editions; the UI does not claim that every file is available. MP3Quran's explicit Surah lists are used for availability.

The current Quran Foundation API requires credentials and was not integrated. No credentials or reciter entries were fabricated. No audio files are bundled. New provider track availability depends on the provider; audible testing of those tracks was not performed as part of this update.

## Verification

Normal Gradle debug compilation and the existing data unit tests passed. Provider snapshots were checked for unique recording identities (79 EveryAyah paths, 299 MP3Quran edition IDs), valid HTTPS servers and Arabic-name joins. No Macrobenchmark or profiling suite was run for this update.

The debug APK was stream-installed successfully on the connected Nothing phone. A short UI smoke check confirmed Home → Quran, Surah/Juz switching, opening a Juz in the reader, phone-back return and the Appearance motion-profile controls. This was functional verification, not a frame-timing measurement or exhaustive animation acceptance test. Audible provider playback was not retested.

## Recitation follow and Settings composition

The reader follows actual Media3 media-item verse changes while playing, in Scroll or Pages mode. Follow recitation defaults on and persists; it can be changed in Reading controls or Settings. Scroll follow waits for the current fling/touch to finish and cancels when playback state or verse changes. It does not use fabricated timings for full-Surah recordings. Verse editions expose direct per-ayah play/pause controls and a contextual play-this-ayah-only action, using a one-verse queue.

Settings now uses keyed lazy sections rather than composing/measuring every expanded section in a vertical Scroll column before displaying its transition. Navigation transitions and reduced-motion handling remain intact. The connected device was confirmed to use Balanced motion with reduced motion off. Debug compilation and existing JVM data tests passed; no benchmark suite was run. Stream installation succeeded and a phone UI smoke check confirmed Settings navigation and the per-ayah play controls. Audio-follow behavior and frame pacing have not been comprehensively measured on-device.
