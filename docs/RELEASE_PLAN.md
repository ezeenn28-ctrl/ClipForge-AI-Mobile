# ClipForge AI release plan

Approved by the owner on 10 November 2026 (Asia/Manila).

## Product decisions

- Personal Android app first. Public distribution is a separate milestone.
- Original dark charcoal/cyan UI based on the approved three-screen concept.
- Local files and direct HTTPS video-file links first. Social page URLs are not promised.
- Zero paid dependencies, no required account, no external analytics.
- Offline-first transcription; a future cloud provider requires permission for each upload.
- Editable AI shortlist, up to 15 candidates, default 15–30 seconds.
- Review and explicit approval before export. Restoring projects never restores approval.
- Target 1080 × 1920 MP4 with no added watermark.
- Retain application identity, existing projects and signing identity.
- Routine technical changes and GitHub commits are authorized. Consolidate phone testing.

## Verification baseline

At upstream fdc0848, Actions #84 built the arm64 development APK and passed JVM
and native speech checks. Both Android playback tests passed. The export test
failed because it compared encoded width directly against portrait display width.
Actions #86 passed rotation-aware export assertions and exact-frame decoding checks on Android.

Existing code contains timeline editing, multiple videos, one local music track,
subtitle generation/correction, sampled speech highlight ranking, autosave,
manual campaign tracking, clip motion and fades through black. These statements
describe implementation, not physical-device acceptance.

## Ordered release gates

1. [x] Baseline tests and synthetic Android playback/export tests pass (Actions #86).
2. [x] Named projects, autosave, reopen and legacy migration passed automated checks; reopen/autosave also confirmed on phone. Metadata backup passed host tests; device restore remains pending.
3. [x] Dark/cyan theme, original icon, caption controls and regular/compact/landscape layouts passed automated checks and internal visual review in Builds 88–90. Build 94 owner-reported editor checks passed; v1 retains the reviewed visual system.
4. [x] Core import/edit/audio/framing passed owner-reported baseline checks and automated regression checks; v1 boundary transitions passed native MP4 checks.
5. [x] Caption rendering/timing passed automated checks; the owner confirmed Build 94 synchronized captions, persistent settings and MP4 matching preview/captions/audio.
6. [x] Up to 15 bounded, nonoverlapping, unapproved candidates and explicit review-before-export passed workflow tests.
7. [x] Two-hour/240-window recovery and real bounded audio seeks at start/middle/end passed automated checks. Full-session phone runtime/thermal behavior remains an explicitly unmeasured, device-dependent limit.
8. [x] Campaign UI/status/view/platform editing and metadata restore passed with 54 synthetic records; actual payments remain separate from estimates and unreadable data is preserved.
9. [x] Owner-reported Nubia Neo 2 acceptance: Build 87 baseline and all eight Build 94 checklist items passed. Build 94 includes update/project retention, playback/seek, speed/pitch, music import/volume, captions, color/reset/undo, settings persistence and matching MP4 output.
10. [x] Non-debuggable release retains the pinned original certificate. Owner-only encrypted key transport, decryption, certificate match and private-key signing were verified; a private owner backup is retained.
11. [x] Signed personal 1.0.0 arm64 APK, SHA-256, quick start/scope/limits and private signing backup are prepared for owner delivery; see FINAL_RELEASE.md.

A gate is checked only after evidence is recorded. Building a debug APK does not
complete release signing or establish device acceptance.

## Core editing batch after Build 90

Per-clip constant speed (0.5–2x, original-audio pitch preserved) and SDR brightness,
contrast, saturation and warmth are implemented and passed automated verification in Build 94; the owner confirmed all eight Build 94 phone-check items on 10 November 2026.
Source trim/caption timestamps remain immutable; output duration, seeking, fades,
thumbnail sampling and music placement use explicit source/output clock conversion.
Sliders commit one undo step per drag. Legacy projects default to 1x/neutral color.
Overlapping transitions, speed ramps and HDR color matching remain outside this batch.

## Advanced follow-up

Background removal, subject tracking, audio cleanup and advanced speed effects
follow the stable core. Each requires working preview/export behavior and
device performance evidence before being marked complete.

## Consolidated phone checklist

- Install/update without uninstalling; existing drafts remain accessible.
- Import two local videos. Play, split, reorder, seek and restart the sequence.
- Add local music. Check original/music volume, fades and synchronization.
- Generate and correct captions. Check bold highlight and caption timing.
- Create a second project, reopen the first, rename it, close/reopen the app.
- Save and restore a metadata backup; keep original video/music files available.
- Approve and export a 1080p vertical clip; view it in Gallery and test Share.
- Interrupt a longer transcription, reopen the app, explicitly resume.
- Report any freeze, black frame, decoder error or mismatch before final sign-off.

## Development reporting

Report commits, actual test outcomes, unresolved issues and whether an APK is a
development build or signed release. Do not claim background work continues
after the active session ends, or promise unlimited processing on the phone.

## Version 1 release freeze

The personal Android v1 scope is frozen at the supported functions listed in
QUICK_START.md. This batch adds duration-preserving dip-to-black and zoom-cut
boundaries, Facebook tracking, end-to-end campaign checks, two-hour checkpoint
recovery and far-seek checks, a non-debuggable release variant, immutable signing
identity checks, encrypted signing-key transport and an owner recovery backup.
These passed Actions #96; CI outcomes and recovered-key verification are recorded in FINAL_RELEASE.md and VERIFICATION.md. Overlapping cross-dissolves and advanced follow-up
features remain explicitly outside v1; no full CapCut feature parity is claimed.

## Current candidate — v1.0.2 Build 116

- [x] Fix the reproduced import-time playback race; consistent timeline snapshots.
- [x] 118 host/unit tests and 9 Android media tests, zero failures/errors/skips.
- [x] Full 240-window real native endurance workload.
- [x] All 12 exact signed-APK workflow checks, including real auto captions,
  clean picker import, persistence, approved Gallery MP4 and Share.
- [x] Original-key, non-debuggable universal installer, checksum and durable delivery.
- [x] Withdraw v1.0.1 after its actual import crash; retain failed-attempt evidence.
- [ ] Fresh owner test of v1.0.2 on the Nubia Neo 2; real media, long-session
  pause/resume, codec behavior, heat/battery and Taglish accuracy.

All automated results are fresh from Actions 116. Earlier phone results remain
baseline evidence. See FINAL_RELEASE.md for exact scope, source and checksum.
