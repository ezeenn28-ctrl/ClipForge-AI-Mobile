package com.clipforge.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.media.FaceDetector
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.effect.MatrixTransformation
import androidx.media3.common.util.Size
import kotlin.math.abs

/** Sparse, local face-first framing. Motion fallback is a guide, never face identification. */
object SubjectAnalyzer {
    data class Result(val framing:SubjectFraming,val faces:Int,val samples:Int)
    @Suppress("DEPRECATION") fun analyze(context:Context,clip:TimelineClip,primary:String,cancel:()->Unit):Result {
        val retriever=MediaMetadataRetriever();val points=mutableListOf<FramingPoint>();var facesFound=0;var previous:IntArray?=null
        try{
            retriever.setDataSource(context,Uri.parse(clip.resolvedSource(primary)))
            val step=maxOf(500L,clip.sourceSpanMs/119);var ms=clip.startMs
            while(ms<clip.endMs&&points.size<120){
                cancel();val raw=retriever.getScaledFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST,320,240)
                if(raw!=null){
                    val width=raw.width/2*2;val height=raw.height
                    val bmp=Bitmap.createScaledBitmap(raw,width,height,false).copy(Bitmap.Config.RGB_565,false)
                    val detected=arrayOfNulls<FaceDetector.Face>(5);val count=FaceDetector(width,height,5).findFaces(bmp,detected)
                    val face=detected.filterNotNull().take(count).maxByOrNull{it.eyesDistance()}
                    val pixels=IntArray(width*height);bmp.getPixels(pixels,0,width,0,0,width,height)
                    var x=.5f;var y=.5f
                    if(face!=null){val p=PointF();face.getMidPoint(p);x=p.x/width;y=(p.y+face.eyesDistance())/height;facesFound++}
                    else if(previous?.size==pixels.size){var sx=0.0;var sy=0.0;var sum=0.0;for(i in pixels.indices step 4){val a=pixels[i];val b=previous!![i];val d=abs((a shr 16 and 255)-(b shr 16 and 255))+abs((a shr 8 and 255)-(b shr 8 and 255))+abs((a and 255)-(b and 255));if(d>60){sx+=(i%width)*d.toDouble();sy+=(i/width)*d.toDouble();sum+=d}};if(sum>0){x=(sx/sum/width).toFloat();y=(sy/sum/height).toFloat()}}
                    val last=points.lastOrNull();if(last!=null){x=last.x*.7f+x*.3f;y=last.y*.7f+y*.3f}
                    points+=FramingPoint(ms,x.coerceIn(0f,1f),y.coerceIn(0f,1f));previous=pixels
                    bmp.recycle();raw.recycle()
                };ms+=step
            }
        }finally{retriever.release()}
        check(points.isNotEmpty()){"No frames could be decoded for framing"}
        return Result(SubjectFraming(points),facesFound,points.size)
    }
}
/** Same crop-window math is used by the live TextureView and exported GL frames. */
object FramingWindow {
    data class Window(val left:Float,val top:Float,val width:Float,val height:Float)
    fun at(f:SubjectFraming,ms:Long,sourceAspect:Float,targetAspect:Float=9f/16):Window {
        val w=minOf(1f,targetAspect/sourceAspect)/f.zoom;val h=minOf(1f,sourceAspect/targetAspect)/f.zoom
        val(x,y)=f.center(ms)
        return Window((x-w/2).coerceIn(0f,1-w),(y-h/2).coerceIn(0f,1-h),w,h)
    }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubjectFramingEffect(private val clip:TimelineClip):MatrixTransformation {
    private var aspect=1f;private val clock=ClipPresentationClock()
    override fun configure(inputWidth:Int,inputHeight:Int):Size {aspect=inputWidth.toFloat()/inputHeight;return Size((inputHeight*9f/16).toInt().coerceAtLeast(2),inputHeight)}
    override fun getMatrix(presentationTimeUs:Long):Matrix {
        val f=clip.editor.framing?:SubjectFraming();val w=FramingWindow.at(f,clip.startMs+clock.relativeUs(presentationTimeUs)/1000,aspect)
        return Matrix().apply{setScale(1/w.width,1/w.height);postTranslate((1-2*w.left-w.width)/w.width,-(1-2*w.top-w.height)/w.height)}
    }
}
