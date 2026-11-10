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

## Speed and color controls — Actions #94 — e95ec58

- 107 JVM/Android-host tests passed: zero failures, errors or skipped tests.
- All seven Android API 30 x86_64 integration tests passed.
- Mixed-speed synthetic export used a 2-second source span at 2x followed by
  another 2-second span at 0.5x, producing approximately five seconds of output.
  Decoded frames remained visible and caption on/off timing matched both speeds.
- Decoded original-audio pitch stayed approximately 440 Hz at both speeds.
  The separate 880 Hz music fixture entered at its 4.5-second output placement.
- Actual window pixels verified preview color grading and neutral reset without
  tinting the editor's black letterbox canvas. Encoded MP4 pixels verified SDR
  brightness/warmth values and independently colored caption text/highlights.
- Host checks covered mixed-speed seek/split/trim clocks, output-time fades,
  source-timed captions, speed-only updates without playlist reset, transition
  playback parameters, one undo step per color drag, reset, persisted settings
  and neutral/1x defaults for old projects.
- Native Whisper integration, arm64 development APK assembly, cached development
  signing fingerprint and APK ZIP integrity checks passed. The color-controls
  layout was rendered and inspected.
- Earlier runs exposed two test-host setup mistakes (missing compact toolbar and
  a dark window instead of the editor's black canvas), corrected before this run.
  External Maven HTTP 429 and interrupted emulator-image downloads were separate
  infrastructure failures. CI now prepares a checksum-verified pinned Robolectric
  SDK for offline test resolution and caches the Android image with bounded retries.

Run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42750019885

APK: ClipForge-AI-Mobile-0.2.94-dev.apk
SHA-256: e460e34a342c41daa27aa500eb43899366ba54cb59ddb98e356c98bc20d56034

This remains a development milestone. These are synthetic/emulator checks, not
new acceptance results from the owner's Nubia Neo 2. Consolidated phone acceptance,
two-hour processing/thermal evidence, overlapping transitions and production signing
remain outstanding. SDR color checks do not establish HDR matching. Export still
requires review/approval; no owner footage was automatically rendered for testing.

## Owner-reported Build 94 phone acceptance — 10 November 2026

The owner selected every item in the two Build 94 device-check groups.
These are owner-reported results on the established Nubia Neo 2 device, separate
from the seven emulator tests above.

1. Installed as an update with existing projects retained.
2. Preview, Play/Pause and Seek worked.
3. 0.5x/2x playback worked with natural voice pitch.
4. Local music import and volume worked.
5. Captions remained synchronized.
6. Color controls, Reset and Undo worked.
7. Settings persisted after closing/reopening.
8. Exported MP4 matched preview, captions and audio.

No blocker was reported in this checklist. This resolves the previously
unconfirmed music-import/volume checks and the Build 94 speed/color acceptance
items. It does not establish new results for two-hour transcription/thermal
behavior, interruption/resume, metadata-backup restore, Campaign Mode,
overlapping transitions, HDR color matching or production signing.

## Personal release 1.0.0 — Actions #96 — f62f7e7

- 116 host/unit tests and all 9 Android media tests passed, zero failures/errors/skips;
  native Whisper integration passed.
- Saved dip-to-black/zoom-cut transitions, split/undo semantics, real boundary
  MP4 pixels and unchanged duration passed.
- Real two-hour AAC/video decoding passed at 0, 3,600,000 and 7,199,000 ms.
  Requested one-second WAVs stayed bounded and retained their expected duration.
- The 240-window runner test used synthetic transcript results. Durable process
  loss, metadata backup/restore and resume skipped 17 completed units and processed
  exactly 223 remaining windows without duplicate cues.
- Campaign UI/edit/restore passed with 54 synthetic records; unreadable data was
  preserved and Facebook is supported. Paid amounts remain separate from estimates.
- The first release-candidate run exposed negative-AAC-PTS decoding and native
  Release/RelWithDebInfo optimization gaps. EOF now uses sample-track availability,
  and all native configurations use -O3. Assertions were retained and passed.
- Release APK is non-debuggable, signed with the pinned Build 94 certificate,
  versionCode 96. Signature, ZIP integrity, native optimization and SHA-256 passed.
- Owner signing-key decryption, certificate match and CSR signature were verified.
  A private owner backup is retained; no private signing/recovery key was committed.
- FINAL_RELEASE.md and QUICK_START.md document the exact v1 scope and limits.
  Prior phone reports are retained separately from current emulator results.

Run: https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42756941805
APK: ClipForge-AI-1.0.0-arm64.apk
SHA-256: 8d11e3ee2cb06e093ac938f162826c7178698b58688020b7ce676ba83acc16d3

The documented personal v1 release is complete. Full-session phone thermal/runtime
and transcription-quality benchmarks remain unmeasured. Public distribution and
advanced additions are separate milestones; no unlimited-processing or full
CapCut feature-parity claim is made.

## v1.0.1 Build 97 — consolidated candidate verification

See FINAL_RELEASE.md for the exact source, installer checksum, signing identity,
all outcomes, errors and explicit pending checks. Build 97 passed 116 host/unit
tests and 240 real native windows. Retry 100 passed all nine media tests.
Phase 103 recorded nine completed actual-APK update/export checks before the
later system-picker failure. Subsequent picker attempts, including 110, did not
complete packaged-JNI captions or clean import. Failed whole jobs are not counted
as fully passed. Production app code and installer bytes stayed unchanged.

Native input was the public JFK sample padded/repeated to two hours. Linux CI
wall time: 765.529 seconds; warm RSS: 308,980 KiB; final/peak RSS: 346,988 KiB.
This is not phone runtime, heat/battery or broad recognition accuracy evidence.

The default required acceptance job covers update/export; the unresolved
DocumentsUI speech/import phase remains available through the driver for further
diagnosis, and is an explicit physical-owner acceptance check. Existing owner
Build 94 results are preserved as baseline evidence.

## v1.0.2 Build 116 — full acceptance and import-crash fix

Version 1.0.1 is withdrawn. Typed-finger DocumentsUI automation got past the
old picker blocker and reproduced a real intermittent main-thread import crash:
a pending playback tick read an empty timeline while using an old active flag.
The `coerceIn(0, -1)` exception was at MainActivity.kt:890 in the old APK.

The fix takes one current immutable timeline snapshot for the tick and consistent
snapshots for seek and preview reconciliation. Empty lists return no player
position. Two regression tests cover empty/replacement sequences, stale indices
and speed conversion.

Fresh [Build 116](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42795867454),
source `21ceeb0fe10bc55f380da064b67df7aabb0a5c6f`, passed:
- 118 host/unit tests and all 9 Android API 30 media tests, zero failures/errors/skips.
- All 240 real native inference windows, including model reload and progress/cues.
- All 12 exact signed-APK checks: legacy-source compatibility update, projects,
  campaign, moving frames, review gate, Gallery MP4 media checks, Share,
  approval clearing, actual packaged-JNI English captions, clean system-picker
  import, saved-project restart and Java/native fatal-log checking.
- Original signing identity, non-debuggable manifest, optimized native kernels,
  both ABI libraries, ZIP integrity and pinned SHA-256.

Linux native endurance used repeated/padded JFK speech totaling two hours;
wall time 472.486 seconds, warm RSS 281,848 KiB, final/peak RSS 348,204 KiB.
This is not a phone benchmark or unique two-hour source.

APK SHA-256: `d680accf98773c81ff98f108f81dc7c1d80d779eedd468f8b62b460de7b8614b`.
See FINAL_RELEASE.md for current delivery, exact test scope and physical limits.
The previously pending packaged-engine and clean-import automated checks are
now passed. Fresh physical Nubia acceptance remains pending.

The test-only finger injector is outside the APK. The baseline update APK was
rebuilt from pinned Build 94 source for x86 compatibility, not the original
ARM64 binary. No owner media was used or automatically exported.
