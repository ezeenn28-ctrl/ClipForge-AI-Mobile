# ClipForge AI 1.5.0 — Build 134

Newly imported timeline cuts now receive a conservative local music and sound-effects draft. Existing projects keep manual audio until Auto Audio is enabled. Every MP4 still requires review and explicit approval.

## Changes

- Seven original instrumental moods; music search, favorites, provenance and audition with the clip.
- Automatic music/SFX suggestions using captions, audio activity, cut boundaries and a visual-change proxy; preserve existing music/SFX or explicitly replace them; Undo Auto Audio.
- Fit/loop music, fades, dialogue-activity ducking, per-cut voice leveling/soft limiting, and estimated mix-headroom warnings.
- Beat/onset guides with optional timeline snapping; silence suggestions with explicit removal; caption timing/readability checks.
- Preview-only safe-area guides and before/after look/mix comparison; batch styling from saved presets.
- Posting caption/tag package for the matching approved export; storage estimate and safe old temporary-audio cleanup.
- Backup now includes presets, queued drafts and audio catalogs. Optional portable backup includes accessible media, with a 1 GB limit; restored queued drafts require fresh review.

## Actual verification

Source commit: `94e390e3f588e89d097625983767ffef1aebe969`.

[Build and integration run](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43002483783).

| Check | Result |
|---|---|
| Unit and Compose tests | 183 passed; 0 failures/errors/skipped |
| Android media integration, API 30 x86_64 emulator | 16 passed; 0 failures/errors/skipped |
| Native Whisper integration/endurance | Passed; 240 repeated public/synthetic fixture windows |
| Actual signed APK UI acceptance | First run: 11 checks passed, then navigation timeout before SFX; immutable retry ongoing |
| Original signing identity and non-debuggable manifest | Passed |
| Downloaded APK SHA-256 and ZIP integrity | Passed |

The media tests include real MP4 decoding/export, portrait captions/styles, speed/pitch, mixed imported SFX/music, approved queue rendering, looped music with ducking across repeats, and portable restoration after deleting original fixture media. Long-source testing uses a synthetic two-hour file and repeated speech fixtures; it is not a two-hour phone performance guarantee.

Physical testing of this update on the owner's Nubia Neo 2 5G remains pending. The owner's earlier “All test works” applies to the previous release.

## Installer

- Filename: `ClipForge-AI-1.5.0-universal.apk`
- Size: 17,650,578 bytes
- APK SHA-256: `10e84bbb4bfaf679133e313c1290333d47f247a819b32f3c27bee9d66a2c82f3`
- Signing certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`
- ABIs: arm64-v8a and x86_64. Install as an update; keep the installed app and project media.

## Practical limits

Mood is a local heuristic, not full semantic scene recognition. Cuts longer than 90 seconds sample beginning/middle/end windows; silence deletion is disabled for sampled analysis. Imported music onset analysis covers the first 90 seconds of its trim and provides approximate guides. Original beds have short seam fades; listen to imported loop joins. Caption checks flag readability/timing, not spelling or transcription correctness. Safe areas are approximate preview guides. Portable backup omits model weights and fails clearly when media access is missing. No automatic community-sound downloads, cloud uploads or automatic MP4 exports were added.
