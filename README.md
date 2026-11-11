# ClipForge AI Mobile

Private, offline-first Android video editor for a personal clipping workflow.

Current installer: **v1.6.0, Build 136**. Exact verification results and download are in
[Studio release record](docs/RELEASE_1.6.0.md). [Workflow upgrade guide](docs/WORKFLOW_UPGRADE.md). Physical phone acceptance remains.
Version 1.0.1 remains withdrawn. See [Style pack guide](docs/STYLE_PACK.md) and
[Sound library guide](docs/SOUND_LIBRARY.md).
The v1 scope, installation instructions and limits are in [Quick start](docs/QUICK_START.md).
See [Release record](docs/FINAL_RELEASE.md), [verification history](docs/VERIFICATION.md)
and [release gates](docs/RELEASE_PLAN.md) for actual evidence.

## Studio upgrade

Music/SFX/Voice/Review tabs, 21 original music choices, per-cue review, optional speech cleanup, campaign presets, opening-text preview and device diagnostics. Verified: 188 unit/Compose tests, 16 Android media tests, 240 native speech windows and 28 checks on the actual signed APK. Physical Nubia acceptance remains pending.

## Editing

- Focused tool panels, recoverable autosaves, reusable editing presets and an explicitly started approved export queue.
- Editable caption in/out and word cues, estimated word-follow highlights, language hints and literal name/Taglish corrections.
- Explainable speech ranking and project-local campaign checklists at the export gate.

- Searchable sound library, audition, local favorites, MP3/audio import and individual Myinstants downloads. Downloaded files remain available offline.


- Offline Generate Hook suggestions from captions or a scene description; editable Funny, Curiosity and Story options.
- Eight portrait reference templates plus Original canvas, six new SDR video looks, ten caption styles, and 18 original synthesized sound effects with timed placement, volume controls, suggestions and project persistence.


- Original dark/cyan interface, custom vector icon, named projects and recent drafts.
- Timeline with up to 60 cuts, multiple source videos, split, trim, duplicate,
  reorder, snapping, exact timecodes and undo/redo.
- Constant 0.5–2x speed with natural voice pitch, fit/crop portrait or landscape
  1080p output, SDR color controls, zoom/motion keyframes and fades.
- Duration-preserving dip-to-black and zoom-cut boundaries.
- Original audio controls and one local music track with placement, trim,
  volume and fades. Preview and export use the same saved timing/settings.
- Editable timed captions, bold keyword highlighting, caption presets, font,
  size, color and position; hook text and SRT export.
- Review is required before every MP4 export. Edits/restoration revoke approval.
  Completed exports can open in Gallery or the Android share sheet.

## Offline AI and recovery

- Checksum-verified, user-initiated Whisper tiny model download; no account or
  paid service. Caption accuracy, especially Taglish/noise, requires review.
- First-30-second, selected-highlight, sampled-source and full-source modes.
  Up to 15 speech-ranked editable candidates; no claim of predicted virality.
- Foreground-service transcription uses bounded audio windows, native progress,
  reusable model weights, thermal pause checks and explicit pause/resume.
- Atomic completed-window checkpoints survive process interruption. Nothing
  automatically exports or resumes after a restart.
- Local autosave, independent named drafts, relinking and metadata backups.
  Backups omit media, model weights, permissions and signing keys.
- Local videos and direct HTTPS media-file imports; social watch-page links
  are not general video download links.

## Campaign records

Manual TikTok, Instagram, YouTube, Facebook and X submission tracking covers
status, views, audience percentages, required tags, notes, CPM, entered payout
thresholds/caps and actual paid amounts. Estimates are separate from payments.
The app does not verify campaign eligibility, geography, rights, engagement,
account standing or duplicate submissions. No media is uploaded automatically.

## Build and verification

Kotlin 2.0.21, Compose, Media3 1.5.1, AGP 8.7.3, Gradle 8.9, JDK 17,
whisper.cpp v1.7.6; Android 8+. Release packaging supports arm64-v8a and x86_64
with `-PuniversalRelease=true`; normal builds default to ARM64.

Actions runs host tests, Android media tests, real native inference and signed
APK acceptance checks using public/synthetic fixtures. Emulator and Linux
results do not establish phone runtime, heat/battery use or broad speech
accuracy. Exact counts and limitations belong to the linked release record.

The non-debuggable release retains the pinned original certificate. Automation
fails if the expected signing key is missing or differs. Its private owner
recovery backup is separate from source and project backups.

## Scope limits

Overlapping video layers/cross-dissolves, speed ramps, subject tracking,
background removal, advanced audio cleanup and device-specific HDR matching
remain future work. No cloud provider is included. Android can interrupt work
because of heat, memory or system limits; completed windows are recoverable.
Original media must remain accessible. This is a personal v1 release, with
public-store distribution and full CapCut feature parity outside its scope.
