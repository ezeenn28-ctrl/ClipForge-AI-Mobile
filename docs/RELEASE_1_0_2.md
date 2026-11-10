# ClipForge AI 1.0.2 — verified phone-test candidate

Prepared 10 November 2026 (Asia/Manila). All automated gates passed.
Fresh physical owner acceptance is the remaining release gate.

- Installer: `ClipForge-AI-1.0.2-universal.apk`, 17,437,582 bytes.
- Version **1.0.2**, version code **116**; ARM64 and x86_64.
- Package `com.clipforge.mobile`; non-debuggable signed release.
- APK source: `21ceeb0fe10bc55f380da064b67df7aabb0a5c6f`.
- APK SHA-256: `d680accf98773c81ff98f108f81dc7c1d80d779eedd468f8b62b460de7b8614b`.
- Certificate SHA-256: `ea9f20de77e24660999aaae941f66029c76ffe94170e33dfc4cfb03ce13d8ea6`.
- [Download APK](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/releases/download/v1.0.2/ClipForge-AI-1.0.2-universal.apk) or [private release page](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/releases/tag/v1.0.2).

## Fixed blocker

**Version 1.0.1 is withdrawn.** Real system-picker import exposed an intermittent
main-thread crash: a pending playback coroutine used an old active-timeline flag
after importing a new source had cleared the clip list. Clamping against
`lastIndex == -1` threw an empty-range exception. Related preview reconciliation
also relied on a mutable list after deriving its position.

Version 1.0.2 reads one current timeline snapshot per playback tick, tolerates an
empty list and uses consistent snapshots for seek, preview reconciliation and
music timing. Two new regression cases cover empty/replacement timelines,
stale player indices and source/output clock mapping through speed.

The test-only typed-finger injector made Android 11 DocumentsUI selection work,
revealing the actual app crash. That helper is not packaged in the installer.
The subsequent complete signed-APK workflow passed, including the import path
and real packaged-engine captions.

## Fresh actual results — Build 116

| Check | Result |
| --- | --- |
| Host/JVM and Android-host unit tests | **118 passed**, zero failures/errors/skips |
| Android API 30 x86_64 media tests | **9 passed**, zero failures/errors/skips |
| Real native inference endurance | **240/240 windows passed**, model reuse/reload, timed cues and progress valid |
| Exact signed APK complete acceptance | **12/12 recorded checks passed**, no Java/native ClipForge fatal exception |
| Signature, release manifest, native flags and APK integrity | Passed; original certificate retained, non-debuggable, both ABIs present |

All four jobs in [Build 116](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42795867454)
completed successfully. The published and direct-chat installers contain the
same checksum-verified APK bytes.

The twelve actual-installer checks verify:
1. Pinned Build 94 compatibility-baseline launch.
2. Signed update and rejection of run-as debugging.
3. Both independent named projects retained.
4. Changing video frames in the non-debuggable release.
5. Campaign record retained.
6. Export blocked before explicit review approval.
7. Approved Gallery MP4 with expected speed/duration, 1080x1920 framing,
   caption/highlight pixels and audio.
8. Android share sheet opened without sending to any recipient.
9. Project restored and export approval cleared after process restart.
10. Public English speech decoded and editable captions produced by the APK's
    packaged JNI engine and foreground service.
11. Clean signed install imported through the system picker and retained its
    new project after restart.
12. No ClipForge Java/native fatal exception in the complete workflow log.

The update baseline was rebuilt from pinned Build 94 source for x86 compatibility
and signed with the original key; it was not the original ARM64 Build 94 binary.
The owner installer itself was tested unchanged. All footage and seeded records
were synthetic/public; no owner media was used or automatically exported.

Native endurance used the public JFK sample padded/repeated into 240 consecutive
30-second windows, totaling 7,200 seconds. Linux x86_64 CI wall time was **472.486
seconds**, with warm RSS 281,848 KiB and final/peak RSS 348,204 KiB. This is a
two-hour-equivalent synthetic workload, not unique two-hour footage or a phone
runtime/accuracy benchmark. Do not infer a phone speed improvement from CI timing.

## Delivery and installation

[Private delivery run](https://github.com/ezeenn28-ctrl/ClipForge-AI-Mobile/actions/runs/42799054605)
passed the four-job gate, APK checksum and production-app tree checks. GitHub
reports the uploaded APK digest equal to the SHA-256 above. Version 1.0.1 is
explicitly marked withdrawn in its release title and notes.

Install 1.0.2 as an **update without uninstalling**. The certificate matches the
existing app, including Build 94. Keep original videos/music accessible.
See [Quick start](QUICK_START.md). The release remains marked prerelease pending
physical owner acceptance.

The earlier local-workspace and direct-chat saving blocker is resolved; a direct
chat installer is now available as well as the private GitHub download.
Owner signing-key recovery/backup was already verified and stays separate and
private; no key material is in the installer or source.

## Remaining physical checks and scope

Check real Nubia media, Play/Pause/Seek, editing, speed/music, caption accuracy,
saved/reopened edits, approved Gallery export and sharing. Also check metadata
backup/restore and a longer transcription pause/reopen/explicit Resume.
Fresh phone runtime, heat/battery use, device codecs and broad Taglish accuracy
remain unmeasured. Earlier owner Build 94 checks remain baseline evidence.

The supported personal v1 scope is in QUICK_START.md. Public-store distribution
is separate. Overlapping layers/cross-dissolves, speed ramps, subject tracking,
background removal and advanced audio cleanup remain future additions.
No cloud provider is included. SDR is tested; device-specific HDR matching is
outside v1. Campaign records are manual and do not guarantee eligibility or income.

Earlier failed and cancelled attempts remain in VERIFICATION.md and Actions.
They are not counted as passing tests. Build 115 was superseded while related
snapshot handling was completed; the fresh results above belong to Build 116.
