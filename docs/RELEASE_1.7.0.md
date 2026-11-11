# ClipForge AI Mobile 1.7.0 — precision editor release and actual tests

Build 142. Installer source: b62e9cb101b8be829790b1fb744ff3b1c48d63ac. Branch: feature/editor-precision.
Base: verified Studio 1.6.0 Build 136. Prepared on 12 November 2026 (Asia/Manila).

## The ten editor upgrades

| Requested area | Delivered behavior |
| --- | --- |
| Larger preview | Large-preview switch and independent tools/timeline collapse controls |
| Timeline gestures | Two-finger anchored zoom; ruler scrubbing; snapping to cuts, captions, music beats and SFX |
| Frame controls | Previous/next actual source video sample; millisecond clock; trim/speed mapping and rounding regression |
| Separate lanes | Selectable video/caption/voice/music/SFX rows; selected-cut lane locks and mute; whole-bed music lock/mute |
| Contextual tools | Tapping a lane selects its cut and corresponding tools; focused caption controls apply per cut |
| Direct text editing | Opening/caption drag, corner resizing, alignment/safe-area guides and bounded handles; persisted shared preview/export placement |
| Waveform and meters | Explicit decoded voice waveform analysis and separate decoded PCM voice/music/SFX peak meters |
| Undo, redo, reset | Named actions, up to 30 cut/music history steps, category reset; one step for a completed text gesture |
| Quick review | Loop selected cut, two-second pre-roll, before/after and precise seeks; preview-only behavior |
| Settings transfer | Selective caption styling/placement, color, video effects/motion and voice settings; preserves target media/trim/speech text |

The batch also fixes per-cut caption style persistence, captures appearance when locking captions, retains resize handles inside the preview, and preserves editing state through metadata/portable backups. Review and explicit approval remain required for export.

## Installer identity

- File: ClipForge-AI-1.7.0-universal.apk
- Version: 1.7.0; version code: 142
- Bytes: 17,748,882
- SHA-256: 8874bc066c78ae124a18d1472869312ba8b37ce3507fca2ff551b0915235a6f2
- Signing certificate SHA-256: ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6
- Non-debuggable release; ARM64 and x86_64 packaged native libraries.
- CI verified the original signing identity and optimized Whisper/CPU inference flags.
- Local download SHA-256 matched the CI manifest and APK ZIP integrity passed.
- The immutable verifier validates version, code, source commit and checksum before installation. It does not rebuild the release APK.

## Actual automated results

| Check group | Result |
| --- | --- |
| Unit / Compose UI | 203 passed; 0 failures, errors or skipped |
| Android media integration | 18 passed; 0 failures, errors or skipped |
| Native Whisper endurance | 240/240 windows passed, including reload |
| Actual non-debuggable signed APK | 29/29 passed; result.json passed=true, error=null |
| Physical Nubia Neo 2 5G acceptance | Pending owner test |

The 203-test suite includes ten precision-editor model tests and five UI/rendering tests. It checks track locks, source-preserving selective paste, mute behavior, labeled/bounded history, project round-trip, music/cut undo, changed-speed frame rounding, two-finger pinch, direct drag/resize at the preview edge, contextual caption selection and moved/scaled caption pixels.

The 18 Android media tests run real decoding and encoded exports on an API 30 x86_64 emulator. New tests verify adjacent video timestamps (including speed-rounded clocks), actual decoded waveform peaks, and per-cut caption style/position/scale after project save/reopen and MP4 encoding. Existing tests retain coverage for mixed audio, imported MP3 timing, ducked/looped music, pitch-preserving speeds, caption timing, color, transitions, backup recovery, preview recovery and approved queues.

The native test ran 240 30-second-equivalent windows on the CI host; completion took 826.8 seconds on that runner. This is an inference-workload result, not a phone thermal/endurance or real-time-processing promise.

### Actual signed-APK acceptance checks

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
12. Signed precision editor steps forward/back using actual video timestamps and an exact timecode
13. Signed precision editor directly moves caption text and records a labeled undo action
14. Actual signed APK applies reference frame and adds a timed sound effect with volume control
15. Actual signed APK searches, auditions and selects an expanded built-in sound
16. Actual signed APK exposes Myinstants search and imports a local MP3 as a timed effect
17. Generated hook and timed SFX survive restart while export still requires review
18. Imported effect and library favorite survive process restart
19. Actual signed APK applies a new video look, caption style and reference frame
20. New frame, caption style and video look survive restart without restoring export approval
21. Signed APK saves and reapplies a reusable editing preset
22. Signed APK enables word-follow captions and exposes editable word timing
23. Signed APK exposes explicit previous-autosave recovery without changing the edit on dismissal
24. Signed APK exports an explicitly reviewed queued snapshot and the MP4 fully decodes
25. Completed queue persists across restart and never restarts exports automatically
26. Clean signed import automatically drafts original music and SFX without exporting
27. Clean signed install imports through system picker and retains its new project after restart
28. Generated music persists across restart and never starts an export automatically
29. No ClipForge Java/native fatal exception in real release workflow

## Evidence and corrections

- [Final 203-test focused suite](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43071958015).
- [Build 142, signing, manifest, optimized native code and endurance](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43071958042).
- [Immutable APK verification, repeated unit suite and 18 Android media tests](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/43072841649).
- Verification harness: faa64e29ac1cc31c774c9d629bdccc0bde0cf164 on verify/editor-apk.
- Earlier compile failure: missing horizontalScroll import, corrected.
- Original media setup failed before app testing when an FFmpeg server returned HTML. A checksum-command typo in the replacement setup was then corrected. The successful verifier uses the fixed helper and a SHA-256-pinned dated FFmpeg release. These CI-tool changes do not alter the installer.
- [Editor source branch](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/tree/feature/editor-precision).

## Install

[Download ClipForge-AI-1.7.0-universal.apk](https://chatgpt.com/library?file_id=libfile_c21edf9d9f9881918f6235b27523afbe&account_id=personal).
Install as an update over the existing app; keep it installed to retain saved projects and media grants. Signing identity is unchanged.

## Practical limits and remaining phone checks

No unresolved implementation or automated-verification blocker remains for this editor batch.

No source/trim/speech text is pasted by the settings clipboard. Track locks/mute are cut-scoped except music, which is one bed. Clipboard, waveform cache, history and preview-only controls are transient; the saved edit retains its settings, and restart clears export approval.

Waveform analysis covers up to 90 seconds; longer cuts sample start/middle/end windows and leave unmeasured gaps. The waveform is for selected source audio. Audio meters are separate decoded PCM peaks after lane volume, not summed master loudness. Imported beat markers and word timings are estimates. Frame seeking uses source timestamps and millisecond player seeks; variable-rate/codec-specific phone behavior still requires validation.

Confirm update installation, local playback, pinch/seek, drag/resize, mute/lock, waveform, save/reopen and approved MP4 playback on the Nubia. Physical phone validation and performance/thermal measurements have not been claimed.

## Usage guide


### Preview and workspace

Import a local video or continue a recent project. The focused editor keeps its preview above the tool pane.
The horizontal row below playback contains **Precision review tools**, **Large preview**, **Hide tools** and **Collapse timeline**. Swipe the row if a control is offscreen. Preview controls are transient and do not change the export.

Use **Show tools** or the bottom toolbar to reopen editing controls. **Show timeline** can reveal lanes from another tool panel. Wide layouts retain independent preview and tool scrolling.

### Timeline and contextual tools

Pinch with two fingers on the timeline to change zoom. Drag the ruler with one finger to scrub. Snapping includes cut boundaries, caption boundaries, music beat markers and SFX boundaries. Beat markers remain approximate for imported music.

Tap a video cut for Edit, a caption block for Captions, original audio for Voice, music for Music, or an SFX block for SFX. The selected item and its tool pane follow that choice.

### Precise review

Open **Precision review tools** for **Previous frame**, **Next frame**, a millisecond timecode, **Loop selected cut**, **Play 2s before edit** and **Before / after**. Swipe this row to see the remaining controls.

Frame controls consult actual video sample timestamps, map through source trim/speed and request precise player seeks. Container/codec timing still needs phone validation. Loop and comparison apply only to preview; Export renders the edit. Comparison retains its current cuts and speed.

### Track mute and lock

Open **Track mute / lock**. Video, captions, voice and SFX controls apply to the selected cut. Music controls apply to the whole bed. Video lock protects structural/video changes; caption, voice and SFX locks protect their lane settings. Caption appearance is captured when locking it.

Mute keeps the source media and timings. Caption mute hides hook/subtitle text, voice mute silences that cut, and SFX mute omits its timed cues. Music mute silences the bed. Unlock before editing a locked lane. Undo/redo can restore a prior lock state.

### Direct text placement and sizing

Open **Text**, choose **Move opening text** or **Move captions**, then drag the outlined guide in the preview. Drag its corner handle to resize. The guide remains inside the preview at the edges. Center and safe-area guides assist alignment.

The displayed caption offset/scale helps confirm a change. Each completed drag/resize creates one labeled undo step. Opening text and body captions keep independent placement/scales. Position and scale are saved per cut and use the same renderer in preview and MP4 export.

**Captions** style/font/color/position controls affect the selected cut in the focused editor. Advanced source tools retain their project-default appearance controls.

### Waveform and meters

Open **Audio → Analyze selected audio waveform**. Reopen the timeline to see decoded peaks in the selected source-audio lane.

Cuts up to 90 seconds are analyzed across their range. Longer cuts sample up to 30 seconds at the start, middle and end; empty intervals are unmeasured. Derived peaks are held in a bounded temporary cache, not stored as project media.

Open **Audio output meters** in Audio. Voice, music and SFX show decoded PCM peak levels after lane volume. They are separate players, not summed master loudness. Check the combined mix by listening.

### Undo, redo, reset and settings transfer

The history controls identify their action, such as **Undo Trim**, **Undo Text placement**, **Undo Music**, or **Redo Caption style**. History retains up to 30 cut/music steps. Runtime history is not restored after restarting the app; the saved edit is retained.

In **More → Copy settings / reset selected**, select Captions, Color, Effects and/or Audio. Copy the selected cut, then paste into the selected cut or all cuts. Captions transfers styling/placement; Audio transfers voice gain/cleanup/volume/fades. Target source media, trim, hook and speech text are preserved. Locks reject prohibited changes as one transaction.

Reset only the selected category. Resetting caption style/placement preserves its words. The internal settings clipboard is transient.

### Export and phone check

Review the edited preview, open Export, and explicitly approve the result. None of the new preview, analysis or editing controls starts an export automatically.

Install the signed update over the existing app. Keep the app installed to retain projects and media access. On the Nubia, confirm local import, pinch/seek, drag/resize, lane mute/lock, waveform, caption save/reopen, and approved MP4 playback in Gallery. Automated emulator results do not establish phone thermals or every source/codec.
