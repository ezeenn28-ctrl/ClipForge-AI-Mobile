# ClipForge AI 1.0.0 — personal Android release

Released 10 November 2026 (Asia/Manila).

- APK: `ClipForge-AI-1.0.0-arm64.apk`
- Version **1.0.0**, version code **96**, arm64-v8a.
- Package `com.clipforge.mobile`; non-debuggable release.
- Source commit: `f62f7e7b4c65a2d819cf8e8ab1458bf9819a197a`
- APK SHA-256: `8d11e3ee2cb06e093ac938f162826c7178698b58688020b7ce676ba83acc16d3`
- Certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`
- [Verified Actions #96](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42756941805)

## Verification

**116 host/unit tests, 9 Android API 30 x86_64 tests, and native Whisper integration
passed. There were zero test failures, errors or skips.**

Real MP4 checks cover transitions, framing, caption appearance/timing, mixed
audio, speed and voice pitch. A two-hour source produced bounded one-second audio
windows at the start, one hour and final second. Negative AAC priming packets are
decoded correctly. A persisted 240-window runner test used synthetic transcript
results to verify process loss, backup/restore and resume without repeating the
17 completed windows; it processed exactly the remaining 223.

Campaign UI tests cover 54 synthetic records, status/view/platform editing,
Facebook, estimates distinct from actual payments, backup/restore and protection
against overwriting unreadable data. No synthetic records were seeded into owner data.

Native kernels passed the -O3 compile-command check. Signature, original update
identity, ZIP integrity, SHA-256 and non-debuggable manifest passed. The original
key was recovered through owner-only AES-GCM/RSA-OAEP transport; its certificate
and private-key signing operation were verified. The private backup stays
separate from app/source/project backups.

## Install and use

Install as an **update** without uninstalling the existing app. The signing
certificate matches Build 94 and the version code increases.

See the [quick start and full v1 scope](QUICK_START.md). Owner-reported Build 94
phone checks remain baseline evidence, not a new physical test of this APK.

## Limits

This freezes the personal-use v1 scope. Public-store distribution is separate.
Dip-to-black and zoom-cut boundaries preserve duration. Overlapping cross-dissolves,
multiple video layers, speed ramps, tracking, background removal and advanced
audio cleanup remain future additions. No cloud provider is included.

Two-hour scheduling/recovery and far-end decoding are verified. Full-session
transcription runtime, heat/battery behavior and broad Taglish accuracy on the
owner's phone are not benchmarked. Review captions and AI candidates. Campaign
records are manual; they do not establish platform eligibility or guaranteed income.
SDR color is tested; device-specific HDR matching is outside v1.
