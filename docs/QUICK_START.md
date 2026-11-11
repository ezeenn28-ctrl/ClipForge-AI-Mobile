# ClipForge AI 1.4.0 — personal Android release

## Install

Download [ClipForge-AI-1.4.0-universal.apk](https://chatgpt.com/library?file_id=libfile_6b37b0eaf9308191806a7297fbfc0b94&account_id=personal).
It includes ARM64 for the Nubia Neo 2 and x86_64 for acceptance testing.
Install as an update. Keep the existing app installed so its
projects and saved media grants remain available. This release retains the
Build 94 signing certificate; its version code increases with the verified build.

## Make a clip

1. Choose **New project** and select a local video, or continue a recent project.
2. Use **Edit** to add videos, trim source in/out, split, duplicate and reorder.
3. Select a cut and open **Speed** for 0.5x–2x playback with natural voice pitch.
4. Open **Effects** for color, motion and the transition to the next clip.
   **Dip to black** and **Zoom cut** preserve the edit's duration. The duration
   setting is shared across the outgoing/incoming halves and shortened for tiny cuts.
5. Use **Captions** to download the offline model once, generate captions and
   correct the words. English, Filipino and Taglish accuracy varies; review the result.
6. Use **Audio** for the original sound and one local music bed. Music keeps its
   own speed and timeline placement. Original-audio fades remain separate from
   the video boundary transition.
7. Play and seek through the full edit. Export opens the review page; explicitly
   approve the clip before rendering. The app never automatically exports candidates.
8. Open the MP4 in Gallery or Share to TikTok, Instagram, YouTube or another app.

## Reference frames and caption styles

Open **Format** for eight reference templates plus Original canvas. Open **Effects** for video looks and manual grading. Open **Captions** for ten styles and editable font, color, size and position. See [Style pack guide](STYLE_PACK.md).

## Workflow upgrades

Open **More** for saved editing presets and previous-autosave recovery. **Captions** adds language hints, editable line/word timings and estimated word-follow highlights. The Export page shows project-local campaign checks and an explicitly started approved export queue. See [Workflow guide](WORKFLOW_UPGRADE.md) for operation and limits.

## More sound effects

Open **Audio → Sound library · 18+** for built-in search/audition, favorites, local MP3 import and the Myinstants tab. Download an individual sound, preview it in **My sounds**, choose **Use sound**, then **Add SFX at playhead**. See [Sound library guide](SOUND_LIBRARY.md) for limits and source permissions.

## Find clips and long sources

**Find clips** samples moments across the source with local speech analysis and
returns up to 15 editable candidates. Full transcription can cover the source in
30-second windows. Saved windows survive interruption; **Resume** is explicit.
Keep the original source accessible. Replacing or deleting it requires relinking.

Two-hour scheduling, durable recovery and far-end audio seeks have dedicated
automated checks. Actual completion time, heat, battery use and Taglish accuracy
depend on the phone and source. The app does not promise uninterrupted or
unlimited processing. A pause finishes the current audio window before stopping.

## Projects and backups

Edits autosave locally. Rename projects in **More** and reopen them from Home.
**Save metadata backup** includes projects, campaign records and speech progress.
It excludes source video/music, model weights and media permissions. Preserve
your original files separately. Restored projects always require a fresh review.

## Campaign Mode

Record submissions, status, platform-reported views, audience percentages, CPM,
payout thresholds and actual paid amounts. The tracker supports TikTok, Instagram,
YouTube, Facebook and X. Estimates are separate from recorded payments. It does
not fetch campaign briefs or verify account standing, geography, engagement,
rights or duplicate-submission eligibility; enter and verify those yourself.

## Version 1 scope

- Offline editing, optional one-time model download, local media and direct HTTPS
  media-file imports. Social watch-page URLs are not general video download links.
- Up to 60 cuts and six hours of output, one music track, 1080p portrait/landscape.
- Boundary transitions are dip-to-black and zoom cuts. Overlapping video tracks,
  cross-dissolves, speed ramps, subject tracking, background removal and advanced
  audio cleanup are future additions.
- SDR color controls are tested; device-specific HDR matching is not promised.
- No cloud provider, required account, ads or paid service is included.

## Signing recovery

The private owner signing backup is separate from project backups. Keep it
offline and do not share it. It preserves the original update identity; losing
that key prevents signing compatible future updates. Build automation fails if
the expected key is missing or its fingerprint differs, rather than rotating it.
