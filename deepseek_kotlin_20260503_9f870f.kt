package com.example.pdfaudioplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class AudioPlaybackService : Service() {

    // 定义操作常量
    companion object {
        const val ACTION_PLAY = "com.example.pdfaudioplayer.action.PLAY"
        const val ACTION_PAUSE = "com.example.pdfaudioplayer.action.PAUSE"
        const val ACTION_STOP = "com.example.pdfaudioplayer.action.STOP"
        const val ACTION_TOGGLE = "com.example.pdfaudioplayer.action.TOGGLE"
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 100
    }

    private lateinit var mediaPlayer: MediaPlayer
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener {
                start()
                updateNotification(true)
            }
            setOnCompletionListener {
                updateNotification(false)
            }
            setOnErrorListener { _, _, _ ->
                updateNotification(false)
                false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY -> playAudio(intent)
            ACTION_PAUSE -> pauseAudio()
            ACTION_STOP -> stopAudio()
            ACTION_TOGGLE -> togglePlayPause()
        }
    }

    fun loadAndPlay(uri: Uri) {
        mediaPlayer.reset()
        mediaPlayer.setDataSource(this, uri)
        mediaPlayer.prepareAsync()
        startForeground(NOTIFICATION_ID, createNotification(false))
    }

    fun playAudio(intent: Intent? = null) {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
        }
        updateNotification(true)
    }

    fun pauseAudio() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        updateNotification(false)
    }

    fun stopAudio() {
        mediaPlayer.stop()
        updateNotification(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) pauseAudio() else playAudio()
    }

    fun isPlaying(): Boolean = mediaPlayer.isPlaying

    private fun createNotificationChannel() {
        NotificationHelper.createNotificationChannel(this)
    }

    private fun createNotification(isPlaying: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("PDF音频播放器")
            .setContentText(if (isPlaying) "正在播放..." else "已暂停")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                android.R.drawable.ic_media_play,
                "播放",
                createActionIntent(ACTION_PLAY)
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                createActionIntent(ACTION_PAUSE)
            )
            .addAction(
                android.R.drawable.ic_media_previous,
                "停止",
                createActionIntent(ACTION_STOP)
            )
            .build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification(isPlaying: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(isPlaying))
    }

    override fun onDestroy() {
        mediaPlayer.release()
        super.onDestroy()
    }
}