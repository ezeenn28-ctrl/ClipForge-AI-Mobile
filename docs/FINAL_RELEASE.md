# ClipForge AI 1.3.0 — Build 127

Prepared 11 November 2026 (Asia/Manila). Physical owner acceptance remains.

## Installer

- File: `ClipForge-AI-1.3.0-universal.apk`; 17503118 bytes.
- Version 1.3.0, version code 127; package `com.clipforge.mobile`.
- Tested application source: `c8c747af270a278707562942eeede5f23fc97619`.
- SHA-256: `a743b641f6aded40d87560ff911bbb165cad06d803a3e3f835301eacb3545329`.
- Certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
- Non-debuggable signed release; ARM64 and x86_64.
- Install as an update without uninstalling to retain app data.

[Download APK](https://chatgpt.com/library?file_id=libfile_c66c36d37a088191a41a5ac345756e24&account_id=personal)

## Upgrade

Eight reference templates plus Original canvas; six new static SDR color looks; ten caption styles with editable fonts, colors, size and position. The Effects picker now appears before the timeline and changing tools resets the tool panel to its top. Frame selection retains caption style. Saved style/frame/look choices persist across restart. The existing offline hook generator, 18 built-in sounds, local import, favorites and explicit Myinstants downloads remain.

See [Style pack guide](STYLE_PACK.md) and [Sound library guide](SOUND_LIBRARY.md).

## Actual verification

[Build 127 workflow](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42921110583).

| Check | Result |
| --- | --- |
| Unit and Android-host tests | 152 passed; zero failures/errors/skips |
| Android API 30 x86_64 media tests | 13 passed; zero failures/errors/skips |
| Native inference | 240/240 synthetic windows passed |
| Exact signed installer acceptance | 20 recorded checks passed; result passed=true, error=null |
| Release integrity | Original certificate, non-debuggable manifest, ARM64/x86_64 and SHA-256 verified |

The style unit tests cover declared matte/aperture geometry, distinct caption rendering, large wrapped text bounds, preview/export grade equivalence, bounded manual color combinations and saved state. The existing motion test now scrolls the actual vertical tool panel and asserts the fade control is displayed before clicking; its 400 ms behavior assertion remains.

Two added Android tests export actual synthetic MP4s with Neon square/Neon glow/Warm film and Portrait card/Sticker/Vintage. Decoded pixels verify matte colors, graded video, rounded corners, caption/card colors and emphasis. Earlier tests retain preview, speed/pitch, transitions, caption, long-source decode and timed imported-sound/music coverage.

Signed installer acceptance includes update compatibility, existing projects, playback/seek, campaign records, approval-gated export, sharing, JNI transcription, hook/sound persistence, system-picker imports and fatal exception checks. Added checks apply the new frame/style/look and verify all three after process restart, with export still requiring approval.

Native endurance repeats/pads public JFK speech into 240 consecutive 30-second windows, totaling 7,200 seconds. Linux CI wall time was 741.819 seconds; warm RSS 283804 KiB, final RSS 345852 KiB and peak RSS 345856 KiB. This is not unique two-hour footage, a phone benchmark or a broad speech-accuracy guarantee.

Update testing uses a rebuilt x86 baseline from pinned Build 94 source signed with the original identity, not the original ARM binary. The owner installer is tested unchanged. All fixtures are public/synthetic; owner footage was not exported or uploaded.

## Earlier failures and remaining checks

Build 125 exposed a test scroll target problem; Build 126 passed unit/media/native tests but signed UI acceptance could not reach the look picker. Verification-only run 42887087332 reached application but failed to locate the saved look control after restart. Build 127 moves the picker to the top of Effects and resets scroll on tool changes. These earlier runs are not counted as successful releases.

Physical Nubia preview smoothness, crop/legibility, codecs, sound levels, personal MP4 output, Myinstants network behavior, heat/battery use and broad Filipino/Taglish accuracy still need owner testing. Emulator/Linux results do not establish those results.

The new looks are static SDR treatments. Frames crop source footage and apply in portrait; landscape suppresses the matte while retaining the saved choice. Emphasis is not word-level karaoke. Hook suggestions are local text templates. No cloud AI provider or automatic reference-video recreation is included.

Myinstants source links do not establish commercial reuse permission. No downloaded community audio is bundled. Existing limits and future scope are documented in Quick start and Sound library. Version 1.0.1 remains withdrawn.

[Previous 1.2.0 record](RELEASE_1_2_0.md).
