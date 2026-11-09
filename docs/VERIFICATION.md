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

Synthetic fixtures are not the user's media. These checks do not verify the
Nubia Neo 2 decoder repair, perceptual audio sync, Taglish accuracy, two-hour
processing, model performance, or Gallery/share integration on the phone.

Release signing and durable signing-key custody remain separate from the
development APK. The final release gate is not complete.
