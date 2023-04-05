package com.example.monitoringapp.service

import android.Manifest
import android.app.* // ktlint-disable no-wildcard-imports
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.* // ktlint-disable no-wildcard-imports
import android.os.Binder
import android.os.Build
import android.os.FileObserver
import android.os.FileObserver.* // ktlint-disable no-wildcard-imports
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.monitoringapp.LogActivity
import com.example.monitoringapp.R

/*This code defines a FileMonitorService class that extends the Service class in Android. When this service is started,
 it monitors the data directories of all installed apps and notifies the user when any of them are accessed. Specifically,
 it uses a FileObserver to watch for file events like file creation, opening, and writing. When such an event occurs,
 the service sends a notification to the user. To trigger this service from an activity, you need to start the service using an Intent. You can use the following code to start the service:
 kotlin
 */

class FileMonitorService : Service() {

    private lateinit var fileObserver: FileObserver
    private lateinit var notificationManager: NotificationManagerCompat

    companion object {
        private const val CHANNEL_ID = "file_monitor_channel"
        private const val NOTIFICATION_ID = 1
    }

    // Initialize an empty list to keep track of the accessed folders
    private val accessedFolders = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? {
        return LocalBinder()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()

        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        // Get a list of all installed apps
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Monitor the data directories of all installed apps
        for (packageInfo in packages) {
            val packageName = packageInfo.packageName
            val dataDir = packageInfo.dataDir

            if (packageName != applicationContext.packageName && dataDir != null) {
                fileObserver = object : FileObserver(dataDir, ALL_EVENTS) {
                    override fun onEvent(event: Int, path: String?) {
                        if (event and (CREATE or OPEN or CLOSE_WRITE) != 0) {
                            val folderPath = "$packageName/$path"
                            Log.d("FileMonitor", "$folderPath has been accessed")
                            sendNotification("$folderPath has been accessed")
                            // Add the accessed folder to the list
                            accessedFolders.add(folderPath)
                        }
                    }
                }

                fileObserver.startWatching()
            }
        }

        val permission = Manifest.permission.FOREGROUND_SERVICE
        val res = checkSelfPermission(permission)
        if (res != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }

        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
                .setContentTitle("File Monitor is running")
                .setContentText("Monitoring file access")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver.stopWatching()
    }

    private fun createNotificationChannel() {
        val name = "File Monitor"
        val descriptionText = "Monitors file access"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun sendNotification(message: String) {
        val intent = Intent(this, LogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
            .setContentTitle("File Access Detected")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(notificationManager) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    // Add a method to return the accessed folders list
    fun getAccessedFolders(): List<String> {
        return accessedFolders
    }

    inner class LocalBinder : Binder() {
        fun getService(): FileMonitorService = this@FileMonitorService
    }
}
