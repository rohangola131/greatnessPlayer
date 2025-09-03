package com.example.dualaudioplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var oshoPlayer: MediaPlayer
    private lateinit var musicPlayer: MediaPlayer
    private lateinit var oshoListView: ListView
    private lateinit var musicListView: ListView
    private lateinit var oshoSeekBar: SeekBar
    private lateinit var musicSeekBar: SeekBar
    private lateinit var oshoPlayPause: ImageButton
    private lateinit var musicPlayPause: ImageButton
    private lateinit var oshoPrevious: ImageButton
    private lateinit var oshoNext: ImageButton
    private lateinit var musicPrevious: ImageButton
    private lateinit var musicNext: ImageButton
    private lateinit var selectOshoFolder: Button
    private lateinit var selectMusicFolder: Button
    private lateinit var oshoCurrentTrack: TextView
    private lateinit var musicCurrentTrack: TextView
    
    private var oshoFiles = mutableListOf<DocumentFile>()
    private var musicFiles = mutableListOf<DocumentFile>()
    private var currentOshoIndex = 0
    private var currentMusicIndex = 0
    private var isOshoPlaying = false
    private var isMusicPlaying = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBar = object : Runnable {
        override fun run() {
            try {
                if (::oshoPlayer.isInitialized && oshoPlayer.isPlaying) {
                    oshoSeekBar.progress = oshoPlayer.currentPosition
                }
                if (::musicPlayer.isInitialized && musicPlayer.isPlaying) {
                    musicSeekBar.progress = musicPlayer.currentPosition
                }
                handler.postDelayed(this, 1000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val OSHO_FOLDER_REQUEST = 200
        private const val MUSIC_FOLDER_REQUEST = 201
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        requestPermissions()
        setupListeners()
        
        // Initialize MediaPlayers
        oshoPlayer = MediaPlayer()
        musicPlayer = MediaPlayer()
        
        // Start the seek bar update
        handler.post(updateSeekBar)
    }
    
    private fun initViews() {
        oshoListView = findViewById(R.id.oshoListView)
        musicListView = findViewById(R.id.musicListView)
        oshoSeekBar = findViewById(R.id.oshoSeekBar)
        musicSeekBar = findViewById(R.id.musicSeekBar)
        oshoPlayPause = findViewById(R.id.oshoPlayPause)
        musicPlayPause = findViewById(R.id.musicPlayPause)
        oshoPrevious = findViewById(R.id.oshoPrevious)
        oshoNext = findViewById(R.id.oshoNext)
        musicPrevious = findViewById(R.id.musicPrevious)
        musicNext = findViewById(R.id.musicNext)
        selectOshoFolder = findViewById(R.id.selectOshoFolder)
        selectMusicFolder = findViewById(R.id.selectMusicFolder)
        oshoCurrentTrack = findViewById(R.id.oshoCurrentTrack)
        musicCurrentTrack = findViewById(R.id.musicCurrentTrack)
    }
    
    private fun setupListeners() {
        selectOshoFolder.setOnClickListener { openFolderPicker(OSHO_FOLDER_REQUEST) }
        selectMusicFolder.setOnClickListener { openFolderPicker(MUSIC_FOLDER_REQUEST) }
        
        oshoPlayPause.setOnClickListener { toggleOshoPlayPause() }
        musicPlayPause.setOnClickListener { toggleMusicPlayPause() }
        
        oshoPrevious.setOnClickListener { playPreviousOsho() }
        oshoNext.setOnClickListener { playNextOsho() }
        musicPrevious.setOnClickListener { playPreviousMusic() }
        musicNext.setOnClickListener { playNextMusic() }
        
        oshoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::oshoPlayer.isInitialized) {
                    oshoPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::musicPlayer.isInitialized) {
                    musicPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        oshoListView.setOnItemClickListener { _, _, position, _ ->
            currentOshoIndex = position
            playOshoTrack()
        }
        
        musicListView.setOnItemClickListener { _, _, position, _ ->
            currentMusicIndex = position
            playMusicTrack()
        }
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK
        )
        
        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun openFolderPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, requestCode)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            when (requestCode) {
                OSHO_FOLDER_REQUEST -> loadOshoFiles(uri)
                MUSIC_FOLDER_REQUEST -> loadMusicFiles(uri)
            }
        }
    }
    
    private fun loadOshoFiles(uri: Uri) {
        oshoFiles.clear()
        val folder = DocumentFile.fromTreeUri(this, uri)
        folder?.listFiles()?.filter { it.isFile && isAudioFile(it.name) }?.let {
            oshoFiles.addAll(it)
        }
        updateOshoList()
    }
    
    private fun loadMusicFiles(uri: Uri) {
        musicFiles.clear()
        val folder = DocumentFile.fromTreeUri(this, uri)
        folder?.listFiles()?.filter { it.isFile && isAudioFile(it.name) }?.let {
            musicFiles.addAll(it)
        }
        updateMusicList()
    }
    
    private fun isAudioFile(name: String?): Boolean {
        val audioExtensions = listOf(".mp3", ".m4a", ".wav", ".ogg", ".flac")
        return audioExtensions.any { name?.lowercase()?.endsWith(it) == true }
    }
    
    private fun updateOshoList() {
        val adapter = ArrayAdapter(
            this, 
            android.R.layout.simple_list_item_1, 
            oshoFiles.map { it.name ?: "Unknown" }
        )
        oshoListView.adapter = adapter
    }
    
    private fun updateMusicList() {
        val adapter = ArrayAdapter(
            this, 
            android.R.layout.simple_list_item_1, 
            musicFiles.map { it.name ?: "Unknown" }
        )
        musicListView.adapter = adapter
    }
    
    private fun toggleOshoPlayPause() {
        if (oshoFiles.isEmpty()) return
        
        if (isOshoPlaying) {
            pauseOsho()
        } else {
            if (!::oshoPlayer.isInitialized || !oshoPlayer.isPlaying) {
                playOshoTrack()
            } else {
                resumeOsho()
            }
        }
    }
    
    private fun toggleMusicPlayPause() {
        if (musicFiles.isEmpty()) return
        
        if (isMusicPlaying) {
            pauseMusic()
        } else {
            if (!::musicPlayer.isInitialized || !musicPlayer.isPlaying) {
                playMusicTrack()
            } else {
                resumeMusic()
            }
        }
    }
    
    private fun playOshoTrack() {
        if (oshoFiles.isEmpty()) return
        
        try {
            oshoPlayer.reset()
            val uri = oshoFiles[currentOshoIndex].uri
            oshoPlayer.setDataSource(this, uri)
            oshoPlayer.prepare()
            oshoPlayer.start()
            
            oshoSeekBar.max = oshoPlayer.duration
            oshoCurrentTrack.text = oshoFiles[currentOshoIndex].name
            oshoPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            isOshoPlaying = true
            
            oshoPlayer.setOnCompletionListener { playNextOsho() }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error playing Osho track", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playMusicTrack() {
        if (musicFiles.isEmpty()) return
        
        try {
            musicPlayer.reset()
            val uri = musicFiles[currentMusicIndex].uri
            musicPlayer.setDataSource(this, uri)
            musicPlayer.prepare()
            musicPlayer.start()
            
            musicSeekBar.max = musicPlayer.duration
            musicCurrentTrack.text = musicFiles[currentMusicIndex].name
            musicPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            isMusicPlaying = true
            
            musicPlayer.setOnCompletionListener { playNextMusic() }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error playing music track", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun pauseOsho() {
        if (::oshoPlayer.isInitialized && oshoPlayer.isPlaying) {
            oshoPlayer.pause()
            oshoPlayPause.setImageResource(android.R.drawable.ic_media_play)
            isOshoPlaying = false
        }
    }
    
    private fun resumeOsho() {
        if (::oshoPlayer.isInitialized) {
            oshoPlayer.start()
            oshoPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            isOshoPlaying = true
        }
    }
    
    private fun pauseMusic() {
        if (::musicPlayer.isInitialized && musicPlayer.isPlaying) {
            musicPlayer.pause()
            musicPlayPause.setImageResource(android.R.drawable.ic_media_play)
            isMusicPlaying = false
        }
    }
    
    private fun resumeMusic() {
        if (::musicPlayer.isInitialized) {
            musicPlayer.start()
            musicPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            isMusicPlaying = true
        }
    }
    
    private fun playPreviousOsho() {
        if (oshoFiles.isNotEmpty()) {
            currentOshoIndex = if (currentOshoIndex > 0) currentOshoIndex - 1 else oshoFiles.size - 1
            playOshoTrack()
        }
    }
    
    private fun playNextOsho() {
        if (oshoFiles.isNotEmpty()) {
            currentOshoIndex = (currentOshoIndex + 1) % oshoFiles.size
            playOshoTrack()
        }
    }
    
    private fun playPreviousMusic() {
        if (musicFiles.isNotEmpty()) {
            currentMusicIndex = if (currentMusicIndex > 0) currentMusicIndex - 1 else musicFiles.size - 1
            playMusicTrack()
        }
    }
    
    private fun playNextMusic() {
        if (musicFiles.isNotEmpty()) {
            currentMusicIndex = (currentMusicIndex + 1) % musicFiles.size
            playMusicTrack()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::oshoPlayer.isInitialized) {
            oshoPlayer.release()
        }
        if (::musicPlayer.isInitialized) {
            musicPlayer.release()
        }
        handler.removeCallbacks(updateSeekBar)
    }
}
