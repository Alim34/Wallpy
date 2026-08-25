package com.example.wallpaperchanger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WallpaperWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val intent = Intent(context, WallpaperWidget::class.java).apply {
                action = ACTION_CHANGE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetButton, pendingIntent)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHANGE) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (prefs.getString(MainActivity.KEY_FOLDER, null) == null) {
                Toast.makeText(context, R.string.pick_folder_first, Toast.LENGTH_SHORT).show()
                return
            }
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
            Toast.makeText(context, R.string.changing, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_CHANGE = "com.example.wallpaperchanger.CHANGE_WALLPAPER"
    }
}
