package com.clipforge.mobile

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One-time in-memory authorization issued only by an explicit approved UI action. */
object BackgroundExports {
    data class State(val active:Boolean=false,val progress:Int=0,val message:String="",val output:String="",val project:EditorProject?=null,val queue:List<QueuedExport> = emptyList())
    private val mutable=MutableStateFlow(State());val state=mutable.asStateFlow()
    private var pending:Pair<String,List<QueuedExport>>?=null
    @Synchronized fun start(context:Context,projects:List<QueuedExport>,approved:Boolean){
        require(approved && projects.isNotEmpty()&&projects.size<=10){"Review and approve before starting export"}
        check(!state.value.active&&pending==null){"An export is already running"}
        projects.forEach{it.project.validate()}
        val token=ProjectCatalog.newId();pending=token to projects.toList();mutable.value=State(active=true,message="Starting approved export",queue=projects)
        try{ContextCompat.startForegroundService(context,Intent(context,BackgroundExportService::class.java).putExtra("token",token))}
        catch(e:Exception){pending=null;mutable.value=State(message="Export could not start: ${e.message}");throw e}
    }
    @Synchronized fun consume(token:String?):List<QueuedExport>? {val p=pending?:return null;if(p.first!=token)return null;pending=null;return p.second}
    fun update(s:State){mutable.value=s}
    fun cancel(context:Context){context.startService(Intent(context,BackgroundExportService::class.java).setAction("cancel"))}
    fun recovery(root:File)=ProjectStore.read(File(root,"background-export.properties"))?.reviewedAgain()
}
class BackgroundExportService:Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);private var work:Job?=null
    private val manager get()=getSystemService(NotificationManager::class.java)
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){super.onCreate();manager.createNotificationChannel(NotificationChannel("clipforge-export","Video exports",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="cancel"){work?.cancel();if(work==null)stopSelf();return START_NOT_STICKY}
        val items=BackgroundExports.consume(intent?.getStringExtra("token"))?:run{if(work==null)stopSelf();return START_NOT_STICKY}
        try{val type=if(Build.VERSION.SDK_INT>=35)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            ServiceCompat.startForeground(this,2002,notification("Preparing approved clip",0),type)
        }catch(e:Exception){BackgroundExports.update(BackgroundExports.State(message="Export failed to start: ${e.message}"));stopSelf();return START_NOT_STICKY}
        work=scope.launch {
            val queue=items.toMutableList();var latest="";var project:EditorProject?=null;var wake:PowerManager.WakeLock?=null
            try{
                wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"$packageName:approved-export").apply{acquire(6*60*60*1000L)}
                for((index,item) in items.withIndex()){
                    project=item.project
                    withContext(Dispatchers.IO){ProjectStore.write(File(filesDir,"background-export.properties"),item.project)}
                    queue[index]=item.copy(status=QueueStatus.RENDERING,message="Rendering approved snapshot")
                    val result=QueueExporter.render(applicationContext,item.project){percent->
                        val message="Export ${index+1}/${items.size} · $percent%"
                        BackgroundExports.update(BackgroundExports.State(true,percent,message,latest,item.project,queue.toList()));manager.notify(2002,notification(message,percent))
                    }
                    latest=result.uri.toString();queue[index]=item.copy(status=QueueStatus.COMPLETE,message=if(result.inGallery)"Saved to Gallery" else "Saved privately",uri=latest)
                    if(item.id!="direct")withContext(Dispatchers.IO){ExportQueue.record(filesDir,queue[index])}
                }
                File(filesDir,"background-export.properties").let{it.delete();ProjectStore.previousFile(it).delete()}
                BackgroundExports.update(BackgroundExports.State(false,100,"Export complete · Saved to Gallery or private exports",latest,project,queue.toList()))
            }catch(e:CancellationException){BackgroundExports.update(BackgroundExports.State(message="Export cancelled. Load interrupted export and review before retrying.",output=latest,project=project,queue=queue.map{if(it.status in listOf(QueueStatus.READY,QueueStatus.RENDERING))it.copy(status=QueueStatus.REVIEW,message="Cancelled: load and review again") else it}))}
            catch(e:Exception){BackgroundExports.update(BackgroundExports.State(message="Export failed: ${e.message}. Load interrupted export and review before retrying.",output=latest,project=project,queue=queue.map{if(it.status in listOf(QueueStatus.READY,QueueStatus.RENDERING))it.copy(status=QueueStatus.REVIEW,message="Stopped: load and review again. ${e.message}") else it}))}
            finally{wake?.let{if(it.isHeld)it.release()};ServiceCompat.stopForeground(this@BackgroundExportService,ServiceCompat.STOP_FOREGROUND_REMOVE);work=null;stopSelf()}
        };return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int){work?.cancel()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    private fun notification(message:String,percent:Int):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel=PendingIntent.getService(this,2,Intent(this,BackgroundExportService::class.java).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,"clipforge-export").setSmallIcon(R.drawable.ic_processing).setContentTitle("ClipForge · approved export").setContentText(message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,percent,percent==0).addAction(0,"Cancel",cancel).build()
    }
}
