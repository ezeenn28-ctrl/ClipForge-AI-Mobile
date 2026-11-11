# Workflow upgrade — v1.4.0

## Focused editor

Edit keeps the full timeline. Other tools show a compact cut selector and their own controls first; **Show timeline** expands the tracks when needed. The preview is slightly larger. Choosing another cut retains the current tool.

## Recovery

Autosave waits 350 ms after editing pauses. Each successful write preserves the previous validated generation before atomically publishing the new file. A missing or corrupt primary can fall back to that generation; a corrupt file cannot overwrite the good recovery copy. **More → Recover previous autosave** offers an explicit restoration for the current project. Dismissing the dialog leaves the edit unchanged. Backgrounding attempts to flush the newest snapshot. Abrupt termination can still lose changes newer than the last completed save. An automatic previous-generation recovery is disclosed on startup. Source media must remain accessible.

## Captions and speech

**Captions → Speech language** offers Auto / Taglish, English and Filipino. Auto is recommended for mixed speech. Language hints are passed to the bundled multilingual recognizer and retained in paused speech jobs; they do not guarantee transcription accuracy.

**Follow spoken words** advances the highlighted word in both preview and MP4. Word boundaries are estimated inside existing caption timestamps, not forced audio alignment. **Caption timing** allows source in/out corrections, line preview and splitting one line into individually editable word cues. Line preview converts source time through the selected cut’s trim, speed and timeline offset, clamping inside that cut. Listen and correct estimated timing. Repeated names or Taglish words can be replaced literally as whole words in the selected transcript. Text is not automatically translated.

## Reusable presets

In **More → My editing presets**, name and save the selected cut's settings. A preset retains reference frame/canvas, caption style/font/colors/word mode, video grade, motion, boundary transition, hook suggestion tone and SFX settings. Applying replaces the selected cut's corresponding settings; caption/canvas settings remain project-wide. Source video, transcript, hook wording, music track, speed and export approval are not copied. Sound anchors are remapped relative to the target cut and out-of-range cues are omitted. Imported sounds remain references to local sound files. Up to 20 presets are stored on this phone.

## Highlight suggestions

Speech ranking includes preceding context for short continuations, favors sentence endings, scores speech coverage against actual candidate duration and penalizes pauses longer than three seconds. Candidate cards explain the local signals. Review the opening/context and suggested trim; sentence punctuation can be wrong. Up to 15 non-overlapping 15–30 second candidates remain unapproved. Frame activity remains a separate visual-change proxy. The app does not predict views or understand every visual scene.

## Export queue

Preview each edit, complete any active campaign checklist and approve it. In Export, choose **Add approved edit to queue** to freeze that reviewed snapshot. Current-edit approval then clears. Add other reviewed edits, then explicitly press **Start approved queue**. Up to ten snapshots render sequentially; later editor changes do not alter an already queued snapshot.

Keep the app open while the queue runs. **Stop queue**, leaving the app or process interruption stops unfinished processing. Unfinished/restored snapshots require **Load for review** (which creates an independent draft), approval and re-adding before another attempt. Completed entries retain their MP4 link. Queue loading never starts an export automatically. Missing source/audio files or a failed render stop the batch. Open completed MP4s from the queue or Gallery. Remove old queue entries when finished.

## Campaign checklist

Required tags, posting caption, entered duration rules and source-footage requirement now save with each project rather than being shared across restored drafts. The checklist appears at the export approval gate. Source/rights/duplicate-submission confirmations clear on restoration and must be checked again. The posting caption is separate from burned-in captions. These are checks of user-entered rules, not platform eligibility, geography, rights or payout verification.

## Storage and scope

Presets and export queue metadata are local. Existing metadata backups include project settings and campaign brief through the project snapshot, but do not include presets, queue entries, source media or downloaded sound assets. Preserve original media and signing recovery separately. No cloud AI provider or new paid service is introduced.

Build/test results belong to the final release record after verification; this guide does not claim that an unfinished build passed.
