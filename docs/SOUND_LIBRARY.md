# Sound library

Open **Audio → Sound library · 18+**.

## Built-in sounds

Search, tap **Preview**, then **Use sound**. Back in the editor, scrub to the desired position and tap **Add SFX at playhead**. Use sound selects an effect; it does not automatically insert or export it.

The 18 original procedural effects are Whoosh, Pop, Impact, Metal chime, Boing, Scratch, Bass boom, Punch, Riser, Fast swish, Click, Camera shutter, Ding, Error beep, Buzzer, Glitch, Heartbeat and Tick.

**Favorite** saves a sound to the local favorites filter. **Stop preview** stops audition playback. Audition is separate from timeline playback and defaults to 25% volume.

## Myinstants

1. Select the **Myinstants** tab.
2. Enter a search, or select a quick term, and tap **Search sounds**.
3. Tap **Download** on an individual result.
4. In **My sounds**, preview the downloaded file and select **Use sound**.
5. Back in the editor, add it at the playhead and set its volume.

The app uses the [official Myinstants API](https://www.myinstants.com/api/v1/). There is no account or API key required for this anonymous workflow. ClipForge caps requests at eight per minute and honors server rate-limit responses. Searches are explicit button actions, not background polling. Video, captions and project media are not uploaded to Myinstants.

Downloaded sounds retain their name and source-page link, and remain available offline in the private library. The APK includes no downloaded Myinstants samples. Community availability does not establish a commercial reuse license; check the sound's rights and the [provider terms](https://www.myinstants.com/en/terms_of_use.html) before paid posts.

## Import audio

Select **Import MP3 / audio** and choose a local file through Android's picker. Imports support audio Android can decode, up to 10 MB and 0.04–15 seconds. Files are copied privately and converted to mono PCM at the decoded sample rate. Boosted audio is reduced rather than amplified; a short edge envelope reduces clicks.

The library allows 200 imported/downloaded sounds. Favorites are stored on this device. Keep app data: metadata project backups do not carry the sound files to another phone. Import missing files again when moving devices.

## Editing

Imported sounds use the same timed cue controls as built-in effects: volume, move to playhead, remove, clear, undo/redo and project save/reopen. Adding at exactly the same source position replaces that existing cue, so the chosen effect is not hidden behind another sound. Undo restores the previous cut state.

The effects lane plays one sound at a time. Nearby cues shorten the previous effect, and remnants under 40 ms are omitted. Anchors follow split, trim, reorder and constant speed changes while the sound itself retains its pitch. There are up to 12 cues per cut and 120 per project.

Review is still required before MP4 export. Check framing, captions and audio on the phone.
