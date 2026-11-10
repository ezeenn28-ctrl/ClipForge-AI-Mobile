# ClipForge AI 1.2.0 — Build 123

Prepared 11 November 2026 (Asia/Manila). Physical owner acceptance remains.

## Installer

- File: `ClipForge-AI-1.2.0-universal.apk`; 17,503,118 bytes.
- Version 1.2.0, version code 123; package `com.clipforge.mobile`.
- Source: `51acf8210a457697817a5e0aaf7e64eb39edec90`.
- SHA-256: `8e533f00ebe8098b18c5c17cc166f8ddc235c67e162f71a23ad3386f235c1e0f`.
- Certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
- Non-debuggable signed release; ARM64 and x86_64.
- Install as an update without uninstalling. Original signing identity and app data are retained.

[Download APK](https://chatgpt.com/library?file_id=libfile_0eedd49a34008191b1e60b6e43504e56&account_id=personal)

## Sound-library upgrade

- 18 original procedural effects, up from six.
- Search, audition, local favorites, MP3/audio import and individual Myinstants downloads.
- Official API access with explicit searches, bounded responses, HTTPS origin checks, download limits and rate-limit handling.
- Downloaded/imported files are copied privately, converted to mono PCM and reduced if excessively boosted. Downloaded sounds retain their name and source-page link.
- Custom effects keep their timing, volume, source information and duration through save/reopen, split, trim, reorder and constant speed changes.
- Adding a new effect at an identical source position replaces the existing cue; Undo restores the previous cut settings.
- Audition stops on dismissal/background navigation and avoids late playback after an asynchronous preparation is cancelled.
- Existing hook generation, white-frame preview/export fix and explicit export approval remain.

See [Sound library guide](SOUND_LIBRARY.md) and [reference-style guide](REFERENCE_STYLE.md).

## Verification

[Build 123 verification](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42865182923).

| Check | Actual result |
| --- | --- |
| Host and Android-host unit tests | 144 passed; zero failures/errors/skips |
| Android API 30 x86_64 media tests | 11 passed; zero failures/errors/skips |
| Real native inference | 240/240 synthetic windows passed |
| Exact signed installer workflow | 18 recorded checks passed |
| Release integrity | Original certificate, non-debuggable manifest, both ABIs and checksum verified |

New unit coverage includes official API response parsing, encoded queries, safe URLs, bounded requests, Retry-After handling, invalid/oversized downloads, custom cue timing/persistence, catalog recovery, boosted-wave handling and local-asset preview.

The added Android media test imports a synthetic MP3, restores its cue from a saved project, and exports it at the chosen time with music in another audio sequence. Decoded MP4 audio verifies quiet intervals and the imported effect's timed energy. The other ten media tests cover the prior preview, framing, captions, speed/pitch, transitions and long-source windowing regressions.

The actual installer workflow retains the earlier update/project/preview/campaign/export/Share/JNI/caption/import checks. New checks search and audition a built-in sound, favorite/select it, expose Myinstants controls, import a local MP3 through the system picker, add it as a timed effect, and preserve both the custom cue and favorite across a process restart. The workflow also checks for Java/native fatal exceptions. Myinstants network download is not claimed as an automated Android UI test: the network client is unit-tested with controlled responses, while live official search and a real Vine Boom MP3 download/full decode were separately verified in the workstation.

Live downloaded example: VINE BOOM SOUND, 21,230 bytes, 1.306122 seconds. No downloaded Myinstants audio is included in the APK or repository.

Native endurance repeats/pads public JFK speech into 240 consecutive 30-second windows, totaling 7,200 seconds. Linux CI wall time was 607.759 seconds; warm RSS 281,584 KiB and final/peak RSS 292,452 KiB. This is not unique two-hour footage, a phone benchmark or a broad speech-accuracy guarantee.

The update baseline was rebuilt from pinned Build 94 source for x86 compatibility and signed with the original identity; it was not the original ARM64 binary. The owner installer itself was tested unchanged. Fixtures are public/synthetic; no owner footage was automatically exported. Build 122 was superseded during final refinements and is not counted as a completed release.

## Scope and remaining phone checks

Check actual Myinstants search/download over the phone's connection, audition loudness/latency, imported effects in a personal export, favorites and project reopening on the Nubia. Physical codec behavior, heat/battery use and broad Filipino/Taglish accuracy remain owner checks.

Myinstants community sounds do not establish blanket commercial reuse rights. Source links are retained for checking permissions before paid posts. API availability and network limits are controlled by Myinstants. Downloaded files and original effects work offline; search needs a connection.

Imported effects are limited to 0.04–15 seconds and 10 MB, with 200 library assets. Project limits remain 12 cues per cut and 120 overall. The lane plays one effect at a time. Metadata project backups do not include sound files.

[Previous 1.1.0 release record](RELEASE_1_1_0.md). Version 1.0.1 remains withdrawn. Existing future scope—overlapping layers/cross-dissolves, speed ramps, tracking, background removal and public-store distribution—has not changed. No cloud AI provider is included.
