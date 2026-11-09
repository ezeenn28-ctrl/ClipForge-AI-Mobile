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
