# ClipForge AI 1.4.0 — Build 131

Prepared 11 November 2026 (Asia/Manila). Physical owner acceptance remains.

## Installer

- File: `ClipForge-AI-1.4.0-universal.apk`; 17568658 bytes.
- Version 1.4.0, version code 131; package `com.clipforge.mobile`.
- Tested application source: `2194bb7690ecd253c3ab4fc6a70fd8884dcb243b`.
- SHA-256: `f1855d33e289230efba675d19b3648e2725b3b0483aec9b6ae3fc723778f67c6`.
- Original certificate: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
- Non-debuggable signed release; ARM64 and x86_64. Install as an update without uninstalling.

[Download APK](https://chatgpt.com/library?file_id=libfile_6b37b0eaf9308191806a7297fbfc0b94&account_id=personal)

## Seven workflow upgrades

1. Focused tool panels show a cut selector and the relevant controls; the full timeline is expandable. Editing preview is slightly larger; Export/full Captions previews are compact so their controls have room.
2. Atomic autosave retains a previous validated generation, recovers missing/corrupt snapshots, and exposes explicit previous-save recovery. Startup discloses when a previous generation was used.
3. Editable source-time caption bounds, line-to-word splitting, word-follow highlighting, whole-word name/Taglish corrections and recognizer language hints (Auto/Taglish, English, Filipino).
4. Reusable local editing presets for caption/frame/look/motion/transition/hook-tone/SFX settings, without copying private source text or video.
5. Explainable highlight ranking considers speech coverage, sentence endings, short preceding context and long pauses.
6. An explicitly started export queue processes reviewed, frozen snapshots sequentially. Restored/interrupted items lose authorization. Loading for review creates a separate project so newer source edits remain intact.
7. Campaign brief inputs persist per project and appear at the export gate; human confirmations clear on restoration.

[Workflow guide](WORKFLOW_UPGRADE.md) explains operation and limits. Existing sounds, Myinstants controls, original signing identity and review-gated single export remain.

## Actual verification

[Build 131 core reports](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42966210010), [full installer run: 21 recorded passes](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42967811616), and [focused remaining checks: 4 passed](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42971423078).

| Check | Actual result |
| --- | --- |
| Unit / Android-host / Compose tests | 171 passed; zero failures/errors/skips |
| Android API 30 x86_64 media tests | 14 passed; zero failures/errors/skips |
| Native inference | 240/240 synthetic windows passed |
| Exact signed installer acceptance | 25 unique checks passed across two verification runs: 21 + 4, same unchanged APK |
| Installer integrity | Certificate, non-debuggable manifest, both ABIs, valid ZIP and checksum verified |

New host tests cover corrupt/missing-primary recovery, interrupted partial writes, preventing corrupt snapshot rotation, bounded word timing, manual overlap rejection, literal Taglish/name corrections, preset isolation and anchor remapping, queue snapshot limits and restart authorization, project-local campaign briefs, speech language checkpoints, ranking reasons, focused tool expansion, timing-editor interaction, trimmed/speed-adjusted caption seeks, full-width explicit approval and preview/export word-render equivalence.

The new Android queue test renders two independently approved synthetic snapshots sequentially, publishes both MP4s, decodes them, verifies independent red/monochrome video grades, and checks that the active word highlight moves in decoded output. Earlier real-media regressions cover captions, framing, color, original/imported sounds, music timing, speed/pitch, transitions and long-source seeks.

Signed acceptance retains the original update/project/preview/campaign/single-export/Share/JNI/import checks and adds preset save/apply, word-mode/timing controls, dismissible previous-save recovery, explicit queued export with full MP4 decode, and completed-queue persistence without automatic restart. Recovery corruption and interrupted-queue authorization are host-tested; dismissal of the recovery dialog is the actual-APK recovery UI check.

Native endurance repeats/pads public JFK speech into 240 consecutive 30-second windows, totaling 7,200 seconds. Linux CI wall time was 493.454 seconds; warm RSS 283956 KiB, final RSS 346800 KiB and peak RSS 346804 KiB. This is not unique two-hour footage, a phone benchmark or a broad speech-accuracy guarantee.

The update baseline is rebuilt from pinned Build 94 source for x86 and signed with the original identity, not the original ARM binary. The owner installer is tested unchanged. Public/synthetic fixtures only; no owner footage was exported or uploaded.

## Limits and phone acceptance

Word highlighting uses estimated boundaries within caption cues, not forced speech alignment. Split word cues can be manually retimed. Language hints do not guarantee Filipino/Taglish accuracy. Highlight scores are local heuristics, not predicted engagement or comprehensive visual understanding.

Keep ClipForge open while the queue renders. Leaving the app stops unfinished queue processing; there is no automatic background resume. Queue metadata and presets are not included in existing metadata backups. Source videos and imported/downloaded sound files must remain accessible. Preset limits: 20; queue limit: 10 snapshots.

Physical Nubia playback, caption legibility/synchronization, codecs, sound levels, personal export, Myinstants connectivity and heat/battery use remain owner checks. Emulator and Linux results do not establish physical-device acceptance.

Early v1.4 candidates were superseded during caption-seek and review-layout corrections. Build 129's signed workflow stopped after six passes because a partially clipped checkbox tap missed its target. Build 131 adds a compact Export/full-Captions preview and full-width explicit approval row; its original test locator still expected the old checkbox widget. The verification driver was corrected to tap the real labeled row.

The full immutable-APK verification then recorded 21 passes, including presets, word timing and the recovery dialog. It reported a false-negative queue timeout after scrolling past the completion message, although a queued MP4 was present in Gallery. The completion locator was corrected without changing the app. Only the remaining four checks were rerun: approved queue MP4 decode, completed-queue restart without auto-export, clean SAF import/restart, and fatal-exception checks. All four passed. The focused queue fixture additionally verifies 1080×1920 framing, five-second saved cuts/speed, audio and cyan-highlighted captions before fully decoding the MP4. The full run's crash log was also inspected and contained no ClipForge Java/native fatal exception.

These are combined passing checks on one SHA-256-identical installer, not a claim that the earlier failed workflow jobs turned green. Application source is the tested Build 131 commit; later commits change only verification scripts and release documentation.

[Previous 1.3.0 record](RELEASE_1_3_0.md). Version 1.0.1 remains withdrawn. No cloud AI provider, automatic export or new paid service is included. Advanced overlapping layers, speed ramps, tracking, background removal and public-store distribution remain outside this release.
