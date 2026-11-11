# Auto Audio implementation checkpoint

Base: released v1.4.0 Build131, owner reported all phone tests work.

Current draft is NOT built or verified and must not be delivered as an APK.

Authorized scope: local automatic background music/SFX drafts, seven original procedural music moods, user-imported music, audition/favorites/provenance, looping/fades, dialogue-activity ducking, per-cut voice leveling/soft limiter and peak warnings, beat markers, silence suggestions with explicit removal, caption proofreading, preview-only safe areas and before/after look/mix, batch preset styling, posting package, storage estimates/safe temporary cleanup, and richer backup with optional media copies. Existing export review remains mandatory; no cloud uploads or automatic MP4 exports.

Saved core: AutoAudio.kt models/planning/PCM inspection, MusicLibrary.kt original synthesis, AudioDspProcessor.kt, MusicTrack loop/duck/metadata fields, per-cut voiceGain/audioProfile and ProjectStore serialization.

Remaining: integrate preview/export DSP and looping, add UI/automatic-on-new-clips policy and undo, editing helpers, backup extension, meaningful unit/media/signed-APK tests; version/build/deliver only after actual checks. Preserve previous projects by defaulting their Auto Audio flag off. Explicit re-generation must preserve existing music/SFX unless the owner chooses replacement. Automatic generation must not rerun after Undo on the same handled clip IDs. New audio activity ranges are SOURCE-time anchors and must map through trim/speed/reordering. Preview/export must use the same gain rules. Source/media must remain local.

Build status: no Auto Audio CI run yet. Last delivered APK remains v1.4.0.
