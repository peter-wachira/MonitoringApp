package com.example.monitoringapp

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.FileObserver
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.* // ktlint-disable no-wildcard-imports
import androidx.appcompat.app.AppCompatActivity
import com.example.monitoringapp.service.FileMonitorService
import java.io.File
import java.io.FileWriter
import java.io.IOException

class LogActivity : AppCompatActivity() {
    private val TAG = LogActivity::class.java.simpleName

    private lateinit var packageInfo: PackageInfo
    private lateinit var permissions: Array<String>
    private var fileObserver: FileObserver? = null
    private var accessedFolders = mutableSetOf<String>()
    private val logMessages = mutableListOf<String>()
    private lateinit var logListAdapter: ArrayAdapter<String>
    private lateinit var fileMonitorService: FileMonitorService

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FileMonitorService.LocalBinder
            fileMonitorService = binder.getService()
            accessedFolders.addAll(fileMonitorService.getAccessedFolders())
            logListAdapter = object : ArrayAdapter<String>(
                this@LogActivity,
                android.R.layout.simple_list_item_1,
                ArrayList(accessedFolders),
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)
                    val folderPath = getItem(position)
                    (view as TextView).text = folderPath
                    return view
                }

                override fun getCount(): Int {
                    return accessedFolders.size
                }

                override fun getItem(position: Int): String? {
                    return accessedFolders.elementAt(position)
                }
            }
            val logList = findViewById<ListView>(R.id.folders_accessed_list)
            logList.adapter = logListAdapter
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Do nothing
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val serviceIntent = Intent(this, FileMonitorService::class.java)
        val bound = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            Log.e(TAG, "Failed to bind to FileMonitorService")
            Toast.makeText(this, "Failed to bind to FileMonitorService", Toast.LENGTH_SHORT).show()
        }

        // Set up back button
        val backButton = findViewById<ImageButton>(R.id.back_button)
        val title = findViewById<TextView>(R.id.app_name)
        backButton.setOnClickListener {
            title.text = ""
            onBackPressed()
        }

        // Get the package name from the intent extra
        val packageName = intent.getStringExtra("packageName")
        title.text = packageName?.let { getAppName(it, applicationContext) }

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
                logMessage("$permission: \n ${getPermissionStatus(permissionStatus)}", logContainer)
            }
        }

        // Monitor the app's file access in the background
        fileObserver = packageInfo.applicationInfo.dataDir?.let {
            object : FileObserver(it, ALL_EVENTS) {
                override fun onEvent(event: Int, path: String?) {
                    if (event and (CREATE or DELETE or MODIFY) != 0) {
                        Log.e(TAG, "File access: $path")
                        logMessage("File access: $path", logContainer)
                        val folderPath = getFolderPathFromMessage("File access: $path")
                        if (folderPath != null) {
                            accessedFolders.add(folderPath)
                            runOnUiThread {
                                logListAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()

        // Set up the log button to save log messages
        val logButton = findViewById<Button>(R.id.save_app_logs)
        logButton.setOnClickListener {
            saveLog()
        }
    }

    private fun getPermissionStatus(permissionStatus: Int): String {
        return when (permissionStatus) {
            PackageManager.PERMISSION_GRANTED -> "Status: Granted"
            PackageManager.PERMISSION_DENIED -> "Status: Denied"
            else -> "Unknown"
        }
    }

    /* In this implementation, the saveLog() method writes each log message to a file named "log.txt" in the app's external files directory.
    It also displays a toast message indicating whether the log was successfully saved or if there was an error.
    Note that this implementation uses a FileWriter to write the log messages to the file,
    and catches any IOException that might occur while writing.
    */

    private fun saveLog() {
        val file = File(getExternalFilesDir(null), "log.txt")
        try {
            val writer = FileWriter(file)
            for (message in logMessages) {
                writer.append(message).append("\n")
            }
            for (folderPath in accessedFolders) {
                writer.append("Folder accessed: $folderPath").append("\n")
            }
            writer.flush()
            writer.close()
            Toast.makeText(this, "Log saved to ${file.absolutePath}", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving log", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logMessage(message: String, logContainer: LinearLayout) {
        logMessages.add(message)

        // Set text color based on the type of message
        val textColor = when {
            message.endsWith("Status: Granted") -> Color.BLACK
            message.endsWith("Status: Denied") -> Color.RED
            else -> Color.BLACK
        }

        // Check if the message is related to accessing files
        if (message.startsWith("File access: ")) {
            // If it is, set the background color to yellow
            val logTextView = TextView(this)
            logTextView.setTextColor(textColor)
            logTextView.text = message.substring(14) // 14 is the length of "File access: "

            // Check if the message contains a folder path
            val folderPath = getFolderPathFromMessage(message)
            if (folderPath != null) {
                // If it does, set the text as a clickable link
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        // Handle click event
                        // Open folder path in file explorer app
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = true
                        ds.color = textColor
                    }
                }
                val clickableText = SpannableString(folderPath)
                clickableText.setSpan(
                    clickableSpan,
                    0,
                    folderPath.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                logTextView.text = TextUtils.concat("File access: ", clickableText)
                logTextView.movementMethod = LinkMovementMethod.getInstance()
            }

            logContainer.addView(logTextView)
        } else {
            // If it is not related to accessing files, set the background color to white
            val logTextView = TextView(this)
            logTextView.setTextColor(textColor)
            logTextView.text = message
            logContainer.addView(logTextView)
        }

        // Scroll to the bottom of the log
        val logScrollView = findViewById<ScrollView>(R.id.logScrollView)
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun getFolderPathFromMessage(message: String): String? {
        val regex = Regex("at ([^()]+)[(][^()]+[)]")
        val matchResult = regex.find(message)
        return matchResult?.groups?.get(1)?.value
    }

    private fun getAppName(packageName: String, context: Context): String {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        return packageManager.getApplicationLabel(appInfo).toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }
}
