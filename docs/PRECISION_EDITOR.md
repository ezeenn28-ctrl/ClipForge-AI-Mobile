# ClipForge AI 1.7.0 — precision editor guide

## Preview and workspace

Import a local video or continue a recent project. The focused editor keeps its preview above the tool pane.
The horizontal row below playback contains **Precision review tools**, **Large preview**, **Hide tools** and **Collapse timeline**. Swipe the row if a control is offscreen. Preview controls are transient and do not change the export.

Use **Show tools** or the bottom toolbar to reopen editing controls. **Show timeline** can reveal lanes from another tool panel. Wide layouts retain independent preview and tool scrolling.

## Timeline and contextual tools

Pinch with two fingers on the timeline to change zoom. Drag the ruler with one finger to scrub. Snapping includes cut boundaries, caption boundaries, music beat markers and SFX boundaries. Beat markers remain approximate for imported music.

Tap a video cut for Edit, a caption block for Captions, original audio for Voice, music for Music, or an SFX block for SFX. The selected item and its tool pane follow that choice.

## Precise review

Open **Precision review tools** for **Previous frame**, **Next frame**, a millisecond timecode, **Loop selected cut**, **Play 2s before edit** and **Before / after**. Swipe this row to see the remaining controls.

Frame controls consult actual video sample timestamps, map through source trim/speed and request precise player seeks. Container/codec timing still needs phone validation. Loop and comparison apply only to preview; Export renders the edit. Comparison retains its current cuts and speed.

## Track mute and lock

Open **Track mute / lock**. Video, captions, voice and SFX controls apply to the selected cut. Music controls apply to the whole bed. Video lock protects structural/video changes; caption, voice and SFX locks protect their lane settings. Caption appearance is captured when locking it.

Mute keeps the source media and timings. Caption mute hides hook/subtitle text, voice mute silences that cut, and SFX mute omits its timed cues. Music mute silences the bed. Unlock before editing a locked lane. Undo/redo can restore a prior lock state.

## Direct text placement and sizing

Open **Text**, choose **Move opening text** or **Move captions**, then drag the outlined guide in the preview. Drag its corner handle to resize. The guide remains inside the preview at the edges. Center and safe-area guides assist alignment.

The displayed caption offset/scale helps confirm a change. Each completed drag/resize creates one labeled undo step. Opening text and body captions keep independent placement/scales. Position and scale are saved per cut and use the same renderer in preview and MP4 export.

**Captions** style/font/color/position controls affect the selected cut in the focused editor. Advanced source tools retain their project-default appearance controls.

## Waveform and meters

Open **Audio → Analyze selected audio waveform**. Reopen the timeline to see decoded peaks in the selected source-audio lane.

Cuts up to 90 seconds are analyzed across their range. Longer cuts sample up to 30 seconds at the start, middle and end; empty intervals are unmeasured. Derived peaks are held in a bounded temporary cache, not stored as project media.

Open **Audio output meters** in Audio. Voice, music and SFX show decoded PCM peak levels after lane volume. They are separate players, not summed master loudness. Check the combined mix by listening.

## Undo, redo, reset and settings transfer

The history controls identify their action, such as **Undo Trim**, **Undo Text placement**, **Undo Music**, or **Redo Caption style**. History retains up to 30 cut/music steps. Runtime history is not restored after restarting the app; the saved edit is retained.

In **More → Copy settings / reset selected**, select Captions, Color, Effects and/or Audio. Copy the selected cut, then paste into the selected cut or all cuts. Captions transfers styling/placement; Audio transfers voice gain/cleanup/volume/fades. Target source media, trim, hook and speech text are preserved. Locks reject prohibited changes as one transaction.

Reset only the selected category. Resetting caption style/placement preserves its words. The internal settings clipboard is transient.

## Export and phone check

Review the edited preview, open Export, and explicitly approve the result. None of the new preview, analysis or editing controls starts an export automatically.

Install the signed update over the existing app. Keep the app installed to retain projects and media access. On the Nubia, confirm local import, pinch/seek, drag/resize, lane mute/lock, waveform, caption save/reopen, and approved MP4 playback in Gallery. Automated emulator results do not establish phone thermals or every source/codec.
