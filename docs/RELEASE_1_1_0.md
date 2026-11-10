# ClipForge AI 1.1.0 — Build 121

Delivery record: 11 November 2026 (Asia/Manila). Physical owner acceptance remains.

## Installer and source

- Filename: `ClipForge-AI-1.1.0-universal.apk`; 17,453,966 bytes.
- Version 1.1.0, version code 121; package `com.clipforge.mobile`.
- Non-debuggable signed release; ARM64 and x86_64.
- Source: `458d5bf7418c266c93d9912d113febd2dc1dfbf9`.
- SHA-256: `9ffee78233d9479080f20c98fdb82b85aa20f06b4e38a120fda19f1ce8cf644d`.
- Signing certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
- Install as an update without uninstalling; the original signing identity is retained.

[Download APK](https://chatgpt.com/library?file_id=libfile_609eddc84a148191af364613f739cee8&account_id=personal)

## Upgrade

- Offline editable hook suggestions from captions or a scene description: Funny, Curiosity and Story tones.
- Two white portrait reference frames: square crop and 4:3 crop, with header hook text and bold highlighted captions.
- Six original procedural sound effects: Whoosh, Pop, Impact, Metal chime, Boing and Scratch.
- Timed placement, per-effect volume, move/remove controls, caption-based suggestions, timeline markers, undo/redo and saved-project support.
- Source-time SFX anchors follow split, trim, reorder and constant speed changes; their pitch remains unchanged.
- Fixed a short-effect preview attack issue and clipped native preview video to its portrait canvas. A real screenshot check now rejects horizontal picture overflow.
- Review approval remains required before export.

See [Reference-style guide](REFERENCE_STYLE.md).

## Verification

[Build 121 verification](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42830932957).

| Check | Actual result |
| --- | --- |
| Host and Android-host unit tests | 133 passed; zero failures/errors/skips |
| Android API 30 x86_64 media integration tests | 10 passed; zero failures/errors/skips |
| Native inference | 240/240 windows passed |
| Exact signed installer workflow | 15 checks passed; no Java/native fatal exception |
| Release integrity and identity | Original certificate, non-debuggable manifest, both ABIs and SHA-256 verified |

The new media test decodes a real exported MP4: white frame bands and hook text remain before/between subtitle cues; a pop is audible at 1 second and impact at 2.5 seconds, with silent intervals when original audio is muted.

Signed-installer checks cover the pinned Build 94 compatibility baseline, update/install, preserved projects/campaign record, moving preview frames, explicit export approval, saved speed/captions/audio/framing in Gallery MP4, Share chooser without sending, restart approval clearing, packaged JNI/service English captions, Generate Hook application, reference frame/SFX controls, persistence, clean system-picker import and crash-log checks. The new screenshot assertion checks that native video stays inside the portrait preview canvas.

The initial final-workflow attempt failed before app execution because Actions could not download its already-built APK artifact (Not Found). Only the failed job was retried using the same immutable APK; the passing build/media/native jobs were retained. Earlier Build 119 passed its workflow but manual screenshot inspection exposed preview overflow; Build 121 supersedes it. No earlier 1.1.0 candidate was delivered.

The update baseline is rebuilt x86 source from pinned Build 94, signed with the original identity, rather than the original ARM64 binary. The installed owner APK is otherwise unchanged. Tests use public/synthetic fixtures, not owner footage.

Native endurance repeats/pads public JFK speech into 240 consecutive 30-second windows, totaling 7,200 seconds. Linux CI wall time: 613.619 seconds; warm RSS 281,400 KiB, final/peak RSS 347,496 KiB. This is not unique two-hour footage, a phone speed benchmark or a speech-accuracy guarantee.

## Remaining owner checks

Physical Nubia audio latency/loudness, codecs, heat/battery behavior and broad Filipino/Taglish accuracy remain unmeasured. Install, preview a personal clip, generate/edit a hook, place SFX, reopen the project and review an approved MP4.

Hook suggestions are local templates, not a cloud language model or visual recognition. Reference presets crop footage; check framing. Original synthesized effects are not ripped from reference videos. These controls do not automatically reproduce an uploaded reference edit.

Existing v1 scope limitations remain: overlapping layers/cross-dissolves, speed ramps, subject tracking, background removal, advanced audio cleanup and public-store release are future scope. No cloud provider is included.

[Previous 1.0.2 release record](RELEASE_1_0_2.md). Version 1.0.1 remains withdrawn for its fixed import crash.
