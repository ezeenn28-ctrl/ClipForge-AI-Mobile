# ClipForge AI Mobile

Private Android clipping editor using Kotlin, Compose, Media3 and native whisper.cpp.

## Studio interface
- Four focused workspaces: Studio, Captions, Campaign and Export, with persistent navigation and visible processing status.
- Custom dark color system, vector identity, local frame thumbnails, clip review cards and full-screen preview. Thumbnail decoding is serialized and capped by an 8 MB cache; large unscaled frames are skipped on API 26.
- Exact trim timecodes support short clips near the end of long sources; crop/fit framing is shared between player preview and export and saved with the project.
- Advanced batch tools, direct-link importing and submission editing forms are progressively disclosed rather than filling the first screen. Workspaces retain scroll positions and unfinished submission form text when switching tabs.
- Compose interaction tests render real Android-view screenshots for layout review. These host-rendered previews do not replace physical-device testing.

## Background reliability and recovery
- User-started offline transcription runs in an Android foreground service, with an ongoing notification, bounded wake lock, progress, thermal pause checks and pause-after-window controls. Screen recreation no longer owns the speech worker.
- Each completed audio window is atomically checkpointed. Relaunching after process interruption offers explicit Resume; no job or export silently restarts. Completed/corrected subtitle state is retained separately from inference checkpoints.
- Metadata backups include the last project, submissions and last speech checkpoint. Videos, downloaded models, signing keys and permissions are excluded. Imported backups are validated and reviewed before replacing included records; active job states restore paused and export approvals remain cleared.
- Development APK signing identity is cached in Actions and its fingerprint is checked. This cache is not a durable production release-keystore backup; release signing remains separate work.

## Implemented
- Local video import and direct HTTPS video-file download (1 GB limit).
- Offline multilingual Whisper tiny model with checksum-verified, user-initiated download; English/Tagalog accuracy requires review.
- First-30-second, selected-highlight, batch-highlight and chunked full-video transcription. Native CPU inference is optimized even in debug APKs. Each job reuses model weights across its audio windows and releases them afterward, including on failure. Current-window progress, phase and elapsed time are displayed; percentages are inference progress, not estimated remaining time.
- Atomic full-transcript checkpoints, resume, editable timed subtitles and paged transcript fields. Audio decoding seeks to each requested window and uses bounded temporary WAV chunks.
- Up to 15 local speech-ranked, non-overlapping 15–30 second highlight suggestions. Ranking uses speech coverage, vocabulary and hook words, not predicted virality. Coverage-window generation and visual-activity sampling remain separate tools.
- Timed subtitle preview/export, approved-clip SRT saving, static opening hooks, adjustable subtitle color/size and bounded subtitle bitmap retention.
- Rendered 1.12× zoom and monochrome effects shared by playback and export.
- Campaign Mode with locally saved caption/rules, required tags, duration checks and manual source/rights confirmations. Failed checks block campaign export approval.
- Atomic local autosave and automatic restoration of the last editing project, including trim, drafts, subtitle edits/style and effects. Playback is paused after restore; export approval, cloud permission and campaign confirmations are never restored. Downloaded source files are retained in app-private storage.
- Local submission tracker with pending/approved/rejected states, views, CPM, payout thresholds/caps, audience geography, rejection notes and actual paid amounts. Pending/rejected submissions and unmet entered limits are excluded from approved estimates. All values are manual USD bookkeeping; estimates are not payout guarantees.
- Explicit clip review approval before export. Routine edits revoke approval. No automatic export or media upload.

## Build and verification
GitHub Actions runs JVM unit tests, builds the arm64 debug APK, checks optimized Whisper/CPU compiler flags and validates APK ZIP structure. A separate Linux JNI smoke job uses the public JFK sample and checksum-verified model to exercise timed recognition, progress callbacks, model reuse, cleanup and reload. Host success does not establish Android speed or subtitle accuracy. Unit test reports, logs and APK are uploaded as artifacts. Open the latest successful **Build Android APK** run to download the APK at a consolidated device-testing milestone.

The baseline commit ae4f644 passed Actions build #59, including unit tests and APK assembly. Development stage one (0e33ce0) passed Actions build #60, including unit tests and APK assembly. See subsequent Actions runs for verification of later changes.

## Current limits
- Android may interrupt foreground processing because of system limits, heat or memory. Completed audio windows survive interruption and Resume is explicit; uninterrupted background execution is not guaranteed. The last editing project restores automatically when its original source is still accessible; active jobs are not restarted. Autosave runs after a one-second editing pause, including when completed batch results arrive. Full-transcription checkpoints survive restart; run full transcription on the restored original video to resume. Moving, replacing or deleting source media can prevent correct restoration; review it again before export.
- Audio decoding seeks and trims each <=30-second inference window. Long files still require device validation of codec seeking, timestamp alignment, runtime and thermal behavior.
- Whisper tiny accuracy varies, especially for Taglish, noise and overlapping speech. Review and correct subtitles.
- Effects are a fixed zoom and monochrome filter; animated transitions, multitrack editing and face tracking remain future work.
- Campaign checks do not fetch briefs or verify audience geography, account standing, engagement, duplicate submissions or payout eligibility.
- Cloud AI has no provider implemented. No paid service is used. A future provider requires explicit permission before upload.
- Export progress and cancellation are available; failed/cancelled partial files are removed. Downloads use unique temporary files so a failed import cannot overwrite the selected source.
- Approved renders publish to Gallery (Movies/ClipForge) on Android 10+, using pending entries until writing finishes. Older devices use scoped FileProvider sharing from export directories. Open/Share actions appear only for a completed render; failed Gallery writes retain the private MP4. Device codec, aspect-ratio, Gallery and sharing behavior need physical-phone validation.
- No claim of complete production readiness or verified two-hour processing until consolidated device testing.

## Stack
Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, Java 17, Media3 1.5.1, whisper.cpp v1.7.6; Android 8+; arm64-v8a.
