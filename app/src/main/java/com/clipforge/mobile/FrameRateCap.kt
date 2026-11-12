package com.clipforge.mobile

import android.content.Context
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.PassthroughShaderProgram

/** Time-grid budget, rather than rounding a fixed source/target frame ratio. Never duplicates frames. */
class FrameBudget(private val fps:Float){
    private var origin:Long?=null;private var lastSlot= -1L
    init{require(fps.isFinite()&&fps in 1f..240f)}
    fun accept(timestampUs:Long):Boolean {
        val start=origin?:timestampUs.also{origin=it}
        // Two microseconds accommodate integer presentation-time rounding at exact grid edges.
        val slot=(((timestampUs-start).coerceAtLeast(0)+2)*fps.toDouble()/1_000_000).toLong()
        if(slot<=lastSlot)return false
        lastSlot=slot;return true
    }
    fun reset(){origin=null;lastSlot= -1}
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FrameRateCap(private val fps:Float):GlEffect {
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram=object:PassthroughShaderProgram(){
        private val budget=FrameBudget(fps)
        override fun queueInputFrame(provider:GlObjectsProvider,texture:GlTextureInfo,presentationTimeUs:Long){
            if(budget.accept(presentationTimeUs))super.queueInputFrame(provider,texture,presentationTimeUs)
            else{inputListener.onInputFrameProcessed(texture);inputListener.onReadyToAcceptInputFrame()}
        }
        // Composition timestamps continue across cuts; keep the budget across input-stream boundaries.
        override fun flush(){super.flush();budget.reset()}
        override fun release(){super.release();budget.reset()}
    }
}
