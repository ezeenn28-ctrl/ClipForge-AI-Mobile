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

## Timeline editing milestone
- Add source selections to a non-destructive sequence of up to 60 cuts. Split at the playhead, duplicate, remove, reorder, trim precisely, and undo/redo. The timeline shows video, caption coverage and original audio against one scrub clock.
- Clipped playlist preview and Media3 sequence export use the same source ranges. Captions are rebased into sequence time for SRT; each rendered cut retains its own source-time subtitle mapping, hook and effect.
- Per-cut original-audio volume and one-second fade controls are implemented for preview and PCM export. Global mute applies to the whole sequence.
- Project autosave and metadata backups retain the sequence, corrected cues and audio settings; approval is always reviewed again after restore.
- This milestone edits cuts from one source. Imported music, multiple source videos, transitions, keyframes, speed ramps and draggable trim handles remain future work. Timeline audio/video synchronization and hardware export still require consolidated physical-device verification.

### Touch editing
- Selected cuts have 44 dp touch targets for dragging source in/out points. Release commits one undoable edit; gesture cancellation discards the draft. Exact timecode fields remain available.
- Optional snapping uses nearby sequence boundaries while scrubbing and source cut/subtitle boundaries while trimming, with a bounded 150 ms tolerance. Trim bounds preserve at least 100 ms and clamp fade lengths.
- Scrub requests are conflated and limited to about 30 updates per second, retaining the latest requested position. This reduces seek pressure; device responsiveness still needs physical verification.

## Multiple videos and local music
- Add up to 12 local videos per import to the existing timeline (60 cuts / six hours total). Each cut retains its own source URI, duration, subtitles and name through split, reorder, undo and restore. The primary source remains available for AI shortlist work.
- A selected source can be relinked to the original media; local access and durations are checked before timeline export. Media and music are referenced with persisted document grants rather than copied into backups. Metadata backups do not include media or grants.
- One local music track supports source in/out timecodes, timeline placement, volume and fades. It plays once, with silence outside its placement, and stops at the sequence end. Original sound keeps per-cut volume/fades and an independent mute control.
- Preview synchronizes a second local audio player to the video clock; export mixes a clipped audio sequence using the same saved settings. Host tests verify composition structure and gain math, not physical audio/video sync or hardware codec compatibility.
- Timeline output uses a consistent 1080 × 1920 or 1920 × 1080 canvas with fit/crop framing. Different videos can be combined on that canvas.
- Imports, music edits, relinking and restoration revoke export approval. Nothing is exported or uploaded automatically.

## Playback repair after Build 78 device feedback
- Preview playlist changes have one owner. Recomposition no longer reloads the same source and cancels a user's Play request. Highlight selection reconciles the source before seeking; timeline seeking checks exact source/range identity rather than only item count.
- Play restarts completed clips/sequences and handles source trim boundaries. Reload preview rebuilds the current playlist at its position while paused; errors remain visible independently of processing status.
- Preview uses a TextureView and explicitly transfers ownership between inline/fullscreen views. Releasing a stale view cannot detach the active preview. Host tests exercise ownership and playback command ordering, not real decoder output.
- Speech highlight ranking now uses the primary source transcript only, preventing another video's timeline captions from being ranked against the wrong duration. Subtitle bitmap replacement recycles the prior cached bitmap.
- The user-provided screen recording showed source playback followed by a black timeline preview with captions. Physical verification of this repair is still required; automated checks alone do not establish that the device freeze is resolved.

- Caption repair: an empty opening hook no longer moves subtitle text into the top hook position. Manual-caption export now retains selected subtitle size/color. Primary-source selected-clip cues remain eligible for speech ranking while unrelated video cues are excluded.

## Simplified editing workflow
- Home prioritizes Find clips, Edit video and Continue editing. Find clips transcribes sampled coverage windows locally and speech-ranks the results; this is sampled analysis, not an exhaustive semantic scan. First use links to the existing one-time speech-model setup.
- The default editor is a dedicated timeline workspace: persistent preview, top Back/Undo/Redo/Export actions and a bottom Edit/Audio/Text/Captions/Effects/Format/More tool bar. Export opens the review page and never bypasses approval.
- Clip controls are contextual. Edit shows split/reorder/trim; Audio shows original sound and music; Text shows hook/manual caption; Captions provides generation/correction/style; Format controls fit/crop. Exact trim fields and source tools remain available without filling the default panel.
- Campaign, backup, relinking and advanced source tools sit under More in the editor. Existing playback repair and multi-source project formats are retained. Host-rendered editor fixtures verify layout and interaction; phone testing remains necessary for real playback.

## Motion and finishing controls
- Effects includes two editable motion keyframes per cut: start/end zoom and normalized position, with linear or eased interpolation. Splitting preserves the motion position at the cut and keeps outer fades at the original clip ends. Eased curves are retimed within each resulting cut.
- Fade-from/to-black controls keep durations unchanged and apply to the video and captions. They are not overlapping cross-dissolves. Fades clamp to half the cut duration on short cuts. Original audio fades remain separate.
- Captions offers Bold clean, Minimal and Caption card presets, with existing size/color controls. Presets and per-cut motion/fades persist in autosave/backups, and old projects receive neutral defaults.
- Preview uses the player clock sampled by the UI; export evaluates motion/fade effects per frame with each item's local presentation clock. Host tests verify values, matrices, persistence and controls, not hardware rendering or perceptual preview/export matching. Physical-device checking remains required.

## Release stabilization: decoder timeouts
- Device feedback from Build 80 identified ERROR_CODE_TIMEOUT after a timeline transition. Preview no longer enables ExoPlayer's GL video-effects pipeline; framing uses the PlayerView fit/crop mode, with zoom/motion/color in view layers. Export retains its own GL rendering effects.
- Preview enables decoder fallback and can switch once to a software-preferred compatible decoder after timeout/decoding failure. The preference persists and is available under More. Software decoding may consume more CPU; access/file errors are not retried as codec failures.
- CI now includes an Android emulator media job using generated, synthetic fixtures. It tests real source/highlight/sequence playback, changing video frames, restart, the compatible decoder, and a 1080p synthetic export with mixed audio/captions/effects. These tests never use or automatically export user media.
- Emulator results do not replace a check on the user's device. The app remains in stabilization until the reported workflow succeeds on the phone; a successful host build alone is not a finished-release claim.


## Approved design and named projects
- The original dark/cyan interface follows the approved ClipForge concept. The editor has a larger responsive preview and visual caption-style cards.
- Projects now have independent identities and names. Importing the same source twice creates separate drafts; the previous draft is checkpointed before source replacement. Recent project cards reopen saved edits, and More allows renaming the active project.
- Named drafts and the last-open project autosave locally. Metadata backups include named drafts with strict path validation; source media and document permissions remain separate. Older project files load with neutral name/identity defaults and migrate on save. Corrupted draft files are retained and reported rather than silently removed.
- Bold highlight emphasizes the last word of each rendered caption line. A # prefix chooses explicit words instead. This is visual emphasis, not inferred word-level speech timing or semantic keyword detection. The shared renderer keeps preview and export styling consistent.
- Selected-source exports now use the same fixed-canvas composition as timeline exports. Portrait validation accounts for encoded dimensions and MP4 rotation metadata.
- The approved release gates and consolidated phone checklist are in [docs/RELEASE_PLAN.md](docs/RELEASE_PLAN.md). Release signing and physical-device acceptance remain outstanding.

## Caption appearance controls
- Captions has Style, Font, Color and Position panels. System Sans, Serif and Mono fonts, text/highlight palettes, alignment and bounded vertical positioning apply to all captions in the current project. The hook keeps its separate top placement.
- Appearance persists in autosave and metadata backups; older projects retain their existing style defaults. Changing appearance revokes export approval.
- Export now scales the caption bitmap to the same centered, full-height 9:16 text canvas used by preview, including inside landscape output. This corrects the previous use of native bitmap dimensions in export.
- Automated checks cover glyph/color/position rendering, legacy project recovery, UI interaction and a synthetic Android MP4 with saved custom caption settings.
- Build 87 device feedback confirmed 26 checklist items. Music import and volume controls remain unconfirmed; see docs/VERIFICATION.md.
