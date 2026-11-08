package com.clipforge.mobile

import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object TimelineExport {
    fun composition(source: Uri, clips: List<TimelineClip>, vertical: Boolean, fillFrame: Boolean,
                    mute: Boolean, subtitleSize: Float, subtitleColor: Int): Composition {
        require(clips.isNotEmpty()) { "Add a timeline clip before exporting" }
        val items = clips.map { clip ->
            val media = MediaItem.Builder().setUri(source).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build()
            val effects = mutableListOf<Effect>().apply {
                addAll(creativeEffects(clip.effect))
                if (vertical) add(Presentation.createForAspectRatio(9f / 16f,
                    if (fillFrame) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
                if (clip.hook.isNotBlank() || clip.caption.isNotBlank() || clip.subtitles.isNotEmpty()) {
                    val overlay = if (clip.subtitles.isNotEmpty()) segmentSubtitleOverlay(clip.hook, clip.subtitles, clip.startMs, subtitleSize, subtitleColor, calibrateClock = true)
                    else timedCaptionOverlay(clip.hook, clip.caption, clip.captionStartMs, clip.captionEndMs, clip.startMs, calibrateClock = true)
                    add(OverlayEffect(listOf(overlay)))
                }
            }
            EditedMediaItem.Builder(media).setRemoveAudio(mute).setEffects(Effects(
                if (mute) emptyList() else listOf(ClipAudioProcessor(clip)), effects)).build()
        }
        return Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .experimentalSetForceAudioTrack(!mute).build()
    }
}
