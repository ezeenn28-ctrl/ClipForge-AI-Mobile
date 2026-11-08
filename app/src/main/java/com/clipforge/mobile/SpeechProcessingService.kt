package com.clipforge.mobile

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** User-started local media processing. No exports, uploads or automatic boot starts. */
class SpeechProcessingService : Service() {
    private var worker: Thread? = null
    @Volatile private var wakeLock: PowerManager.WakeLock? = null
    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Offline video processing", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) { SpeechJobs.pause(); return START_NOT_STICKY }
        try {
            val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(SpeechJobs.state.value), type)
        } catch (error: Exception) {
            SpeechJobs.fail(this, error.message ?: "Foreground processing could not start"); stopSelf(); return START_NOT_STICKY
        }
        if (worker?.isAlive == true || SpeechJobs.workerActive.get()) return START_NOT_STICKY
        worker = Thread({
            val power = getSystemService(PowerManager::class.java)
            try {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:offline-speech").apply {
                    setReferenceCounted(false); acquire(6L * 60 * 60 * 1000)
                }
                SpeechJobs.run(applicationContext, { state ->
                    runCatching { manager.notify(NOTIFICATION_ID, notification(state)) }
                }, {
                    Build.VERSION.SDK_INT >= 29 && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
                })
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
                Handler(mainLooper).post {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf(); worker = null
                }
            }
        }, "ClipForge-offline-speech").apply { start() }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        SpeechJobs.pause()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf()
    }
    override fun onDestroy() {
        if (worker?.isAlive == true) SpeechJobs.pause()
        super.onDestroy()
    }
    private fun notification(state: SpeechJobs.State): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, SpeechProcessingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val job = state.job
        val total = job?.units()?.size ?: 0
        val done = job?.results?.size ?: 0
        val percentage = if (total == 0) 0 else ((done * 100 + state.percent.coerceIn(0, 99)) / total).coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_processing)
            .setContentTitle("ClipForge · offline transcription")
            .setContentText("$done/$total windows saved · ${state.phase}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percentage, total == 0)
            .addAction(0, "Pause after window", pause).build()
    }
    companion object {
        private const val CHANNEL = "clipforge-offline-processing"
        private const val NOTIFICATION_ID = 702
        const val ACTION_PAUSE = "com.clipforge.mobile.PAUSE_SPEECH"
    }
}
