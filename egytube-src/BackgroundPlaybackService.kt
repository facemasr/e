package com.personal.ytplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground lifecycle notification for direct, authorized Media3 playback.
 * The Media3 player remains owned by the Activity while its process is kept active.
 * This service is intentionally NOT invoked for YouTube extracted streams or embeds.
 */
internal class BackgroundPlaybackService:Service() {
    companion object {
        const val ACTION_START="com.personal.ytplayer.PLAYBACK_START"
        const val ACTION_STOP="com.personal.ytplayer.PLAYBACK_STOP"
        private const val CHANNEL="egytube_allowed_background"
        private const val NOTIFICATION=16172
    }
    override fun onCreate(){
        super.onCreate()
        val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(
            NotificationChannel(CHANNEL,"تشغيل الفيديو في الخلفية",NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_STOP){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY}
        val title=intent?.getStringExtra("title").orEmpty().take(60).ifBlank{"فيديو مسموح"}
        val open=PendingIntent.getActivity(this,0,
            Intent(this,MainActivity::class.java).apply{
                flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("EGYTUBE • تشغيل في الخلفية")
            .setContentText(title)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION,notification)
        return START_NOT_STICKY
    }
    override fun onBind(intent:Intent?):IBinder?=null
}
