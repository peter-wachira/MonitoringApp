package com.example.monitoringapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.widget.* // ktlint-disable no-wildcard-imports
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    private lateinit var packageInfo: PackageInfo
    private lateinit var permissions: Array<String>
    private var fileObserver: FileObserver? = null
    private var accessedFolders = mutableListOf<String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // Set up back button
        val backButton = findViewById<ImageButton>(R.id.back_button)
        val title = findViewById<TextView>(R.id.app_name)
        backButton.setOnClickListener {
            title.text = ""
            onBackPressed()
        }

        // Get the package name from the intent extra
        val packageName = intent.getStringExtra("packageName")
        title.text = packageName

        // Get the permissions for the selected app
        packageInfo = packageName?.let {
            packageManager.getPackageInfo(it, PackageManager.GET_PERMISSIONS)
        } ?: throw IllegalArgumentException("Package name cannot be null")

        permissions = packageInfo.requestedPermissions ?: emptyArray()

        // Create a TextView for each permission and add it to the log container
        val logContainer = findViewById<LinearLayout>(R.id.log_container)
        if (permissions.isNotEmpty()) {
            for (permission in permissions) {
                val permissionStatus = packageManager.checkPermission(permission, packageName)
                logMessage("$permission: ${getPermissionStatus(permissionStatus)}", logContainer)
            }
        }

        // Set up the block button to block app permissions
        val blockButton = findViewById<Button>(R.id.restrict_access_button)
        blockButton.setOnClickListener {
            for (permission in permissions) {
                if (isRestrictedPermission(permission)) {
                    continue
                }
//                setPermissionBlocked(packageInfo.packageName, permission)
            }
            Toast.makeText(this, "Permissions blocked for ${packageInfo.applicationInfo.loadLabel(packageManager)}", Toast.LENGTH_SHORT).show()
        }

        // Monitor the app's file access in the background
        fileObserver = packageInfo.applicationInfo.dataDir?.let {
            object : FileObserver(it, ALL_EVENTS) {
                override fun onEvent(event: Int, path: String?) {
                    if (event and (CREATE or FileObserver.DELETE or FileObserver.MODIFY) != 0) {
                        logMessage("File access: $path", logContainer)
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun isRestrictedPermission(permission: String): Boolean {
        return permission in arrayOf(
            Manifest.permission.WRITE_SETTINGS,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.INSTALL_PACKAGES,
            Manifest.permission.INSTALL_SHORTCUT,
        )
    }

    private fun getPermissionStatus(permissionStatus: Int): String {
        return when (permissionStatus) {
            PackageManager.PERMISSION_GRANTED -> "Granted"
            PackageManager.PERMISSION_DENIED -> "Denied"
            else -> "Unknown"
        }
    }

    private fun logMessage(message: String, logContainer: LinearLayout) {
        val logTextView = TextView(this)
        logTextView.text = message

        // Set text color based on the type of message
        val textColor = when {
            message.startsWith("Permission granted") -> Color.GREEN
            message.startsWith("Permission denied") -> Color.RED
            else -> Color.BLACK
        }
        logTextView.setTextColor(textColor)

        // Set text size
        logTextView.textSize = 16f

        // Set layout parameters
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        layoutParams.setMargins(0, 0, 0, 16)

        // Check if the message is related to accessing files, media, location, or contacts
        if (message.contains("READ_EXTERNAL_STORAGE") || message.contains("WRITE_EXTERNAL_STORAGE") ||
            message.contains("READ_MEDIA_IMAGES") || message.contains("READ_MEDIA_AUDIO") ||
            message.contains("READ_MEDIA_VIDEO") || message.contains("ACCESS_FINE_LOCATION") ||
            message.contains("READ_CONTACTS")
        ) {
            // If it is, set the background color to yellow
            logTextView.setBackgroundColor(Color.YELLOW)

            // Check if the message contains a folder path
            val folderPath = getFolderPathFromMessage(message)
            if (folderPath != null) {
                // If it does, add the folder path to the accessed folders list
                accessedFolders.add(folderPath)

                // Update the folder list adapter
                val folderListView = findViewById<ListView>(R.id.folders_accessed_list)
                val folderListAdapter = folderListView.adapter as? ArrayAdapter<String>
                if (folderListAdapter == null) {
                    val newAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf(folderPath))
                    folderListView.adapter = newAdapter
                } else {
                    folderListAdapter.clear()
                    folderListAdapter.addAll(accessedFolders)
                    folderListAdapter.notifyDataSetChanged()
                }
            }
        }

        // Add the log message to the log container
        logContainer.addView(logTextView, layoutParams)
    }

    private fun getFolderPathFromMessage(message: String): String? {
        return when {
            message.contains("READ_EXTERNAL_STORAGE") || message.contains("WRITE_EXTERNAL_STORAGE") -> {
                // Get the folder path from the message by splitting the message and taking the last part
                val messageParts = message.split(":")
                if (messageParts.size > 1) {
                    val folderPath = messageParts[1].trim()
                    if (folderPath.isNotBlank()) {
                        folderPath
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            message.contains("READ_MEDIA_IMAGES") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path
            }
            message.contains("READ_MEDIA_AUDIO") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).path
            }
            message.contains("READ_MEDIA_VIDEO") -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path
            }
            message.contains("ACCESS_FINE_LOCATION") -> {
                "Location"
            }
            message.contains("READ_CONTACTS") -> {
                "Contacts"
            }
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop monitoring the app's file access when the activity is destroyed
        fileObserver?.stopWatching()
    }
}
