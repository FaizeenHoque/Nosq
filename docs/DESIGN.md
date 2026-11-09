# NOS design system

A dedicated reading instrument: clear type, an editorial hierarchy and controls that describe their function. The Quran remains the central content.

- Palette: warm paper `#F4F3EF`, ink `#171718`, off-white surfaces `#FEFDF9`; dark base `#101011`, dark surface `#1C1C1E`. True-black mode uses `#000000`. Red is reserved for position, selection and active emphasis; it can be disabled.
- Typography: Geist for interface and translations, NDot 57 for display headings and counters, Amiri Quran exclusively for Quranic Arabic. Arabic direction is always RTL and its line height is independent of interface text.
- Scale: 4 / 8 / 12 / 16 / 20 / 24 / 28 / 32 dp. Default screen gutters are 24 dp, contained panels 20–24 dp, fine dividers create list structure. Icon controls retain native 48 dp touch targets.
- Geometry: 8 dp small radii, 16 dp list controls, 20 dp panels, 24 dp primary reading tile. Round playback controls provide a deliberate contrast to structured lists.
- Hierarchy: small uppercase technical labels, confident display type, quiet metadata. Inverted surfaces identify continue-reading and listening; most Surahs remain simple rows.
- Interaction: native ripple and press feedback, 180 ms navigation fades, native modal sheets and dialogs. Reduced motion disables navigation fades. Long-pressing a verse provides haptic feedback and contextual actions.
- Accessibility: system font scaling, semantic tab selection, descriptive icon actions, native switch/radio/slider semantics, sufficient monochrome contrast, user-adjustable reading sizes. Arabic diacritics are preserved and given generous leading. No essential meaning is carried by red alone.

The interpretation follows Nothing’s own explanation of OS 5.0: Geist for content clarity and selective dot-matrix identity. The product brief’s restrained surfaces take priority over adding frosted effects.
https://nothing.community/d/61774/10
