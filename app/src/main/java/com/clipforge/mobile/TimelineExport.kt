package com.clipforge.mobile

import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object TimelineExport {
    fun frameProcessorFactory() = DefaultVideoFrameProcessor.Factory.Builder()
        .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL).build()
    fun audioProcessors(clip: TimelineClip): List<AudioProcessor> = buildList {
        add(ClipAudioProcessor(clip))
        if (clip.speed != 1f) {
            add(ToInt16PcmAudioProcessor())
            add(SonicAudioProcessor().apply { setSpeed(clip.speed); setPitch(1f) })
        }
    }
    fun composition(source: Uri, clips: List<TimelineClip>, vertical: Boolean, fillFrame: Boolean,
                    mute: Boolean, subtitleSize: Float, subtitleColor: Int, music: MusicTrack? = null, preset: CaptionPreset = CaptionPreset.BOLD, appearance: CaptionAppearance = CaptionAppearance(), soundAssets: Map<SoundKind, String> = emptyMap()): Composition {
        appearance.validate()
        require(clips.isNotEmpty()) { "Add a timeline clip before exporting" }
        val renderAppearance = if (vertical) appearance else appearance.copy(layout = ReferenceLayout.STANDARD)
        val reference = renderAppearance.layout
        val items = clips.mapIndexed { index, clip ->
            val incoming = clips.getOrNull(index - 1)?.transition
            val outgoing = clip.transition.takeIf { index < clips.lastIndex }
            val media = MediaItem.Builder().setUri(Uri.parse(clip.resolvedSource(source.toString()))).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build()
            val effects = mutableListOf<Effect>().apply {
                addAll(creativeEffects(if (clip.effect == "Monochrome") "None" else clip.effect))
                val grade = VideoLooks.color(clip.effect, clip.color)
                if (grade != ColorAdjustments() || clip.effect == "Monochrome") {
                    val matrix = grade.exportMatrix(clip.effect == "Monochrome")
                    add(RgbMatrix { _, _ -> matrix })
                }
                if (reference.videoAspect != null) add(Presentation.createForAspectRatio(reference.videoAspect, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                add(Presentation.createForWidthAndHeight(if (vertical) 1080 else 1920, if (vertical) 1920 else 1080,
                    if (fillFrame && reference == ReferenceLayout.STANDARD) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
                if (incoming?.kind == CutTransitionKind.ZOOM_CUT || outgoing?.kind == CutTransitionKind.ZOOM_CUT)
                    add(ClipVisualEffects.transitionMotion(clip, incoming, outgoing))
                if (clip.visuals.start != MotionPoint() || clip.visuals.end != MotionPoint()) add(ClipVisualEffects.motion(clip))
                if (reference != ReferenceLayout.STANDARD || clip.hook.isNotBlank() || clip.caption.isNotBlank() || clip.subtitles.isNotEmpty()) {
                    val overlay = if (clip.subtitles.isNotEmpty()) segmentSubtitleOverlay(clip.hook, clip.subtitles, clip.startMs, subtitleSize, subtitleColor, calibrateClock = true, preset = preset, appearance = renderAppearance)
                    else timedCaptionOverlay(clip.hook, clip.caption, clip.captionStartMs, clip.captionEndMs, clip.startMs, calibrateClock = true, subtitleSize = subtitleSize, subtitleColor = subtitleColor, preset = preset, appearance = renderAppearance)
                    add(OverlayEffect(listOf(overlay)))
                }
                if (clip.visuals.fadeInMs > 0 || clip.visuals.fadeOutMs > 0) add(ClipVisualEffects.fade(clip))
                if (incoming?.kind == CutTransitionKind.DIP_BLACK || outgoing?.kind == CutTransitionKind.DIP_BLACK)
                    add(ClipVisualEffects.transitionFade(clip, incoming, outgoing))
                // Overlays evaluate source timestamps; retime the completed picture last.
                if (clip.speed != 1f) add(SpeedChangeEffect(clip.speed))
            }
            EditedMediaItem.Builder(media).setRemoveAudio(mute).setEffects(Effects(
                if (mute) emptyList() else audioProcessors(clip), effects)).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(items).build())
        music?.let { track ->
            track.validate()
            val total = TimelineModel.duration(clips)
            val active = track.activeDuration(total)
            if (active > 0) {
                val period = track.endMs - track.startMs
                require(!track.loop || (active + period - 1) / period <= 1500) { "Music loop is too short for this timeline" }
                val activity = if (mute) emptyList() else AudioPlanning.activity(clips)
                val sequence = EditedMediaItemSequence.Builder()
                if (track.timelineStartMs > 0) sequence.addGap(track.timelineStartMs * 1000)
                var cursor = 0L
                while (cursor < active) {
                    val length = minOf(period, active - cursor)
                    val localOffset = cursor
                    val media = MediaItem.Builder().setUri(Uri.parse(track.source)).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(track.startMs).setEndPositionMs(track.startMs + length).build()).build()
                    val processor = AudioDspProcessor { sample, ms -> sample * track.gain(track.timelineStartMs + localOffset + ms, total, activity) }
                    sequence.addItem(EditedMediaItem.Builder(media).setRemoveVideo(true).setEffects(Effects(listOf(processor), emptyList())).build())
                    cursor += length
                    if (!track.loop) break
                }
                sequences.add(sequence.build())
            }
        }
        val sounds = SoundTimeline.events(clips)
        if (sounds.isNotEmpty()) {
            val sequence = EditedMediaItemSequence.Builder()
            var cursor = 0L
            sounds.forEach { sound ->
                val uri = sound.asset?.source ?: soundAssets[sound.kind] ?: error("Sound effect asset is missing")
                if (sound.startMs > cursor) sequence.addGap((sound.startMs - cursor) * 1000)
                val media = MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setEndPositionMs(sound.durationMs).build()).build()
                val envelope = TimelineClip("sfx", 0, sound.durationMs, volume = sound.volume,
                    fadeInMs = minOf(20, sound.durationMs), fadeOutMs = minOf(20, sound.durationMs))
                sequence.addItem(EditedMediaItem.Builder(media).setRemoveVideo(true)
                    .setEffects(Effects(listOf(ClipAudioProcessor(envelope)), emptyList())).build())
                cursor = sound.endMs
            }
            sequences.add(sequence.build())
        }
        return Composition.Builder(sequences).experimentalSetForceAudioTrack(!mute || music != null || sounds.isNotEmpty()).build()
    }
}




