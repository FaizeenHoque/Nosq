# NOS motion system

The benchmarking phase was stopped on request. This update was validated with a normal Android build; no new benchmark or profiling runs were started.

## Rhythm

- Press: 110 ms.
- Fast feedback: 140 ms.
- Standard interaction: 240 ms.
- Major screen movement: 360 ms.
- Exit fade: 220 ms, overlapped with the incoming screen.
- Dashboard stagger: 35 ms, bounded to four intervals.

The entry curve is a precise deceleration (`0.16, 1, 0.3, 1`). Exit uses `0.4, 0, 1, 1`. There are no bouncing panels, character effects, particles or perpetual decorative animations.

## Destinations

`NavigationMotion.kt` separates primary tabs, hierarchical pages and modal player/details pages. Primary tabs use directional shared-axis motion. Detail pages slide and fade forward; phone-back reveals the previous opaque surface beneath the departing screen. Modal pages move vertically. Directions follow RTL layouts. Navigation Compose retains predictive-back handling.

Rapid tab requests coalesce to the latest destination and wait for the current entry to resume. Tab state and list positions are restored. The bottom indicator moves continuously; the Surah/Juz pager indicator follows swipe progress directly.

Calm, Balanced (default) and Expressive profiles persist in Appearance settings. Calm reduces movement; Expressive increases travel and adds restrained scale on interface-only screens. Reduced motion and Android animator-off settings override these profiles. Quranic glyphs are never transformed independently.

## Components

Dashboard interface sections have bounded entrances and statistic counters, while Quranic Arabic remains static. Reading progress is interpolated in the draw phase. Buttons, cards, Surah rows, tabs, chips and switches share restrained press feedback and system-respecting selection haptics.

The reader animates chrome, translations, bookmark icons, background selection and footer mode controls. Page content does not morph or animate Arabic glyphs. Verse actions transition between action, note, bookmark-confirmation and Tafsir-source panels inside a native modal sheet. Published commentary and existing verse-details navigation remain available.

Player controls crossfade their states, the mini-player expands through the screen transition, and reciter labels transition independently of Arabic text. Activity dots represent playback/buffering state rather than invented audio amplitudes. Settings retain native draggable sliders and switches, with animated value labels, disclosures, theme reveal and dialogs.

Theme changes update colors once and animate a graphics layer, rather than rebuilding the palette on each frame. Motion work uses scoped effects and stops when components leave composition. Full-list placement animations are avoided.

The app’s reduced-motion preference and Android’s animator-off setting disable custom transformations and staggers. Native platform components also follow system animation settings. Statistic animation exposes the final value to accessibility services, avoiding announcements of intermediate counts. Verse references are always exact and never count through intermediate values.
