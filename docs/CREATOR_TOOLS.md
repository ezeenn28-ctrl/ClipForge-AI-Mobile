# ClipForge AI 1.8 creator tools

Open a project, select a timeline cut, then **More → Creator tools**.

- **Templates:** Anime, Romance, Podcast, Gaming, Music. Each styles selected-cut captions, opening text, gentle motion and voice cleanup. Source trim, spoken words and the existing music bed remain. Locked tracks reject incompatible changes.
- **Subject framing:** Explicit local analysis, at most 120 reduced frames, at least 500 ms apart. Face-first; motion or center fallback where no face is found. No identity recognition, upload or added model download. Review the privately rendered preview, then apply. Horizontal/vertical offsets and zoom remain adjustable. Fast motion, profiles, multiple people and sparse long-video sampling can require manual correction.
- **Beat draft:** Uses the current music bed's detected beat markers or BPM-estimated grid. Creates at most 12 splits in the first 60 seconds, respecting minimum cut spacing and project limits. Adds subtle zoom cuts and sparse swishes where there are no existing effects. Review a private preview before applying; one undo restores the timeline. It does not predict musical downbeats or engagement.
- **Opening text:** Independent font, color, background, fade/pop and show/hide interval in output seconds. Caption body styling and timing stay separate. Preview and MP4 use the same text renderer. Existing projects retain their previous opening appearance by default.
- **Caption dictionary:** Personal literal word/phrase replacements, including Filipino/Taglish and names. Longest match wins, matching is case-insensitive, whole words/phrases are respected and replacements do not cascade. New transcription windows use saved rules. Apply to existing selected-cut captions explicitly. Blank correction deletes a rule. The dictionary is included in metadata backups.
- **Clip versions:** Saves the original project first, then opens a new independently saved project with the chosen version name. Originals and alternatives appear in Projects. Each version requires fresh export review.
- **Background export:** Only an explicit approved Export or approved queue Start creates an in-memory render authorization. A foreground service continues when the editor is backgrounded. Notification includes progress and Cancel; Android 13+ notification permission is requested. Denying notifications leaves in-app controls available. Cancel, force-stop or process death requires loading the interrupted snapshot and reviewing again before retry. There is no automatic boot/resume/export and no persistent authorization.
- **Quality:** 720p or 1080p, up to 24/30/60 fps and 2/4/8/12/20 Mbps target bitrate. FPS is a cap, not frame interpolation; device encoders can adjust bitrate. File-size estimates include an audio/overhead allowance and are approximate.

Draft previews render only the first 60 seconds privately, at 720p and up to 24 fps. They never publish to Gallery. Apply is enabled after preview completion. Final exports still require explicit review and approval. Preview rendering can be cancelled.

Offline transcription, procedural audio and local framing remain local. No optional cloud AI is enabled by these changes.

## Verification

The release/test-results report records exact source SHA, APK hash, signature, automated test counts, run links and limitations. Emulator results do not measure thermal behavior or performance on the Nubia Neo 2 5G. Existing v1.7 installed-device success was reported by the user; v1.8 needs its own device check.
