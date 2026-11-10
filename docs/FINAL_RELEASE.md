# ClipForge AI 1.0.1 — final phone-test candidate

Prepared 10 November 2026 (Asia/Manila). Physical owner acceptance is pending.

- Installer: `ClipForge-AI-1.0.1-universal.apk`, 17,437,582 bytes.
- App version **1.0.1**, version code **97**, ARM64 and x86_64.
- Package `com.clipforge.mobile`; non-debuggable signed release variant.
- APK source commit: `496792695211b64ab7e11ce3d82bd0d87306bf1b`.
- APK SHA-256: `01d88ee76d8109c37ea3cda8462c594dfb873bda5e8fe18f8a1a35e606cdc69a`.
- Certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
- [Download APK](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/releases/download/v1.0.1/ClipForge-AI-1.0.1-universal.apk) or [private release page](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/releases/tag/v1.0.1).

## Actual results

| Check | Result |
| --- | --- |
| Host/unit tests, Build 97 | 116 passed; zero failures/errors/skips |
| Android API 30 media tests, retry 100 | 9 passed; zero failures/errors/skips |
| Native inference endurance, Build 97 | All 240 windows passed, including model reuse/reload and progress/timing checks |
| Exact signed APK update/export phase 103 | Nine completed checks passed before later picker automation failed |
| Packaged-engine captions and clean system-picker import | Pending: automation could not activate the synthetic file in DocumentsUI |
| Fresh physical Nubia acceptance | Pending; earlier Build 94 owner results remain baseline evidence |

The native workload used the public JFK sample padded/repeated into 240 consecutive
30-second windows: 7,200 seconds of synthetic input, not unique two-hour footage.
Linux x86_64 CI wall time was 765.529 seconds; warm RSS 308,980 KiB, final/peak
RSS 346,988 KiB. This is not an Android-phone performance or accuracy benchmark.

The nine exact-installer checks cover baseline launch, signed update with debugging
disabled, both saved project names, changing preview frames, campaign record
retention, export blocked before explicit approval, approved Gallery MP4 with
expected speed/duration and 1080x1920 framing/caption/highlight/audio checks,
share-sheet launch without sending, and restart with approval cleared.
The overall phase-103 job **failed later**; it is not described as fully passed.

The compatibility baseline was rebuilt from pinned Build 94 source for x86 and
signed with the original key; it was not the original ARM64 Build 94 binary.
The owner APK itself was unchanged across all acceptance attempts. All footage
and seeded records were synthetic/public; no owner media was exported.

- [Build/unit/native #97](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42769587936)
- [All nine media tests #100](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42772568687)
- [Completed exact-APK update/export checks #103](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42775151387)
- [Latest unresolved picker attempt #110](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42778039367)

## Errors and limits

Build 97's media attempt never executed tests: slow package downloads and insufficient
emulator userdata disk exhausted its job time. Retry 100 used 2 GB userdata,
no snapshots and a bounded boot timeout; all nine tests then passed.

Initial acceptance failures were harness scrolling, disabled TextView labels and
completion actions below the viewport. Correcting these revealed a completed
Gallery export; all media and approval assertions were retained. Later picker
attempts waited for file loading, targeted its tile and tried list mode, but the
system picker did not return the selected synthetic document. Packaged-JNI speech,
clean import and the final speech/import crash-log check were therefore not run
to completion. They remain explicit owner phone checks.

Signature, original update identity, optimized kernels, ZIP integrity, manifest
and SHA-256 passed. Owner signing-key recovery/backup was already verified and
remains private, separate from source, installer and project backups.

The chat workspace became unavailable after the APK was downloaded and verified;
saving a new direct chat file failed. Delivery therefore uses the private GitHub
repository. The first release attempt received HTTP 403 for an older workflow
target. The permitted default-branch target is used only after verifying the
production app subtree is identical to the APK source; installer bytes are
checked independently by the pinned SHA-256.

## Install and final phone checks

Install as an **update, without uninstalling**. The original certificate matches
Build 94; existing projects and media grants should remain. Private GitHub login
is required for download. See [Quick start](QUICK_START.md).

Check existing projects, new video import, Play/Pause/Seek, split/reorder/undo,
speed/music, actual auto captions, saved/reopened edits, approved Gallery export
and sharing. Also check metadata backup/restore and a longer transcription
pause/reopen/explicit Resume. Report any crash, freeze, black frame or mismatch.
Fresh physical verification and broad Taglish accuracy are not inferred from CI.

## Supported v1 scope

The supported personal v1 feature scope is in QUICK_START.md. Public-store
distribution is separate. Dip-to-black and zoom-cut boundaries preserve duration.
Overlapping layers/cross-dissolves, speed ramps, subject tracking, background
removal and advanced audio cleanup remain future additions; no cloud provider
is included. SDR is tested; device-specific HDR matching is outside v1.
Campaign records are manual and do not guarantee eligibility or income.

Delivery succeeded in [private delivery run 5](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42778792327). GitHub reports the uploaded APK digest equal to the pinned SHA-256 above. The release is explicitly marked prerelease pending owner acceptance.
