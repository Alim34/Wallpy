package com.example.wallpaperchanger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private lateinit var folderText: TextView
    private lateinit var statusText: TextView

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        folderText = findViewById(R.id.folderText)
        statusText = findViewById(R.id.statusText)
        val intervalInput = findViewById<EditText>(R.id.intervalInput)

        intervalInput.setText(prefs.getLong(KEY_INTERVAL, 60L).toString())

        findViewById<Button>(R.id.pickFolderButton).setOnClickListener {
            pickFolder.launch(null)
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (prefs.getString(KEY_FOLDER, null) == null) {
                Toast.makeText(this, R.string.pick_folder_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val minutes = (intervalInput.text.toString().toLongOrNull() ?: 60L)
                .coerceAtLeast(15L) // минимум WorkManager — 15 минут
            intervalInput.setText(minutes.toString())
            prefs.edit()
                .putLong(KEY_INTERVAL, minutes)
                .putBoolean(KEY_RUNNING, true)
                .apply()

            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
                minutes, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
            updateUi()
            Toast.makeText(this, R.string.started, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
            prefs.edit().putBoolean(KEY_RUNNING, false).apply()
            updateUi()
            Toast.makeText(this, R.string.stopped, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.changeNowButton).setOnClickListener {
            if (prefs.getString(KEY_FOLDER, null) == null) {
                Toast.makeText(this, R.string.pick_folder_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            WorkManager.getInstance(this)
                .enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
            Toast.makeText(this, R.string.changing, Toast.LENGTH_SHORT).show()
        }

        updateUi()
    }

    private fun updateUi() {
        val folder = prefs.getString(KEY_FOLDER, null)
        folderText.text = if (folder != null) {
            getString(R.string.folder_selected, Uri.parse(folder).lastPathSegment ?: folder)
        } else {
            getString(R.string.folder_not_selected)
        }
        statusText.text = if (prefs.getBoolean(KEY_RUNNING, false)) {
            getString(R.string.status_running, prefs.getLong(KEY_INTERVAL, 60L))
        } else {
            getString(R.string.status_stopped)
        }
    }

    companion object {
        const val KEY_FOLDER = "folder_uri"
        const val KEY_INTERVAL = "interval_minutes"
        const val KEY_RUNNING = "running"
        const val WORK_NAME = "wallpaper_change"
    }
}
