# Verification record

## Actions #86 — 1c50112

- JVM/Android-host tests: 86 passed, zero failures/errors/skips.
- Native Whisper integration: passed.
- Android API 30 x86_64 emulator: all three integration tests passed.
- Default and compatible decoder workflows: source playback, selected highlight,
  multi-source transitions, completion, changing video content and restart passed.
- Synthetic export: portrait display dimensions 1080 × 1920 after MP4 rotation,
  audio track present, expected duration and non-black decoded frames in both cuts.
- Development arm64 APK assembled; ZIP integrity and cached signing fingerprint passed.
- Rendered regular/compact home layouts, timeline fixture, motion/style controls
  and bold highlighted captions inspected.

Run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42661876282

The initial export failures were test assumptions: encoded width was treated as
display width, then the retriever's default sync-frame selection sampled an
intentional black fade-in frame. The checks now account for rotation and decode
near explicit interior timestamps. Assertions were retained for orientation,
dimensions, visible content, audio and duration.

## Limits

Synthetic fixtures are not the user's media. See the separate owner-reported
device results below. Two-hour processing, broad Taglish accuracy and model
performance remain unverified. Unselected checklist items are unconfirmed,
not failures.

Release signing and durable signing-key custody remain separate from the
development APK. The final release gate is not complete.

## Owner-reported device test — Build 87

Reported directly through the interactive checklist on 10 November 2026
(Asia/Manila). These are owner-reported results, separate from CI results.

- 26 of 28 checklist items were explicitly confirmed.
- Update installation, startup and existing projects passed.
- Local import, picture/audio, Play/Pause, Seek and playback without black
  screen/freeze/timeout passed on the user's Nubia Neo 2.
- Multiple videos, split, reorder and undo/redo passed.
- Auto captions, caption text edits, bold highlighting and subtitle timing passed.
- Project rename, independent drafts, recent-project selection and reopened edits passed.
- Approval gating, MP4 export, Gallery playback, framing/captions/audio,
  no unintended black frames/watermark and sharing passed.
- Local music import and volume controls were not selected. They remain
  unconfirmed; do not record a failure or assume music synchronization passed.
- Metadata-backup restore, two-hour processing and thermal/recovery behavior
  were not part of this submitted checklist.

Run 87: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42663064555

## Actions #88 — a4b958c

- 93 JVM/Android-host tests passed, with zero failures/errors/skips.
- All four Android API 30 x86_64 media tests passed.
- The new MP4 test saved/reloaded custom caption settings and verified pink text,
  cyan emphasis, left alignment and normalized vertical position in decoded output.
- Export bitmap scaling matches the preview's full-height centered 9:16 canvas.
  Host tests check portrait and landscape scale values. The pixel-position MP4
  assertion currently covers portrait output.
- Font selection changes rasterized glyphs; color/position controls, reset,
  legacy project defaults and invalid-position rejection passed host checks.
- Preview uses a bounded 360 × 640 caption bitmap; MP4 retains the full text canvas.
- Native Whisper integration and arm64 development APK build passed.
- Cached development signing fingerprint and APK integrity checks passed.
- The caption-controls layout fixture was rendered and inspected.

Run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42668895198

These new caption appearance controls have automated verification. They have not
yet been included in the owner's consolidated physical-device acceptance.
Build 87 device results remain valid for the tested baseline; unconfirmed music
controls, long-video/thermal behavior and release signing remain outstanding.

## Studio design upgrade — Actions #90 — 6c34902

- 99 JVM/Android-host tests passed: zero failures, errors or skipped tests.
- Five Android API 30 x86_64 integration tests passed, including real application
  launcher-icon loading, playback/restart/decoder recovery paths and MP4 rendering.
- Native Whisper integration passed; arm64 development APK assembly, cached
  signing fingerprint and APK integrity checks passed.
- Adaptive/monochrome launcher resources, dark native theme and splash resources
  compiled. The launcher icon was rendered and inspected on the host; the actual
  Android package-manager icon path passed separately on the emulator.
- Home navigation, direct-link draft retention, system Back, empty timeline
  recovery, editor actions and original-project preservation passed regression tests.
- Rendered home at regular and compact phone sizes, landscape home, editor
  fixture, caption workspace, campaign workspace, export empty state and brand
  icon were inspected. Fixtures are labeled and do not claim real user footage.
- The music track visualization reflects actual placement and active duration;
  it does not claim decoded waveform data.

Run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42710884850

APK: ClipForge-AI-Mobile-0.2.90-dev.apk
SHA-256: b1669a341b710eb5c92b38f28dcd8365d9db9687026eaba52cb8decf75d853a2

This is a consolidated development milestone. Physical acceptance of the new UI,
music import/volume checks, long-video/thermal behavior and production release
signing remain outstanding. The prior owner-reported Build 87 results are retained
as baseline evidence, not relabeled as tests of Build 90.
