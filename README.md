# ClipForge AI Mobile

Private Android clipping editor using Kotlin, Compose, Media3 and native whisper.cpp.

## Implemented
- Local video import and direct HTTPS video-file download (1 GB limit).
- Offline multilingual Whisper tiny model with checksum-verified, user-initiated download; English/Tagalog accuracy requires review.
- First-30-second, selected-highlight, batch-highlight and chunked full-video transcription.
- Atomic full-transcript checkpoints, resume, editable timed subtitles and paged transcript fields. Audio decoding seeks to each requested window and uses bounded temporary WAV chunks.
- Up to 15 local speech-ranked, non-overlapping 15–30 second highlight suggestions. Ranking uses speech coverage, vocabulary and hook words, not predicted virality. Coverage-window generation and visual-activity sampling remain separate tools.
- Timed subtitle preview/export, static opening hooks, adjustable subtitle color/size and bounded subtitle bitmap retention.
- Rendered 1.12× zoom and monochrome effects shared by playback and export.
- Campaign Mode with locally saved caption/rules, required tags, duration checks and manual source/rights confirmations. Failed checks block campaign export approval.
- Explicit clip review approval before export. Routine edits revoke approval. No automatic export or media upload.

## Build and verification
GitHub Actions runs JVM unit tests, builds the arm64 debug APK and validates its ZIP structure. Unit test reports, logs and APK are uploaded as artifacts. Open the latest successful **Build Android APK** run to download the APK at a consolidated device-testing milestone.

The baseline commit ae4f644 passed Actions build #59, including unit tests and APK assembly. See subsequent Actions runs for verification of new changes.

## Current limits
- Foreground processing: keep the app open. Background work, full project restoration and process-death job recovery are not implemented. Full-transcription checkpoints survive restart; reopen the same video and run full transcription to resume.
- Audio decoding seeks and trims each <=30-second inference window. Long files still require device validation of codec seeking, timestamp alignment, runtime and thermal behavior.
- Whisper tiny accuracy varies, especially for Taglish, noise and overlapping speech. Review and correct subtitles.
- Effects are a fixed zoom and monochrome filter; animated transitions, multitrack editing and face tracking remain future work.
- Campaign checks do not fetch briefs or verify audience geography, account standing, engagement, duplicate submissions or payout eligibility.
- Cloud AI has no provider implemented. No paid service is used. A future provider requires explicit permission before upload.
- Export progress and cancellation are available; failed/cancelled partial files are removed. Downloads use unique temporary files so a failed import cannot overwrite the selected source.
- Export remains in the app-specific Movies directory. Device codec, aspect-ratio and playback/export behavior need physical-phone validation.
- No claim of complete production readiness or verified two-hour processing until consolidated device testing.

## Stack
Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, Java 17, Media3 1.5.1, whisper.cpp v1.7.6; Android 8+; arm64-v8a.
