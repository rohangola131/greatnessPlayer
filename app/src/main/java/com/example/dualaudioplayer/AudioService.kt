package com.example.dualaudioplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AudioService : Service() {
    
    private var oshoPlayer: MediaPlayer? = null
    private var musicPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "dual_audio_player_channel"
        const val ACTION_PLAY_OSHO = "play_osho"
        const val ACTION_PAUSE_OSHO = "pause_osho"
        const val ACTION_PLAY_MUSIC = "play_music"
        const val ACTION_PAUSE_MUSIC = "pause_music"
        const val ACTION_STOP = "stop"
        const val EXTRA_OSHO_URI = "osho_uri"
        const val EXTRA_MUSIC_URI = "music_uri"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Acquire wake lock to keep playing in background
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DualAudioPlayer::AudioServiceLock"
        )
        wakeLock?.acquire(10*60*1000L) // 10 minutes
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_OSHO -> {
                val uri = intent.getStringExtra(EXTRA_OSHO_URI)
                uri?.let { playOsho(it) }
            }
            ACTION_PAUSE_OSHO -> pauseOsho()
            ACTION_PLAY_MUSIC -> {
                val uri = intent.getStringExtra(EXTRA_MUSIC_URI)
                uri?.let { playMusic(it) }
            }
            ACTION_PAUSE_MUSIC -> pauseMusic()
            ACTION_STOP -> stopSelf()
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    private fun playOsho(uriString: String) {
        try {
            oshoPlayer?.reset()
            oshoPlayer = MediaPlayer().apply {
                setDataSource(uriString)
                prepareAsync()
                setOnPreparedListener { start() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun pauseOsho() {
        oshoPlayer?.pause()
    }
    
    private fun playMusic(uriString: String) {
        try {
            musicPlayer?.reset()
            musicPlayer = MediaPlayer().apply {
                setDataSource(uriString)
                prepareAsync()
                setOnPreparedListener { start() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun pauseMusic() {
        musicPlayer?.pause()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dual Audio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dual Audio Player Service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Dual Audio Player")
        .setContentText("Playing audio in background")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
    
    override fun onDestroy() {
        super.onDestroy()
        oshoPlayer?.release()
        musicPlayer?.release()
        wakeLock?.release()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
