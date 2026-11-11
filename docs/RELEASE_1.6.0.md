# ClipForge AI Mobile 1.6.0 — Studio upgrade

Release: 1.6.0, version code 136. APK: ClipForge-AI-1.6.0-universal.apk.
Installer source: c78ceb0b4dde95a21decd892753306ea8525b161 on feature/studio-upgrade.
Original Studio implementation: e782cdaead830f738d45d00098315a6682e95f9b.

## Delivered features

- Audio split into Music, SFX, Voice and Review.
- 21 original procedural music choices: three variations in seven moods, plus local import/search/favorites and clip audition.
- Per-cue Keep/Replace/Remove controls and music replacement/removal. Replacing a cue cycles its built-in sound while preserving its timing/volume.
- Named campaign presets for framing, captions, hook tone, campaign rules and audio intensity, included in metadata backups. Applying clears source/rights confirmations and export approval.
- Editable opening/cover text, three local hook suggestions, and a first-three-seconds preview. Apply edits before previewing; this preview action does not limit exported text duration.
- Light/Balanced/Sharp preview detail, storage/RAM/battery diagnostics, and a privacy-filtered diagnostic share sheet.
- Optional speech cleanup (rumble reduction, soft noise gate, compression), used by preview and export and retained in project saves.
- Caption style, font, color, highlight, size, position/alignment, word-follow mode and timing controls.
- Review and explicit approval remain required before export; restoring/restarting never restores export authorization.

## Corrections in this continuation

The Studio branch previously ran only unit checks because it was omitted from the full Android build trigger. Added it to the release workflow. Campaign preset application now restores its saved hook tone. The release test harness now selects the SFX tab in the redesigned Audio workspace.

Prepared the release as 1.6.0 Build 136 while retaining the original signing identity. Added five focused regression tests covering 21 distinct music waveforms/IDs, audio cue review mutations, speech-cleanup persistence and PCM handling, rumble attenuation, and campaign-preset backup without private source media.

The Ubuntu package mirrors stalled FFmpeg setup before app tests could start. A bounded setup attempt timed out (exit 124). Verification then used standalone media test tools and a Python environment, without rebuilding or altering the signed APK. The immutable verifier checks the APK checksum before installation.

## Verified installer identity

- SHA-256: 90834cf61f87a9c548ef848c606ad5afbf1c58d8244a1304ce55325eebbe4eed
- Size: 17,683,342 bytes
- Signing certificate SHA-256: ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6
- Non-debuggable signed release; ARM64 and x86_64 native libraries included.
- Local download checksum matched the CI manifest; APK ZIP integrity passed.
- CI verified optimized Whisper/CPU inference flags in 12 compile commands.

## Actual automated results

Unit/Compose: 188 tests, 0 failures, 0 errors, 0 skipped. Both the focused run and release build ran these tests. New tests: StudioAudioReviewTest (1) and StudioUpgradeTest (4).

Native Whisper: 240/240 windows passed, including model reuse, cleanup, reload and progress callbacks. This was a two-hour-equivalent inference workload executed on the CI host, not a physical-phone thermal/endurance claim.

Android media integration: 16 tests, 0 failures, 0 errors, 0 skipped on an API 30 x86_64 emulator. This covers real decoder/recovery, portrait export, mixed audio, caption placement, pitch-preserving speed, color reset, transitions, two-hour-source window seeks, imported MP3 timing, looped music ducking, approved queues and portable-media recovery.

Actual signed APK: **28/28 acceptance checks passed** on the unchanged Build 136 installer. The artifact's result.json reports passed=true and error=null. It exercised the non-debuggable release, its packaged JNI speech engine, Android document picker and Gallery exports. Exported MP4s were inspected and decoded with FFmpeg/ffprobe.

| Check group | Result |
| --- | --- |
| Unit/Compose | 188 passed; 0 failed/errors/skipped |
| Android media integration | 16 passed; 0 failed/errors/skipped |
| Native Whisper endurance | 240/240 windows passed |
| Actual signed-APK acceptance | 28/28 checks passed |
| Signing, manifest, optimized native flags, APK checksum/ZIP integrity | Passed |
| Physical Nubia Neo 2 5G acceptance | Pending owner test |

### Signed-APK acceptance checklist

1. Pinned Build 94 source compatibility APK opens production-serialized synthetic project
2. Signed release updates Build 94 and rejects run-as debugging
3. Both named projects survive the signed update
4. Actual non-debuggable release renders changing video frames
5. Campaign record survives signed update
6. Export remains blocked before explicit approval
7. Actual release approval/export preserves saved speed, captions, framing and audio in Gallery MP4
8. Release opens Android share sheet without sending to a recipient
9. Process restart restores project and clears export approval
10. Actual signed APK decodes public English speech and produces editable captions through its packaged JNI/service
11. Actual signed APK generates and applies a contextual editable hook from captions
12. Signed Studio APK previews the opening text and stops at the three-second boundary
13. Actual signed APK applies reference frame and adds a timed sound effect with volume control
14. Actual signed APK searches, auditions and selects an expanded built-in sound
15. Actual signed APK exposes Myinstants search and imports a local MP3 as a timed effect
16. Generated hook and timed SFX survive restart while export still requires review
17. Imported effect and library favorite survive process restart
18. Actual signed APK applies a new video look, caption style and reference frame
19. New frame, caption style and video look survive restart without restoring export approval
20. Signed APK saves and reapplies a reusable editing preset
21. Signed APK enables word-follow captions and exposes editable word timing
22. Signed APK exposes explicit previous-autosave recovery without changing the edit on dismissal
23. Signed APK exports an explicitly reviewed queued snapshot and the MP4 fully decodes
24. Completed queue persists across restart and never restarts exports automatically
25. Clean signed import automatically drafts original music and SFX without exporting
26. Clean signed install imports through system picker and retains its new project after restart
27. Generated music persists across restart and never starts an export automatically
28. No ClipForge Java/native fatal exception in real release workflow

## Evidence

- [APK build, unit tests and native endurance](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43058346187). Its original emulator jobs encountered Ubuntu-mirror setup stalls; the successful immutable verifier below replaced that setup.
- [188-test focused unit/Compose run](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43058345947).
- [Successful immutable signed-APK acceptance and 16 Android media tests](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43061309695).
- Acceptance harness source: 5e86acb93bacfaf436dc712a0c2d8c33c043d522 on verify/studio-apk. CI/setup/documentation changes after the installer source do not alter the APK.
- [Studio branch](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/tree/feature/studio-upgrade).

## Install and use

[Download ClipForge-AI-1.6.0-universal.apk](https://chatgpt.com/library?file_id=libfile_be6cc5cb55fc8191b3375f32be7675ba&account_id=personal).
Install as an update over the existing app; keep it installed to retain projects and media grants. The signing identity is unchanged and version code 136 is newer than Build 134.

Use Audio → Music/SFX/Voice/Review. Open Text for editable cover text and its opening preview, More → Campaign tools for presets, and More → Device performance and diagnostics for preview detail/status. Review and approve each export.

## Limits and remaining physical validation

No unresolved implementation or automated-verification blocker remains for this Studio batch. These results do not establish physical Nubia performance, thermal behavior or every source/codec. On the phone, confirm update installation, local video preview, the new Audio pages/cleanup, caption changes, campaign preset save/apply and approved MP4 playback in Gallery.

Music is procedural instrumental audio. Mood/hook suggestions use local caption heuristics, not semantic scene understanding or predictions of engagement. Cleanup is optional filtering/gating/compression, not voice isolation. Word timings are estimates and English/Filipino/Taglish captions need review. Light preview does not reduce source decoding resolution or change export quality. Diagnostic sharing opens the Android chooser; sending remains the user's action.

Verified and prepared on 12 November 2026 (Asia/Manila).
