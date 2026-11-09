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
2. [ ] Named projects, autosave, reopen, legacy migration and metadata backup pass.
3. [ ] Approved theme, readable caption styles and phone layout pass visual review.
4. [ ] Import, trim, split, reorder, undo/redo, audio and framing pass end to end.
5. [ ] Captions retain timing/style across preview, saves and MP4 export.
6. [ ] Up to 15 editable highlight candidates; approval required for every render.
7. [ ] Long-video interruption/resume works within documented storage/thermal limits.
8. [ ] Campaign checks and manual payout records retain correct states.
9. [ ] Consolidated Nubia Neo 2 device test has no blocking playback/export failure.
10. [ ] Release signing is configured securely without changing the update identity.
11. [ ] Signed release APK, checksum, quick-start guide and actual limitations delivered.

A gate is checked only after evidence is recorded. Building a debug APK does not
complete release signing or establish device acceptance.

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
